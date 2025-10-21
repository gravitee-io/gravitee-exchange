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
package io.gravitee.exchange.controller.websocket.server;

import static io.gravitee.exchange.api.controller.ws.WebsocketControllerConstants.EXCHANGE_CONTROLLER_PATH;
import static io.gravitee.exchange.api.controller.ws.WebsocketControllerConstants.EXCHANGE_PROTOCOL_HEADER;
import static io.gravitee.exchange.api.controller.ws.WebsocketControllerConstants.LEGACY_CONTROLLER_PATH;

import io.gravitee.exchange.api.websocket.protocol.ProtocolVersion;
import io.gravitee.exchange.controller.websocket.WebSocketRequestHandler;
import io.reactivex.rxjava3.core.Completable;
import io.vertx.rxjava3.core.AbstractVerticle;
import io.vertx.rxjava3.core.http.HttpServer;
import io.vertx.rxjava3.ext.web.Router;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Guillaume LAMIRAND (guillaume.lamirand at graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
@RequiredArgsConstructor
public class WebSocketControllerServerVerticle extends AbstractVerticle {

    private final HttpServer controllerWebSocketHttpServer;
    private final WebSocketRequestHandler webSocketRequestHandler;

    @Override
    public Completable rxStart() {
        Router router = Router.router(vertx);
        router.route(EXCHANGE_CONTROLLER_PATH).handler(webSocketRequestHandler);
        router
            .route(LEGACY_CONTROLLER_PATH)
            .handler(ctx -> {
                ctx.request().headers().add(EXCHANGE_PROTOCOL_HEADER, ProtocolVersion.LEGACY.version());
                webSocketRequestHandler.handle(ctx);
            });
        // Default non-handled requests:
        router.route().handler(ctx -> ctx.fail(404));
        // Failure handler
        router
            .route()
            .failureHandler(ctx -> {
                log.error(
                    "[{}] An error occurred while processing websocket request",
                    webSocketRequestHandler.getExchangeController().identifyConfiguration().id(),
                    ctx.failure()
                );
                ctx.response().setStatusCode(ctx.statusCode()).end();
            });

        return controllerWebSocketHttpServer
            .requestHandler(router)
            .rxListen()
            .doOnSuccess(server -> log.info("Controller websocket listener ready to accept requests on port {}", server.actualPort()))
            .doOnError(throwable -> log.error("Unable to start Controller websocket listener", throwable))
            .ignoreElement();
    }

    @Override
    public Completable rxStop() {
        return controllerWebSocketHttpServer
            .close()
            .doOnSubscribe(disposable -> log.info("Stopping Controller websocket server ..."))
            .doFinally(() -> log.info("HTTP Server has been successfully stopped"));
    }
}
