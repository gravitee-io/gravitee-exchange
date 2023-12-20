/*
 * Copyright © 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.exchange.connector.websocket.channel;

import static org.assertj.core.api.Assertions.assertThat;

import io.gravitee.exchange.api.channel.exception.ChannelTimeoutException;
import io.gravitee.exchange.api.controller.ws.WebsocketControllerConstants;
import io.gravitee.exchange.api.websocket.channel.test.AbstractWebSocketTest;
import io.gravitee.exchange.api.websocket.channel.test.DummyCommand;
import io.gravitee.exchange.api.websocket.channel.test.DummyCommandHandler;
import io.gravitee.exchange.api.websocket.channel.test.DummyPayload;
import io.gravitee.exchange.api.websocket.channel.test.DummyReply;
import io.gravitee.exchange.api.websocket.protocol.ProtocolAdapter;
import io.gravitee.exchange.api.websocket.protocol.ProtocolExchange;
import io.gravitee.exchange.api.websocket.protocol.ProtocolVersion;
import io.gravitee.exchange.connector.websocket.AbstractWebSocketConnectorTest;
import io.gravitee.exchange.connector.websocket.exception.WebSocketConnectorException;
import io.reactivex.rxjava3.observers.TestObserver;
import io.reactivex.rxjava3.plugins.RxJavaPlugins;
import io.reactivex.rxjava3.schedulers.TestScheduler;
import io.vertx.core.http.WebSocketConnectOptions;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxTestContext;
import io.vertx.rxjava3.core.buffer.Buffer;
import io.vertx.rxjava3.core.http.HttpClient;
import io.vertx.rxjava3.core.http.ServerWebSocket;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * @author Guillaume LAMIRAND (guillaume.lamirand at graviteesource.com)
 * @author GraviteeSource Team
 */
class WebSocketConnectorChannelTest extends AbstractWebSocketConnectorTest {

    @AfterEach
    public void afterEach() {
        RxJavaPlugins.reset();
    }

    @ParameterizedTest
    @EnumSource(ProtocolVersion.class)
    void should_initialize_with_hello_reply(ProtocolVersion protocolVersion) {
        ProtocolAdapter protocolAdapter = protocolAdapter(protocolVersion);
        HttpClient httpClient = AbstractWebSocketTest.vertx.createHttpClient();
        WebSocketConnectOptions webSocketConnectOptions = new WebSocketConnectOptions()
            .setHost("localhost")
            .setPort(AbstractWebSocketTest.serverPort)
            .setURI(WebsocketControllerConstants.EXCHANGE_CONTROLLER_PATH);
        AbstractWebSocketTest.websocketServerHandler = ws -> this.replyHello(ws, protocolAdapter);
        httpClient
            .rxWebSocket(webSocketConnectOptions)
            .flatMapCompletable(webSocket -> {
                WebSocketConnectorChannel webSocketConnectorChannel = new WebSocketConnectorChannel(
                    List.of(),
                    List.of(),
                    AbstractWebSocketTest.vertx,
                    webSocket,
                    protocolAdapter
                );
                return webSocketConnectorChannel.initialize().doFinally(webSocket::close);
            })
            .test()
            .awaitDone(10, TimeUnit.SECONDS)
            .assertComplete();
    }

    @ParameterizedTest
    @EnumSource(ProtocolVersion.class)
    void should_failed_to_initialize_after_time_out_without_hello_reply(ProtocolVersion protocolVersion) {
        ProtocolAdapter protocolAdapter = protocolAdapter(protocolVersion);
        HttpClient httpClient = AbstractWebSocketTest.vertx.createHttpClient();
        WebSocketConnectOptions webSocketConnectOptions = new WebSocketConnectOptions()
            .setHost("localhost")
            .setPort(AbstractWebSocketTest.serverPort)
            .setURI(WebsocketControllerConstants.EXCHANGE_CONTROLLER_PATH);
        TestScheduler testScheduler = new TestScheduler();
        // set calls to Schedulers.computation() to use our test scheduler
        RxJavaPlugins.setComputationSchedulerHandler(ignore -> testScheduler);
        // Advance in time when hello command is received so reply will timeout
        AbstractWebSocketTest.websocketServerHandler =
            ws -> ws.binaryMessageHandler(event -> testScheduler.advanceTimeBy(60, TimeUnit.SECONDS));

        TestObserver<Void> testObserver = httpClient
            .rxWebSocket(webSocketConnectOptions)
            .flatMapCompletable(webSocket -> {
                WebSocketConnectorChannel webSocketConnectorChannel = new WebSocketConnectorChannel(
                    List.of(),
                    List.of(),
                    AbstractWebSocketTest.vertx,
                    webSocket,
                    protocolAdapter
                );
                return webSocketConnectorChannel.initialize().doFinally(webSocket::close);
            })
            .test();
        testObserver.awaitDone(10, TimeUnit.SECONDS).assertError(WebSocketConnectorException.class);
    }

