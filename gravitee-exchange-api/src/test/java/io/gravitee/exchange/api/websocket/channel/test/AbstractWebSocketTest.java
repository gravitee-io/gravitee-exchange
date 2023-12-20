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
package io.gravitee.exchange.api.websocket.channel.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.exchange.api.controller.ws.WebsocketControllerConstants;
import io.gravitee.exchange.api.websocket.command.DefaultExchangeSerDe;
import io.gravitee.exchange.api.websocket.protocol.ProtocolAdapter;
import io.gravitee.exchange.api.websocket.protocol.ProtocolAdapterFactory;
import io.gravitee.exchange.api.websocket.protocol.ProtocolVersion;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.plugins.RxJavaPlugins;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.WebSocketConnectOptions;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.rxjava3.core.http.HttpClient;
import io.vertx.rxjava3.core.http.HttpServer;
import io.vertx.rxjava3.core.http.ServerWebSocket;
import io.vertx.rxjava3.core.http.WebSocket;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.util.TestSocketUtils;

/**
 * @author Guillaume LAMIRAND (guillaume.lamirand at graviteesource.com)
 * @author GraviteeSource Team
 */
@ExtendWith(VertxExtension.class)
public abstract class AbstractWebSocketTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    protected static HttpServer httpServer;
    protected static int serverPort;
    protected static Handler<ServerWebSocket> websocketServerHandler;
    protected static io.vertx.rxjava3.core.Vertx vertx;
    private static Disposable serverDispose;
    protected DefaultExchangeSerDe exchangeSerDe;

    @BeforeAll
    public static void startWebSocketServer(Vertx vertx, VertxTestContext context) {
        AbstractWebSocketTest.vertx = io.vertx.rxjava3.core.Vertx.newInstance(vertx);
        final HttpServerOptions httpServerOptions = new HttpServerOptions();
        serverPort = TestSocketUtils.findAvailableTcpPort();
        httpServerOptions.setPort(serverPort);
        serverDispose =
            AbstractWebSocketTest.vertx
                .createHttpServer(httpServerOptions)
                .webSocketHandler(serverWebSocket -> {
                    if (null != websocketServerHandler) {
                        websocketServerHandler.handle(serverWebSocket);
                    }
                })
                .listen(serverPort)
                .subscribe(
                    server -> {
                        httpServer = server;
                        context.completeNow();
                    },
                    context::failNow
                );
    }

    @BeforeEach
    public void initSerDer() {
        this.exchangeSerDe = new DummyCommandSerDe(OBJECT_MAPPER);
    }

    @AfterAll
    public static void stopWebSocketServer() {
        if (null != serverDispose) {
            serverDispose.dispose();
        }
        if (null != httpServer) {
            httpServer.close().subscribe();
        }
    }

    @AfterEach
    public void cleanHandler() {
        websocketServerHandler = null;
        RxJavaPlugins.reset();
    }

    public ProtocolAdapter protocolAdapter(final ProtocolVersion protocolVersion) {
        return ProtocolAdapterFactory.create(protocolVersion, this.exchangeSerDe);
    }

    protected static Single<WebSocket> rxWebSocket() {
        HttpClient httpClient = vertx.createHttpClient();
        WebSocketConnectOptions webSocketConnectOptions = new WebSocketConnectOptions()
            .setHost("localhost")
            .setPort(serverPort)
            .setURI(WebsocketControllerConstants.EXCHANGE_CONTROLLER_PATH);
        return httpClient.rxWebSocket(webSocketConnectOptions);
    }
}
