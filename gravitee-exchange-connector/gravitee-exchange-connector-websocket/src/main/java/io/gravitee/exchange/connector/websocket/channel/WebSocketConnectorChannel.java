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

import io.gravitee.exchange.api.channel.exception.ChannelException;
import io.gravitee.exchange.api.command.Command;
import io.gravitee.exchange.api.command.CommandAdapter;
import io.gravitee.exchange.api.command.CommandHandler;
import io.gravitee.exchange.api.command.CommandStatus;
import io.gravitee.exchange.api.command.Reply;
import io.gravitee.exchange.api.command.ReplyAdapter;
import io.gravitee.exchange.api.command.hello.HelloCommand;
import io.gravitee.exchange.api.command.hello.HelloCommandPayload;
import io.gravitee.exchange.api.connector.ConnectorChannel;
import io.gravitee.exchange.api.websocket.channel.AbstractWebSocketChannel;
import io.gravitee.exchange.api.websocket.protocol.ProtocolAdapter;
import io.gravitee.exchange.connector.websocket.exception.WebSocketConnectorException;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.core.Vertx;
import io.vertx.rxjava3.core.http.WebSocketBase;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Guillaume LAMIRAND (guillaume.lamirand at graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
public class WebSocketConnectorChannel extends AbstractWebSocketChannel implements ConnectorChannel {

    public WebSocketConnectorChannel(
        final List<CommandHandler<? extends Command<?>, ? extends Reply<?>>> commandHandlers,
        final List<CommandAdapter<? extends Command<?>, ? extends Command<?>, ? extends Reply<?>>> commandAdapters,
        final List<ReplyAdapter<? extends Reply<?>, ? extends Reply<?>>> replyAdapters,
        final Vertx vertx,
        final WebSocketBase webSocket,
        final ProtocolAdapter protocolAdapter
    ) {
        super(commandHandlers, commandAdapters, replyAdapters, vertx, webSocket, protocolAdapter);
    }

    @Override
    public Completable initialize() {
        return super
            .initialize()
            .andThen(
                Single.defer(() -> {
                    HelloCommand helloCommand = new HelloCommand(new HelloCommandPayload(UUID.randomUUID().toString()));
                    return sendHelloCommand(helloCommand)
                        .onErrorResumeNext(throwable -> {
                            if (throwable instanceof ChannelException) {
                                return Single.error(new WebSocketConnectorException("Hello handshake failed", throwable, true));
                            } else {
                                return Single.error(throwable);
                            }
                        })
                        .doOnSuccess(reply -> {
                            if (reply.getCommandStatus() == CommandStatus.SUCCEEDED) {
                                this.targetId = reply.getPayload().getTargetId();
                                this.active = true;
                            } else {
                                throw new WebSocketConnectorException(
                                    String.format("Hello handshake failed: %s", reply.getErrorDetails()),
                                    false
                                );
                            }
                        });
                })
            )
            .ignoreElement();
    }

    @Override
    protected boolean expectHelloCommand() {
        return false;
    }
}
