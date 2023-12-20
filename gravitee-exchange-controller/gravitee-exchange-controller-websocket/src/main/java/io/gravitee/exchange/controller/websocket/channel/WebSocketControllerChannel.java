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
package io.gravitee.exchange.controller.websocket.channel;

import io.gravitee.exchange.api.command.Command;
import io.gravitee.exchange.api.command.CommandHandler;
import io.gravitee.exchange.api.command.Reply;
import io.gravitee.exchange.api.command.ReplyHandler;
import io.gravitee.exchange.api.command.goodbye.GoodByeCommand;
import io.gravitee.exchange.api.command.goodbye.GoodByeCommandPayload;
import io.gravitee.exchange.api.controller.ControllerChannel;
import io.gravitee.exchange.api.websocket.channel.AbstractWebSocketChannel;
import io.gravitee.exchange.api.websocket.command.ExchangeSerDe;
import io.gravitee.exchange.api.websocket.protocol.ProtocolAdapter;
import io.gravitee.exchange.controller.core.channel.primary.PrimaryChannelManager;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.CompletableEmitter;
import io.vertx.rxjava3.core.Vertx;
import io.vertx.rxjava3.core.http.ServerWebSocket;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Guillaume LAMIRAND (guillaume.lamirand at graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
public class WebSocketControllerChannel extends AbstractWebSocketChannel implements ControllerChannel {

    private final PrimaryChannelManager primaryChannelManager;

    public WebSocketControllerChannel(
        final List<CommandHandler<? extends Command<?>, ? extends Reply<?>>> commandHandlers,
        final List<ReplyHandler<? extends Command<?>, ? extends Command<?>, ? extends Reply<?>>> replyHandlers,
        final Vertx vertx,
        final ServerWebSocket webSocket,
        final ProtocolAdapter protocolAdapter,
        final PrimaryChannelManager primaryChannelManager
    ) {
        super(commandHandlers, replyHandlers, vertx, webSocket, protocolAdapter);
        this.primaryChannelManager = primaryChannelManager;
    }

    @Override
    public boolean isActive() {
        return this.active;
    }

    @Override
    protected boolean expectHelloCommand() {
        return true;
    }

    @Override
    public Completable close() {
        return Completable
            .defer(() -> {
                if (!webSocket.isClosed()) {
                    return send(new GoodByeCommand(new GoodByeCommandPayload(targetId, true))).ignoreElement();
                }
                return Completable.complete();
            })
            .onErrorComplete()
            .doFinally(this::cleanChannel);
    }

    @Override
    public void enforceActiveStatus(final boolean isActive) {
        this.active = isActive;
    }

    @Override
    protected void closeHandler() {
        this.primaryChannelManager.sendChannelEvent(this, false);
    }

    @Override
    protected void pongHandler() {
        if (this.active) {
            primaryChannelManager.sendChannelEvent(this, true);
        }
    }

    @Override
    protected void handleHelloCommand(
        final CompletableEmitter emitter,
        final Command<?> command,
        final CommandHandler<Command<?>, Reply<?>> commandHandler
    ) {
        if (commandHandler == null) {
            this.webSocket.close((short) 1011, "No handler for hello command").subscribe();
            emitter.onError(new WebSocketChannelInitializationException("No handler found for hello command. Closing connection."));
        } else {
            super.handleHelloCommand(emitter, command, commandHandler);
        }
    }
}
