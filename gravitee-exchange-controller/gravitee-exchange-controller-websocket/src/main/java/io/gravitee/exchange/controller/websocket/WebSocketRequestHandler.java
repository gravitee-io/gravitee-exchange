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
package io.gravitee.exchange.controller.websocket;

import io.gravitee.common.http.HttpStatusCode;
import io.gravitee.exchange.api.command.Command;
import io.gravitee.exchange.api.command.CommandHandler;
import io.gravitee.exchange.api.command.Reply;
import io.gravitee.exchange.api.command.ReplyHandler;
import io.gravitee.exchange.api.controller.ControllerChannel;
import io.gravitee.exchange.api.controller.ControllerCommandContext;
import io.gravitee.exchange.api.controller.ControllerCommandHandlersFactory;
import io.gravitee.exchange.api.controller.ExchangeController;
import io.gravitee.exchange.api.websocket.command.ExchangeSerDe;
import io.gravitee.exchange.api.websocket.protocol.ProtocolAdapterFactory;
import io.gravitee.exchange.controller.core.channel.primary.PrimaryChannelManager;
import io.gravitee.exchange.controller.websocket.auth.WebSocketControllerAuthentication;
import io.gravitee.exchange.controller.websocket.channel.WebSocketControllerChannel;
import io.vertx.rxjava3.core.Vertx;
import io.vertx.rxjava3.core.http.HttpServerRequest;
import io.vertx.rxjava3.ext.web.RoutingContext;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Guillaume LAMIRAND (guillaume.lamirand at graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
@RequiredArgsConstructor
public class WebSocketRequestHandler implements io.vertx.core.Handler<io.vertx.rxjava3.ext.web.RoutingContext> {

    private final Vertx vertx;
    private final ExchangeController exchangeController;
    private final WebSocketControllerAuthentication<?> controllerAuthentication;
    private final ControllerCommandHandlersFactory controllerCommandHandlersFactory;
    private final ExchangeSerDe commandSerDe;
    private final PrimaryChannelManager primaryChannelManager;

    @Override
    public void handle(final RoutingContext routingContext) {
        log.debug("Incoming connection on Websocket Controller");
        HttpServerRequest request = routingContext.request();
        ControllerCommandContext controllerContext = controllerAuthentication.authenticate(request);
        if (controllerContext.isValid()) {
            request
                .toWebSocket()
                .flatMapCompletable(webSocket -> {
                    List<CommandHandler<? extends Command<?>, ? extends Reply<?>>> commandHandlers = controllerCommandHandlersFactory.buildCommandHandlers(
                        controllerContext
                    );
                    List<ReplyHandler<? extends Command<?>, ? extends Command<?>, ? extends Reply<?>>> replyHandlers = controllerCommandHandlersFactory.buildReplyHandlers(
                        controllerContext
                    );

                    ControllerChannel websocketControllerChannel = new WebSocketControllerChannel(
                        commandHandlers,
                        replyHandlers,
                        vertx,
                        webSocket,
                        ProtocolAdapterFactory.create(request, commandSerDe),
                        primaryChannelManager
                    );
                    return exchangeController
                        .register(websocketControllerChannel)
                        .doOnComplete(() ->
                            webSocket.closeHandler(v ->
                                exchangeController.unregister(websocketControllerChannel).onErrorComplete().subscribe()
                            )
                        )
                        .doOnError(throwable -> {
                            log.error("Unable to register websocket channel");
                            webSocket.close((short) 1011, "Unexpected error while registering channel").subscribe();
                        })
                        .onErrorComplete();
                })
                .doOnError(throwable -> routingContext.fail(HttpStatusCode.INTERNAL_SERVER_ERROR_500))
                .subscribe();
        } else {
            // Authentication failed so reject the request
            log.debug("Unauthorized request on Websocket Controller");
            routingContext.fail(HttpStatusCode.UNAUTHORIZED_401);
        }
    }
}