    @ParameterizedTest
    @EnumSource(ProtocolVersion.class)
    void should_send_command_and_receive_reply(ProtocolVersion protocolVersion) {
        ProtocolAdapter protocolAdapter = protocolAdapter(protocolVersion);
        AbstractWebSocketTest.websocketServerHandler =
            serverWebSocket ->
                this.replyHello(
                        serverWebSocket,
                        protocolAdapter,
                        command -> {
                            DummyReply dummyReply = new DummyReply(command.getId(), new DummyPayload());
                            serverWebSocket
                                .writeBinaryMessage(
                                    protocolAdapter.write(
                                        ProtocolExchange
                                            .builder()
                                            .type(ProtocolExchange.Type.REPLY)
                                            .exchangeType(dummyReply.getType())
                                            .exchange(dummyReply)
                                            .build()
                                    )
                                )
                                .subscribe();
                        }
                    );
        DummyCommand command = new DummyCommand(new DummyPayload());
        AbstractWebSocketTest
            .rxWebSocket()
            .flatMap(webSocket -> {
                WebSocketConnectorChannel webSocketConnectorChannel = new WebSocketConnectorChannel(
                    List.of(),
                    List.of(),
                    AbstractWebSocketTest.vertx,
                    webSocket,
                    protocolAdapter
                );
                return webSocketConnectorChannel.initialize().andThen(webSocketConnectorChannel.send(command)).doFinally(webSocket::close);
            })
            .test()
            .awaitDone(10, TimeUnit.SECONDS)
            .assertValue(reply -> {
                assertThat(reply).isInstanceOf(DummyReply.class);
                assertThat(reply.getCommandId()).isEqualTo(command.getId());
                return true;
            });
    }

    @ParameterizedTest
    @EnumSource(ProtocolVersion.class)
    void should_send_command_and_receive_empty_reply_after_timeout(ProtocolVersion protocolVersion) {
        ProtocolAdapter protocolAdapter = protocolAdapter(protocolVersion);
        TestScheduler testScheduler = new TestScheduler();
        // set calls to Schedulers.computation() to use our test scheduler
        RxJavaPlugins.setComputationSchedulerHandler(ignore -> testScheduler);
        // Advance in time when primary command is received so reply will timeout
        AbstractWebSocketTest.websocketServerHandler =
            serverWebSocket ->
                this.replyHello(serverWebSocket, protocolAdapter, command -> testScheduler.advanceTimeBy(60, TimeUnit.SECONDS));
        DummyCommand command = new DummyCommand(new DummyPayload());

        AbstractWebSocketTest
            .rxWebSocket()
            .flatMap(webSocket -> {
                WebSocketConnectorChannel webSocketConnectorChannel = new WebSocketConnectorChannel(
                    List.of(),
                    List.of(),
                    AbstractWebSocketTest.vertx,
                    webSocket,
                    protocolAdapter
                );
                return webSocketConnectorChannel.initialize().andThen(webSocketConnectorChannel.send(command)).doFinally(webSocket::close);
            })
            .test()
            .awaitDone(10, TimeUnit.SECONDS)
            .assertError(ChannelTimeoutException.class);
    }

    @ParameterizedTest
    @EnumSource(ProtocolVersion.class)
    void should_receive_pong(ProtocolVersion protocolVersion, VertxTestContext vertxTestContext) throws InterruptedException {
        ProtocolAdapter protocolAdapter = protocolAdapter(protocolVersion);
        Checkpoint pongCheckpoint = vertxTestContext.checkpoint();
        AbstractWebSocketTest.websocketServerHandler =
            serverWebSocket -> {
                this.replyHello(serverWebSocket, protocolAdapter);
                serverWebSocket.pongHandler(event -> pongCheckpoint.flag());
                serverWebSocket.writePing(Buffer.buffer("ping"));
            };

        AbstractWebSocketTest
            .rxWebSocket()
            .flatMapCompletable(webSocket -> {
                WebSocketConnectorChannel webSocketConnectorChannel = new WebSocketConnectorChannel(
                    List.of(),
                    List.of(),
                    AbstractWebSocketTest.vertx,
                    webSocket,
                    protocolAdapter
                );
                return webSocketConnectorChannel.initialize().doFinally(webSocket::close);
            })
            .test()
            .awaitDone(10, TimeUnit.SECONDS)
            .assertComplete();
        assertThat(vertxTestContext.awaitCompletion(10, TimeUnit.SECONDS)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(ProtocolVersion.class)
    void should_handle_command(ProtocolVersion protocolVersion, VertxTestContext vertxTestContext) throws InterruptedException {
        ProtocolAdapter protocolAdapter = protocolAdapter(protocolVersion);
        Checkpoint handlerCheckpoint = vertxTestContext.checkpoint();

        AtomicReference<ServerWebSocket> webSocketAtomicReference = new AtomicReference<>();
        AbstractWebSocketTest.websocketServerHandler =
            ws -> {
                webSocketAtomicReference.set(ws);
                this.replyHello(ws, protocolAdapter);
            };
        AbstractWebSocketTest
            .rxWebSocket()
            .flatMapCompletable(webSocket -> {
                WebSocketConnectorChannel webSocketConnectorChannel = new WebSocketConnectorChannel(
                    List.of(new DummyCommandHandler(handlerCheckpoint)),
                    List.of(),
                    AbstractWebSocketTest.vertx,
                    webSocket,
                    protocolAdapter
                );
                return webSocketConnectorChannel.initialize();
            })
            .test()
            .awaitDone(10, TimeUnit.SECONDS)
            .assertComplete();

        webSocketAtomicReference
            .get()
            .writeBinaryMessage(
                protocolAdapter.write(
                    ProtocolExchange
                        .builder()
                        .type(ProtocolExchange.Type.COMMAND)
                        .exchangeType(DummyCommand.COMMAND_TYPE)
                        .exchange(new DummyCommand(new DummyPayload()))
                        .build()
                )
            );

        assertThat(vertxTestContext.awaitCompletion(10, TimeUnit.SECONDS)).isTrue();
    }
}
