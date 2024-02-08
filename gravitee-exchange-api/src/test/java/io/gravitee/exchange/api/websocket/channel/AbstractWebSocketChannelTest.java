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
package io.gravitee.exchange.api.websocket.channel;

import static org.assertj.core.api.Assertions.assertThat;

import io.gravitee.exchange.api.channel.Channel;
import io.gravitee.exchange.api.channel.exception.ChannelNoReplyException;
import io.gravitee.exchange.api.channel.exception.ChannelTimeoutException;
import io.gravitee.exchange.api.command.Command;
import io.gravitee.exchange.api.command.CommandAdapter;
import io.gravitee.exchange.api.command.CommandHandler;
import io.gravitee.exchange.api.command.Reply;
import io.gravitee.exchange.api.command.ReplyAdapter;
import io.gravitee.exchange.api.command.noreply.NoReply;
import io.gravitee.exchange.api.command.primary.PrimaryCommand;
import io.gravitee.exchange.api.command.primary.PrimaryCommandPayload;
import io.gravitee.exchange.api.command.primary.PrimaryReply;
import io.gravitee.exchange.api.command.primary.PrimaryReplyPayload;
import io.gravitee.exchange.api.command.unknown.UnknownReply;
import io.gravitee.exchange.api.websocket.channel.test.AbstractWebSocketTest;
import io.gravitee.exchange.api.websocket.channel.test.AdaptedDummyReply;
import io.gravitee.exchange.api.websocket.channel.test.DummyCommand;
import io.gravitee.exchange.api.websocket.channel.test.DummyCommandAdapter;
import io.gravitee.exchange.api.websocket.channel.test.DummyCommandHandler;
import io.gravitee.exchange.api.websocket.channel.test.DummyPayload;
import io.gravitee.exchange.api.websocket.channel.test.DummyReply;
import io.gravitee.exchange.api.websocket.channel.test.DummyReplyAdapter;
import io.gravitee.exchange.api.websocket.protocol.ProtocolAdapter;
import io.gravitee.exchange.api.websocket.protocol.ProtocolExchange;
import io.gravitee.exchange.api.websocket.protocol.ProtocolVersion;
import io.gravitee.exchange.api.websocket.protocol.legacy.ignored.IgnoredReply;
import io.reactivex.rxjava3.plugins.RxJavaPlugins;
import io.reactivex.rxjava3.schedulers.TestScheduler;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.rxjava3.core.buffer.Buffer;
import io.vertx.rxjava3.core.http.ServerWebSocket;
import io.vertx.rxjava3.core.http.WebSocketBase;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * @author Guillaume LAMIRAND (guillaume.lamirand at graviteesource.com)
 * @author GraviteeSource Team
 */
