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

import io.gravitee.exchange.api.command.Command;
import io.gravitee.exchange.api.command.hello.HelloCommand;
import io.gravitee.exchange.api.command.hello.HelloReply;
import io.gravitee.exchange.api.command.hello.HelloReplyPayload;
import io.gravitee.exchange.api.websocket.channel.test.AbstractWebSocketTest;
import io.gravitee.exchange.api.websocket.protocol.ProtocolAdapter;
import io.gravitee.exchange.api.websocket.protocol.ProtocolExchange;
import io.vertx.junit5.VertxExtension;
import io.vertx.rxjava3.core.http.ServerWebSocket;
import java.util.function.Consumer;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * @author Guillaume LAMIRAND (guillaume.lamirand at graviteesource.com)
 * @author GraviteeSource Team
 */
@ExtendWith(VertxExtension.class)
public abstract class AbstractWebSocketConnectorTest extends AbstractWebSocketTest {

    protected void replyHello(final ServerWebSocket serverWebSocket, final ProtocolAdapter protocolAdapter) {
        this.replyHello(serverWebSocket, protocolAdapter, cmd -> {});
    }

    protected void replyHello(
        final ServerWebSocket serverWebSocket,
        final ProtocolAdapter protocolAdapter,
        Consumer<Command<?>> commandHandler
    ) {
        serverWebSocket.binaryMessageHandler(buffer -> {
            ProtocolExchange websocketExchange = protocolAdapter.read(buffer);
            if (websocketExchange.type() == ProtocolExchange.Type.COMMAND) {
                Command<?> command = websocketExchange.asCommand();
                if (command.getType().equals(HelloCommand.COMMAND_TYPE)) {
                    HelloReply helloReply = new HelloReply(command.getId(), new HelloReplyPayload("targetId"));
                    serverWebSocket
                        .writeBinaryMessage(
                            protocolAdapter.write(
                                ProtocolExchange
                                    .builder()
                                    .type(ProtocolExchange.Type.REPLY)
                                    .exchangeType(helloReply.getType())
                                    .exchange(helloReply)
                                    .build()
                            )
                        )
                        .subscribe();
                } else if (command.getType().equals(io.gravitee.exchange.api.websocket.protocol.legacy.hello.HelloCommand.COMMAND_TYPE)) {
                    io.gravitee.exchange.api.websocket.protocol.legacy.hello.HelloReply helloReply =
                        new io.gravitee.exchange.api.websocket.protocol.legacy.hello.HelloReply(
                            command.getId(),
                            new io.gravitee.exchange.api.websocket.protocol.legacy.hello.HelloReplyPayload("targetId")
                        );
                    serverWebSocket
                        .writeBinaryMessage(
                            protocolAdapter.write(
                                ProtocolExchange
                                    .builder()
                                    .type(ProtocolExchange.Type.REPLY)
                                    .exchangeType(helloReply.getType())
                                    .exchange(helloReply)
                                    .build()
                            )
                        )
                        .subscribe();
                } else {
                    commandHandler.accept(command);
                }
            }
        });
    }
}
