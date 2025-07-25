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

import static io.gravitee.exchange.api.controller.ws.WebsocketControllerConstants.EXCHANGE_PROTOCOL_HEADER;

import io.gravitee.common.utils.RxHelper;
import io.gravitee.exchange.api.command.Command;
import io.gravitee.exchange.api.command.CommandAdapter;
import io.gravitee.exchange.api.command.CommandHandler;
import io.gravitee.exchange.api.command.Reply;
import io.gravitee.exchange.api.command.ReplyAdapter;
import io.gravitee.exchange.api.controller.ws.WebsocketControllerConstants;
import io.gravitee.exchange.api.websocket.command.ExchangeSerDe;
import io.gravitee.exchange.api.websocket.protocol.ProtocolVersion;
import io.gravitee.exchange.connector.embedded.EmbeddedExchangeConnector;
import io.gravitee.exchange.connector.websocket.channel.WebSocketConnectorChannel;
import io.gravitee.exchange.connector.websocket.client.WebSocketConnectorClientFactory;
import io.gravitee.exchange.connector.websocket.exception.WebSocketConnectorException;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.vertx.core.http.WebSocketConnectOptions;
import io.vertx.rxjava3.core.Vertx;
import io.vertx.rxjava3.core.http.HttpClient;
import io.vertx.rxjava3.core.http.WebSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Guillaume LAMIRAND (guillaume.lamirand at graviteesource.com)
 * @author GraviteeSource Team
 */
@SuperBuilder
@Slf4j
public class WebSocketExchangeConnector extends EmbeddedExchangeConnector {

    private final ProtocolVersion protocolVersion;
    private final List<CommandHandler<? extends Command<?>, ? extends Reply<?>>> commandHandlers;
    private final List<CommandAdapter<? extends Command<?>, ? extends Command<?>, ? extends Reply<?>>> commandAdapters;
    private final List<ReplyAdapter<? extends Reply<?>, ? extends Reply<?>>> replyAdapters;
    private final Vertx vertx;
    private final WebSocketConnectorClientFactory webSocketConnectorClientFactory;
    private final ExchangeSerDe exchangeSerDe;

    public WebSocketExchangeConnector(
        final ProtocolVersion protocolVersion,
        final List<CommandHandler<? extends Command<?>, ? extends Reply<?>>> commandHandlers,
        final List<CommandAdapter<? extends Command<?>, ? extends Command<?>, ? extends Reply<?>>> commandAdapters,
        final List<ReplyAdapter<? extends Reply<?>, ? extends Reply<?>>> replyAdapters,
        final Vertx vertx,
        final WebSocketConnectorClientFactory webSocketConnectorClientFactory,
        final ExchangeSerDe exchangeSerDe
    ) {
        this.protocolVersion = protocolVersion;
        this.commandHandlers = commandHandlers != null ? new ArrayList<>(commandHandlers) : new ArrayList<>();
        this.commandAdapters = commandHandlers != null ? new ArrayList<>(commandAdapters) : new ArrayList<>();
        this.replyAdapters = commandHandlers != null ? new ArrayList<>(replyAdapters) : new ArrayList<>();
        this.vertx = vertx;
        this.webSocketConnectorClientFactory = webSocketConnectorClientFactory;
        this.exchangeSerDe = exchangeSerDe;
    }

    @Override
    public Completable initialize() {
        return Completable
            .fromRunnable(() -> setPrimary(false))
            .andThen(this.connect())
            .flatMapCompletable(webSocket -> {
                connectorChannel =
                    new WebSocketConnectorChannel(
                        commandHandlers,
                        commandAdapters,
                        replyAdapters,
                        vertx,
                        webSocket,
                        protocolVersion.adapterFactory().apply(exchangeSerDe)
                    );
                return connectorChannel
                    .initialize()
                    .doOnComplete(() ->
                        webSocket.closeHandler(v -> {
                            log.warn("Exchange Connector has been closed with status code '{}'", webSocket.closeStatusCode());
                            if (shouldReconnect(webSocket)) {
                                initialize().onErrorComplete().subscribeOn(Schedulers.io()).subscribe();
                            }
                        })
                    );
            })
            .retryWhen(
                RxHelper.retryExponentialBackoff(
                    1,
                    300,
                    TimeUnit.SECONDS,
                    1.5,
                    throwable -> throwable instanceof WebSocketConnectorException connectorException && connectorException.isRetryable()
                )
            )
            .doOnError(throwable -> log.error("Unable to connect to Exchange Controller Endpoint."))
            .doOnComplete(() -> log.info("Exchange Connector is now connected and ready"));
    }

    private Single<WebSocket> connect() {
        return Maybe
            .fromCallable(webSocketConnectorClientFactory::nextEndpoint)
            .switchIfEmpty(
                Maybe.fromRunnable(() -> {
                    throw new WebSocketConnectorException(
                        "No Exchange Controller Endpoint is defined. Please check your configuration",
                        false
                    );
                })
            )
            .toSingle()
            .flatMap(webSocketEndpoint -> {
                log.debug("Trying to connect to the Exchange Controller WebSocket '{}'", webSocketEndpoint.getUrl());
                HttpClient httpClient = webSocketConnectorClientFactory.createHttpClient(webSocketEndpoint);
                WebSocketConnectOptions webSocketConnectOptions = new WebSocketConnectOptions()
                    .setURI(webSocketEndpoint.resolvePath(WebsocketControllerConstants.EXCHANGE_CONTROLLER_PATH))
                    .addHeader(EXCHANGE_PROTOCOL_HEADER, protocolVersion.version());

                if (webSocketConnectorClientFactory.getConfiguration().headers() != null) {
                    webSocketConnectorClientFactory.getConfiguration().headers().forEach(webSocketConnectOptions::addHeader);
                }
                return httpClient
                    .rxWebSocket(webSocketConnectOptions)
                    .doOnSuccess(webSocket -> {
                        webSocketConnectorClientFactory.resetEndpointRetries();
                        log.debug("Exchange Connector has successfully connected to the Exchange Controller WebSocket");
                    })
                    .onErrorResumeNext(throwable -> {
                        log.error(
                            "Unable to connect to the Exchange Controller Endpoint {} times, retrying...",
                            webSocketConnectorClientFactory.endpointRetries(),
                            throwable
                        );
                        // Force the HTTP client to close after a defect.
                        return httpClient
                            .close()
                            .andThen(
                                Single.error(
                                    new WebSocketConnectorException("Unable to connect to Exchange Controller Endpoint", throwable, true)
                                )
                            );
                    });
            });
    }

    @Override
    public void addCommandHandlers(final List<CommandHandler<? extends Command<?>, ? extends Reply<?>>> commandHandlers) {
        if (commandHandlers != null) {
            commandHandlers.forEach(commandHandler -> {
                if (
                    this.commandHandlers.stream()
                        .noneMatch(newCommandHandler -> newCommandHandler.supportType().equals(commandHandler.supportType()))
                ) {
                    this.commandHandlers.add(commandHandler);
                }
            });
        }
        super.addCommandHandlers(commandHandlers);
    }

    private boolean shouldReconnect(WebSocket webSocket) {
        return (
            webSocketConnectorClientFactory.getConfiguration().autoReconnect() || !Objects.equals(webSocket.closeStatusCode(), (short) 1000)
        );
    }
}
