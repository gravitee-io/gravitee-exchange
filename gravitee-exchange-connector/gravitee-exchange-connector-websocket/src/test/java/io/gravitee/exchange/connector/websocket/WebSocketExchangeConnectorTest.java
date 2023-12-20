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
package io.gravitee.exchange.connector.websocket;

import static org.assertj.core.api.Assertions.assertThat;

import io.gravitee.exchange.api.configuration.PrefixConfiguration;
import io.gravitee.exchange.api.websocket.protocol.ProtocolVersion;
import io.gravitee.exchange.connector.websocket.client.WebSocketClientConfiguration;
import io.gravitee.exchange.connector.websocket.client.WebSocketConnectorClientFactory;
import io.gravitee.exchange.connector.websocket.exception.WebSocketConnectorException;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxTestContext;
import io.vertx.rxjava3.core.http.ServerWebSocket;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/**
 * @author Guillaume LAMIRAND (guillaume.lamirand at graviteesource.com)
 * @author GraviteeSource Team
 */
class WebSocketExchangeConnectorTest extends AbstractWebSocketConnectorTest {

    private MockEnvironment environment;
    private WebSocketExchangeConnector websocketExchangeConnector;

    @BeforeEach
    public void beforeEach() {
        environment = new MockEnvironment();
        environment.setProperty("exchange.connector.ws.endpoints[0]", "http://localhost:%s".formatted(serverPort));
        WebSocketConnectorClientFactory webSocketConnectorClientFactory = new WebSocketConnectorClientFactory(
            vertx,
            new WebSocketClientConfiguration(new PrefixConfiguration(environment))
        );
        this.websocketExchangeConnector =
            new WebSocketExchangeConnector(ProtocolVersion.V1, List.of(), List.of(), vertx, webSocketConnectorClientFactory, exchangeSerDe);
    }

    @Test
    void should_initialize_connector_including_hello_handshake() {
        websocketServerHandler = serverWebSocket -> this.replyHello(serverWebSocket, protocolAdapter(ProtocolVersion.V1));
        websocketExchangeConnector.initialize().test().awaitDone(30, TimeUnit.SECONDS).assertComplete();
    }

    @Test
    void should_not_fail_with_timeout_when_initializing_connector_without_hello_reply() {
        websocketExchangeConnector.initialize().test().assertNotComplete();
    }

    @Test
    void should_not_fail_with_timeout_when_initializing_connector_without_endpoint() {
        environment.setProperty("exchange.connector.ws.endpoints[0]", "");
        websocketExchangeConnector.initialize().test().assertError(WebSocketConnectorException.class);
    }

    @Test
    void should_reconnect_after_unexpected_close(VertxTestContext testContext) throws InterruptedException {
        AtomicReference<ServerWebSocket> ws = new AtomicReference<>();
        Checkpoint checkpoint = testContext.checkpoint(2);
        websocketServerHandler =
            serverWebSocket -> {
                replyHello(serverWebSocket, protocolAdapter(ProtocolVersion.V1));
                ws.set(serverWebSocket);
                checkpoint.flag();
            };
        // Initialize first
        websocketExchangeConnector.initialize().test().awaitDone(10, TimeUnit.SECONDS).assertComplete();

        // Close websocket to execute reconnect
        ws.get().close((short) 1001);
        assertThat(testContext.awaitCompletion(10, TimeUnit.SECONDS)).isTrue();
    }
}
