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

import io.gravitee.exchange.api.configuration.PrefixConfiguration;
import io.gravitee.exchange.api.controller.ControllerCommandHandlersFactory;
import io.gravitee.exchange.api.controller.ExchangeController;
import io.gravitee.exchange.api.websocket.command.ExchangeSerDe;
import io.gravitee.exchange.controller.core.DefaultExchangeController;
import io.gravitee.exchange.controller.core.channel.primary.PrimaryChannelManager;
import io.gravitee.exchange.controller.core.cluster.ControllerClusterManager;
import io.gravitee.exchange.controller.websocket.auth.WebSocketControllerAuthentication;
import io.gravitee.exchange.controller.websocket.server.WebSocketControllerServerConfiguration;
import io.gravitee.exchange.controller.websocket.server.WebSocketControllerServerVerticle;
import io.gravitee.node.api.cache.CacheManager;
import io.gravitee.node.api.cluster.ClusterManager;
import io.gravitee.node.certificates.KeyStoreLoaderManager;
import io.gravitee.node.vertx.server.http.VertxHttpServer;
import io.gravitee.node.vertx.server.http.VertxHttpServerFactory;
import io.gravitee.node.vertx.server.http.VertxHttpServerOptions;
import io.reactivex.rxjava3.core.Completable;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.VertxOptions;
import io.vertx.rxjava3.core.Vertx;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Guillaume LAMIRAND (guillaume.lamirand at graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
public class WebSocketExchangeController extends DefaultExchangeController implements ExchangeController {

    private static final String HTTP_PREFIX = "controller.ws.http";
    private static final String VERTICLE_INSTANCE = "controller.ws.instances";

    private final Vertx vertx;
    private final KeyStoreLoaderManager keyStoreLoaderManager;
    private final WebSocketControllerServerConfiguration serverConfiguration;
    private final WebSocketControllerAuthentication<?> controllerAuthentication;
    private final ControllerCommandHandlersFactory controllerCommandHandlersFactory;
    private final ExchangeSerDe commandSerDe;
    private final PrimaryChannelManager primaryChannelManager;

    private String websocketServerVerticleId;

    public WebSocketExchangeController(
        final PrefixConfiguration prefixConfiguration,
        final ControllerClusterManager controllerClusterManager,
        final ClusterManager clusterManager,
        final CacheManager cacheManager,
        final Vertx vertx,
        final KeyStoreLoaderManager keyStoreLoaderManager,
        final WebSocketControllerAuthentication<?> controllerAuthentication,
        final ControllerCommandHandlersFactory controllerCommandHandlersFactory,
        final ExchangeSerDe exchangeSerDe,
        final PrimaryChannelManager primaryChannelManager
    ) {
        super(prefixConfiguration, controllerClusterManager, clusterManager, cacheManager);
        this.vertx = vertx;
        this.keyStoreLoaderManager = keyStoreLoaderManager;
        this.serverConfiguration = new WebSocketControllerServerConfiguration(prefixConfiguration);
        this.controllerAuthentication = controllerAuthentication;
        this.controllerCommandHandlersFactory = controllerCommandHandlersFactory;
        this.commandSerDe = exchangeSerDe;
        this.primaryChannelManager = primaryChannelManager;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        deployVerticle();
    }

    private void deployVerticle() {
        int instances = prefixConfiguration.getProperty(VERTICLE_INSTANCE, Integer.class, 0);
        int verticleInstances = (instances < 1) ? VertxOptions.DEFAULT_EVENT_LOOP_POOL_SIZE : instances;
        log.info("Starting Exchange Controller Websocket [{} instance(s)]", verticleInstances);

        DeploymentOptions options = new DeploymentOptions().setInstances(verticleInstances);
        VertxHttpServerFactory vertxHttpServerFactory = new VertxHttpServerFactory(vertx);
        VertxHttpServerOptions vertxHttpServerOptions = createVertxHttpServerOptions();
        WebSocketRequestHandler webSocketRequestHandler = new WebSocketRequestHandler(
            vertx,
            this,
            controllerAuthentication,
            controllerCommandHandlersFactory,
            commandSerDe,
            primaryChannelManager
        );
        vertx
            .deployVerticle(
                () -> {
                    VertxHttpServer vertxHttpServer = vertxHttpServerFactory.create(vertxHttpServerOptions);
                    return new WebSocketControllerServerVerticle(vertxHttpServer.newInstance(), webSocketRequestHandler);
                },
                options
            )
            .flatMapCompletable(deploymentId -> {
                websocketServerVerticleId = deploymentId;
                return Completable.complete();
            })
            .subscribe(
                () -> log.info("Exchange Controller Websocket deployed successfully"),
                error -> log.error("Unable to deploy Exchange Controller Websocket", error.getCause())
            );
    }

    private VertxHttpServerOptions createVertxHttpServerOptions() {
        VertxHttpServerOptions.VertxHttpServerOptionsBuilder<?, ?> builder = VertxHttpServerOptions
            .builder()
            .prefix(prefixConfiguration.prefixKey(HTTP_PREFIX))
            .keyStoreLoaderManager(keyStoreLoaderManager)
            .environment(prefixConfiguration.environment())
            .defaultPort(serverConfiguration.port())
            .host(serverConfiguration.host())
            .alpn(serverConfiguration.alpn());
        if (serverConfiguration.secured()) {
            builder =
                builder
                    .secured(true)
                    .keyStoreType(serverConfiguration.keyStoreType())
                    .keyStorePath(serverConfiguration.keyStorePath())
                    .keyStorePassword(serverConfiguration.keyStorePassword())
                    .trustStoreType(serverConfiguration.trustStoreType())
                    .trustStorePaths(List.of(serverConfiguration.trustStorePath()))
                    .trustStorePassword(serverConfiguration.trustStorePassword())
                    .clientAuth(serverConfiguration.clientAuth());
        }
        return builder
            .compressionSupported(serverConfiguration.compressionSupported())
            .idleTimeout(serverConfiguration.idleTimeout())
            .tcpKeepAlive(serverConfiguration.tcpKeepAlive())
            .maxHeaderSize(serverConfiguration.maxHeaderSize())
            .maxChunkSize(serverConfiguration.maxChunkSize())
            .maxWebSocketFrameSize(serverConfiguration.maxWebSocketFrameSize())
            .maxWebSocketMessageSize(serverConfiguration.maxWebSocketMessageSize())
            .handle100Continue(true)
            // Need to be enabled to have MaxWebSocketFrameSize and MaxWebSocketMessageSize set (otherwise `gravitee-node` is skipping them)
            .websocketEnabled(true)
            .build();
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        undeployVerticle();
    }

    private void undeployVerticle() {
        if (websocketServerVerticleId != null) {
            vertx
                .undeploy(websocketServerVerticleId)
                .subscribe(
                    () -> log.info("Exchange Controller Websocket undeployed successfully"),
                    throwable -> log.error("Unable to undeploy Exchange Controller Websocket", throwable.getCause())
                );
        }
    }
}
