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

import io.gravitee.exchange.api.configuration.IdentifyConfiguration;
import io.gravitee.exchange.api.controller.ControllerCommandHandlersFactory;
import io.gravitee.exchange.api.controller.ExchangeController;
import io.gravitee.exchange.api.websocket.command.ExchangeSerDe;
import io.gravitee.exchange.controller.core.DefaultExchangeController;
import io.gravitee.exchange.controller.websocket.auth.WebSocketControllerAuthentication;
import io.gravitee.exchange.controller.websocket.server.WebSocketControllerServerConfiguration;
import io.gravitee.exchange.controller.websocket.server.WebSocketControllerServerVerticle;
import io.gravitee.node.api.cache.CacheManager;
import io.gravitee.node.api.certificate.KeyStoreLoaderFactoryRegistry;
import io.gravitee.node.api.certificate.KeyStoreLoaderOptions;
import io.gravitee.node.api.certificate.TrustStoreLoaderOptions;
import io.gravitee.node.api.cluster.ClusterManager;
import io.gravitee.node.management.http.endpoint.ManagementEndpointManager;
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
    private final KeyStoreLoaderFactoryRegistry<KeyStoreLoaderOptions> keyStoreLoaderFactoryRegistry;
    private final KeyStoreLoaderFactoryRegistry<TrustStoreLoaderOptions> trustStoreLoaderFactoryRegistry;
    private final WebSocketControllerServerConfiguration serverConfiguration;
    private final WebSocketControllerAuthentication<?> controllerAuthentication;
    private final ControllerCommandHandlersFactory controllerCommandHandlersFactory;
    private final ExchangeSerDe commandSerDe;
    private String websocketServerVerticleId;

    public WebSocketExchangeController(
        final IdentifyConfiguration identifyConfiguration,
        final ClusterManager clusterManager,
        final CacheManager cacheManager,
        final Vertx vertx,
        final KeyStoreLoaderFactoryRegistry<KeyStoreLoaderOptions> keyStoreLoaderFactoryRegistry,
        final KeyStoreLoaderFactoryRegistry<TrustStoreLoaderOptions> trustStoreLoaderFactoryRegistry,
        final WebSocketControllerAuthentication<?> controllerAuthentication,
        final ControllerCommandHandlersFactory controllerCommandHandlersFactory,
        final ExchangeSerDe exchangeSerDe
    ) {
        this(
            identifyConfiguration,
            clusterManager,
            cacheManager,
            null,
            vertx,
            keyStoreLoaderFactoryRegistry,
            trustStoreLoaderFactoryRegistry,
            controllerAuthentication,
            controllerCommandHandlersFactory,
            exchangeSerDe
        );
    }

    public WebSocketExchangeController(
        final IdentifyConfiguration identifyConfiguration,
        final ClusterManager clusterManager,
        final CacheManager cacheManager,
        final ManagementEndpointManager managementEndpointManager,
        final Vertx vertx,
        final KeyStoreLoaderFactoryRegistry<KeyStoreLoaderOptions> keyStoreLoaderFactoryRegistry,
        final KeyStoreLoaderFactoryRegistry<TrustStoreLoaderOptions> trustStoreLoaderFactoryRegistry,
        final WebSocketControllerAuthentication<?> controllerAuthentication,
        final ControllerCommandHandlersFactory controllerCommandHandlersFactory,
        final ExchangeSerDe exchangeSerDe
    ) {
        super(identifyConfiguration, clusterManager, cacheManager, managementEndpointManager);
        this.vertx = vertx;
        this.serverConfiguration = new WebSocketControllerServerConfiguration(identifyConfiguration);
        this.keyStoreLoaderFactoryRegistry = keyStoreLoaderFactoryRegistry;
        this.trustStoreLoaderFactoryRegistry = trustStoreLoaderFactoryRegistry;
        this.controllerAuthentication = controllerAuthentication;
        this.controllerCommandHandlersFactory = controllerCommandHandlersFactory;
        this.commandSerDe = exchangeSerDe;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        deployVerticle();
    }

    private void deployVerticle() {
        int instances = identifyConfiguration.getProperty(VERTICLE_INSTANCE, Integer.class, 0);
        int verticleInstances = (instances < 1) ? VertxOptions.DEFAULT_EVENT_LOOP_POOL_SIZE : instances;
        log.info("Starting Exchange Controller Websocket [{} instance(s)]", verticleInstances);

        DeploymentOptions options = new DeploymentOptions().setInstances(verticleInstances);
        VertxHttpServerFactory vertxHttpServerFactory = new VertxHttpServerFactory(
            vertx,
            keyStoreLoaderFactoryRegistry,
            trustStoreLoaderFactoryRegistry
        );
        VertxHttpServerOptions vertxHttpServerOptions = createVertxHttpServerOptions();
        WebSocketRequestHandler webSocketRequestHandler = new WebSocketRequestHandler(
            vertx,
            this,
            controllerAuthentication,
            controllerCommandHandlersFactory,
            commandSerDe
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
                error -> log.error("Unable to deploy Exchange Controller Websocket", error)
            );
    }

    private VertxHttpServerOptions createVertxHttpServerOptions() {
        VertxHttpServerOptions.VertxHttpServerOptionsBuilder<?, ?> builder = VertxHttpServerOptions
            .builder()
            .prefix(identifyConfiguration.identifyProperty(HTTP_PREFIX))
            .environment(identifyConfiguration.environment())
            .defaultPort(serverConfiguration.port())
            .host(serverConfiguration.host())
            .alpn(serverConfiguration.alpn());
        if (serverConfiguration.secured()) {
            builder =
                builder
                    .secured(true)
                    .keyStoreLoaderOptions(
                        KeyStoreLoaderOptions
                            .builder()
                            .type(serverConfiguration.keyStoreType())
                            .paths(List.of(serverConfiguration.keyStorePath()))
                            .password(serverConfiguration.keyStorePassword())
                            .build()
                    )
                    .trustStoreLoaderOptions(
                        TrustStoreLoaderOptions
                            .builder()
                            .type(serverConfiguration.trustStoreType())
                            .paths(List.of(serverConfiguration.trustStorePath()))
                            .password(serverConfiguration.trustStorePassword())
                            .build()
                    )
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
            try {
                vertx.undeploy(websocketServerVerticleId).blockingAwait(); // Need to be "blocking" to make sure it is done before continuing
                log.info("Exchange Controller Websocket undeployed successfully");
            } catch (Exception e) {
                log.error("Unable to undeploy Exchange Controller Websocket", e);
            }
        }
    }
}