@ExtendWith(VertxExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class AbstractWebSocketChannelTest extends AbstractWebSocketTest {

    @ParameterizedTest
    @EnumSource(ProtocolVersion.class)
    void should_send_command_and_receive_reply(ProtocolVersion protocolVersion) {
        ProtocolAdapter protocolAdapter = protocolAdapter(protocolVersion);
        websocketServerHandler =
            serverWebSocket ->
                serverWebSocket.binaryMessageHandler(buffer -> {
                    ProtocolExchange websocketExchange = protocolAdapter.read(buffer);
                    Command<?> command = websocketExchange.asCommand();
                    DummyReply primaryReply = new DummyReply(command.getId(), new DummyPayload());
                    serverWebSocket
                        .writeBinaryMessage(
                            protocolAdapter.write(
                                ProtocolExchange
                                    .builder()
                                    .type(ProtocolExchange.Type.REPLY)
                                    .exchangeType(primaryReply.getType())
                                    .exchange(primaryReply)
                                    .build()
                            )
                        )
                        .subscribe();
                });
        DummyCommand command = new DummyCommand(new DummyPayload());
        rxWebSocket()
            .<Reply<?>>flatMap(webSocket -> {
                Channel webSocketChannel = new SimpleWebSocketChannel(List.of(), List.of(), List.of(), vertx, webSocket, protocolAdapter);
                return webSocketChannel.initialize().andThen(webSocketChannel.send(command));
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
    void should_send_command_and_throw_exception_after_timeout(ProtocolVersion protocolVersion) {
        ProtocolAdapter protocolAdapter = protocolAdapter(protocolVersion);
        TestScheduler testScheduler = new TestScheduler();
        // set calls to Schedulers.computation() to use our test scheduler
        RxJavaPlugins.setComputationSchedulerHandler(ignore -> testScheduler);
        // Advance in time when primary command is received so reply will timeout
        websocketServerHandler =
            serverWebSocket -> serverWebSocket.binaryMessageHandler(buffer -> testScheduler.advanceTimeBy(60, TimeUnit.SECONDS));
        DummyCommand command = new DummyCommand(new DummyPayload());
        rxWebSocket()
            .flatMap(webSocket -> {
                Channel webSocketChannel = new SimpleWebSocketChannel(List.of(), List.of(), List.of(), vertx, webSocket, protocolAdapter);
                return webSocketChannel.initialize().andThen(webSocketChannel.send(command));
            })
            .test()
            .awaitDone(10, TimeUnit.SECONDS)
            .assertError(ChannelTimeoutException.class);
    }

    @ParameterizedTest
    @EnumSource(ProtocolVersion.class)
    void should_send_command_and_throw_no_reply_exception_when_receiving_no_reply(ProtocolVersion protocolVersion) {
        ProtocolAdapter protocolAdapter = protocolAdapter(protocolVersion);
        websocketServerHandler =
            serverWebSocket ->
                serverWebSocket.binaryMessageHandler(buffer -> {
                    ProtocolExchange websocketExchange = protocolAdapter.read(buffer);
                    Command<?> command = websocketExchange.asCommand();
                    NoReply reply = new NoReply(command.getId(), "no reply");
                    serverWebSocket
                        .writeBinaryMessage(
                            protocolAdapter.write(
                                ProtocolExchange
                                    .builder()
                                    .type(ProtocolExchange.Type.REPLY)
                                    .exchangeType(reply.getType())
                                    .exchange(reply)
                                    .build()
                            )
                        )
                        .subscribe();
                });
        DummyCommand command = new DummyCommand(new DummyPayload());
        rxWebSocket()
            .<Reply<?>>flatMap(webSocket -> {
                Channel webSocketChannel = new SimpleWebSocketChannel(List.of(), List.of(), List.of(), vertx, webSocket, protocolAdapter);
                return webSocketChannel.initialize().andThen(webSocketChannel.send(command));
            })
            .test()
            .awaitDone(10, TimeUnit.SECONDS)
            .assertError(ChannelNoReplyException.class);
    }

    @ParameterizedTest
    @EnumSource(ProtocolVersion.class)
    void should_send_decorated_command_and_reply(ProtocolVersion protocolVersion, VertxTestContext vertxTestContext)
        throws InterruptedException {
        ProtocolAdapter protocolAdapter = protocolAdapter(protocolVersion);
        Checkpoint handlerCheckpoint = vertxTestContext.checkpoint(3);
        websocketServerHandler =
            serverWebSocket ->
                serverWebSocket.binaryMessageHandler(buffer -> {
                    ProtocolExchange websocketExchange = protocolAdapter.read(buffer);
                    Command<?> command = websocketExchange.asCommand();
                    AdaptedDummyReply adaptedDummyReply = new AdaptedDummyReply(command.getId(), new DummyPayload());
                    serverWebSocket
                        .writeBinaryMessage(
                            protocolAdapter.write(
                                ProtocolExchange
                                    .builder()
                                    .type(ProtocolExchange.Type.REPLY)
                                    .exchangeType(adaptedDummyReply.getType())
                                    .exchange(adaptedDummyReply)
                                    .build()
                            )
                        )
                        .subscribe();
                    handlerCheckpoint.flag();
                });
        DummyCommand command = new DummyCommand(new DummyPayload());
        rxWebSocket()
            .<Reply<?>>flatMap(webSocket -> {
                Channel webSocketChannel = new SimpleWebSocketChannel(
                    List.of(),
                    List.of(new DummyCommandAdapter(handlerCheckpoint)),
                    List.of(new DummyReplyAdapter(handlerCheckpoint)),
                    vertx,
                    webSocket,
                    protocolAdapter
                );
                return webSocketChannel.initialize().andThen(webSocketChannel.send(command));
            })
            .test()
            .awaitDone(10, TimeUnit.SECONDS)
            .assertValue(reply -> {
                assertThat(reply.getCommandId()).isEqualTo(command.getId());
                return true;
            });
        assertThat(vertxTestContext.awaitCompletion(10, TimeUnit.SECONDS)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(ProtocolVersion.class)
    void should_receive_pong(ProtocolVersion protocolVersion, VertxTestContext vertxTestContext) throws InterruptedException {
        ProtocolAdapter protocolAdapter = protocolAdapter(protocolVersion);
        Checkpoint pongCheckpoint = vertxTestContext.checkpoint();

        AtomicReference<ServerWebSocket> webSocketAtomicReference = new AtomicReference<>();
        websocketServerHandler =
            serverWebSocket -> {
                serverWebSocket.pongHandler(event -> pongCheckpoint.flag());
                webSocketAtomicReference.set(serverWebSocket);
            };
        rxWebSocket()
            .flatMapCompletable(webSocket -> {
                Channel webSocketChannel = new SimpleWebSocketChannel(List.of(), List.of(), List.of(), vertx, webSocket, protocolAdapter);
                return webSocketChannel.initialize();
            })
            .test()
            .awaitDone(10, TimeUnit.SECONDS)
            .assertComplete();
        webSocketAtomicReference.get().writePing(Buffer.buffer());
        assertThat(vertxTestContext.awaitCompletion(10, TimeUnit.SECONDS)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(ProtocolVersion.class)
    void should_handle_command(ProtocolVersion protocolVersion, VertxTestContext vertxTestContext) throws InterruptedException {
        ProtocolAdapter protocolAdapter = protocolAdapter(protocolVersion);
        Checkpoint handlerCheckpoint = vertxTestContext.checkpoint();

        AtomicReference<ServerWebSocket> webSocketAtomicReference = new AtomicReference<>();
        websocketServerHandler = webSocketAtomicReference::set;
        rxWebSocket()
            .flatMapCompletable(webSocket -> {
                Channel webSocketChannel = new SimpleWebSocketChannel(
                    List.of(new DummyCommandHandler(handlerCheckpoint)),
                    List.of(),
                    List.of(),
                    vertx,
                    webSocket,
                    protocolAdapter
                );
                return webSocketChannel.initialize();
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

    @ParameterizedTest
    @EnumSource(ProtocolVersion.class)
    void should_not_handle_command_without_command_handler(ProtocolVersion protocolVersion, VertxTestContext vertxTestContext)
        throws InterruptedException {
        ProtocolAdapter protocolAdapter = protocolAdapter(protocolVersion);
        Checkpoint handlerCheckpoint = vertxTestContext.checkpoint();
        AtomicReference<ServerWebSocket> webSocketAtomicReference = new AtomicReference<>();
        websocketServerHandler =
            ws -> {
                webSocketAtomicReference.set(ws);
                ws.binaryMessageHandler(buffer -> {
                    ProtocolExchange websocketExchange = protocolAdapter.read(buffer);
                    assertThat(websocketExchange.type()).isEqualTo(ProtocolExchange.Type.REPLY);
                    Reply<?> reply = websocketExchange.asReply();
                    if (ProtocolVersion.LEGACY == protocolVersion) {
                        assertThat(reply).isInstanceOf(IgnoredReply.class);
                    } else {
                        assertThat(reply).isInstanceOf(NoReply.class);
                    }
                    handlerCheckpoint.flag();
                });
            };
        rxWebSocket()
            .flatMapCompletable(webSocket -> {
                Channel webSocketChannel = new SimpleWebSocketChannel(List.of(), List.of(), List.of(), vertx, webSocket, protocolAdapter);
                return webSocketChannel.initialize();
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

    @ParameterizedTest
    @EnumSource(ProtocolVersion.class)
    void should_not_handle_command_with_unknown_command(ProtocolVersion protocolVersion, VertxTestContext vertxTestContext)
        throws InterruptedException {
        ProtocolAdapter protocolAdapter = protocolAdapter(protocolVersion);
        Checkpoint handlerCheckpoint = vertxTestContext.checkpoint();
        AtomicReference<ServerWebSocket> webSocketAtomicReference = new AtomicReference<>();
        websocketServerHandler =
            ws -> {
                webSocketAtomicReference.set(ws);
                ws.binaryMessageHandler(buffer -> {
                    ProtocolExchange websocketExchange = protocolAdapter.read(buffer);
                    assertThat(websocketExchange.type()).isEqualTo(ProtocolExchange.Type.REPLY);
                    Reply<?> reply = websocketExchange.asReply();
                    assertThat(reply).isInstanceOf(UnknownReply.class);
                    handlerCheckpoint.flag();
                });
            };
        rxWebSocket()
            .flatMapCompletable(webSocket -> {
                Channel webSocketChannel = new SimpleWebSocketChannel(
                    new ArrayList<>(),
                    new ArrayList<>(),
                    new ArrayList<>(),
                    vertx,
                    webSocket,
                    protocolAdapter
                );
                return webSocketChannel.initialize();
            })
            .test()
            .awaitDone(10, TimeUnit.SECONDS)
            .assertComplete();

        if (ProtocolVersion.LEGACY == protocolVersion) {
            webSocketAtomicReference.get().writeBinaryMessage(Buffer.buffer("command:{\"type\":\"WRONG\",\"payload\":{}}"));
        } else {
            webSocketAtomicReference.get().writeBinaryMessage(Buffer.buffer("t:COMMAND;;et:WRONG;;e:{\"type\":\"WRONG\",\"payload\":{}}"));
        }
        assertThat(vertxTestContext.awaitCompletion(10, TimeUnit.SECONDS)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(ProtocolVersion.class)
    void should_properly_sent_primary_command(ProtocolVersion protocolVersion, VertxTestContext vertxTestContext)
        throws InterruptedException {
        ProtocolAdapter protocolAdapter = protocolAdapter(protocolVersion);
        Checkpoint handlerCheckpoint = vertxTestContext.checkpoint();
        websocketServerHandler =
            ws ->
                ws.binaryMessageHandler(buffer -> {
                    if (ProtocolVersion.LEGACY == protocolVersion) {
                        assertThat(buffer).hasToString("primary: true");
                    } else {
                        ProtocolExchange websocketExchange = protocolAdapter.read(buffer);
                        assertThat(websocketExchange.type()).isEqualTo(ProtocolExchange.Type.COMMAND);
                        Command<?> command = websocketExchange.asCommand();
                        assertThat(command).isInstanceOf(PrimaryCommand.class);
                        ws.writeBinaryMessage(
                            protocolAdapter.write(
                                ProtocolExchange
                                    .builder()
                                    .type(ProtocolExchange.Type.REPLY)
                                    .exchangeType(PrimaryCommand.COMMAND_TYPE)
                                    .exchange(new PrimaryReply(command.getId(), new PrimaryReplyPayload()))
                                    .build()
                            )
                        );
                    }
                    handlerCheckpoint.flag();
                });
        rxWebSocket()
            .flatMap(webSocket -> {
                Channel webSocketChannel = new SimpleWebSocketChannel(
                    new ArrayList<>(),
                    new ArrayList<>(),
                    new ArrayList<>(),
                    vertx,
                    webSocket,
                    protocolAdapter
                );
                return webSocketChannel.initialize().andThen(webSocketChannel.send(new PrimaryCommand(new PrimaryCommandPayload(true))));
            })
            .test()
            .awaitDone(10, TimeUnit.SECONDS)
            .assertComplete();
        assertThat(vertxTestContext.awaitCompletion(10, TimeUnit.SECONDS)).isTrue();
    }

    private static class SimpleWebSocketChannel extends AbstractWebSocketChannel {

        protected SimpleWebSocketChannel(
            final List<CommandHandler<? extends Command<?>, ? extends Reply<?>>> commandHandlers,
            final List<CommandAdapter<? extends Command<?>, ? extends Command<?>, ? extends Reply<?>>> commandAdapters,
            final List<ReplyAdapter<? extends Reply<?>, ? extends Reply<?>>> replyAdapters,
            final io.vertx.rxjava3.core.Vertx vertx,
            final WebSocketBase webSocket,
            final ProtocolAdapter protocolAdapter
        ) {
            super(commandHandlers, commandAdapters, replyAdapters, vertx, webSocket, protocolAdapter);
        }

        @Override
        protected boolean expectHelloCommand() {
            return false;
        }
    }
}
