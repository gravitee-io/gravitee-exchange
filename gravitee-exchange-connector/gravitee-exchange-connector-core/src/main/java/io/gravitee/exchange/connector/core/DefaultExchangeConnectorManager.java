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
package io.gravitee.exchange.connector.core;

import io.gravitee.exchange.api.connector.ExchangeConnector;
import io.gravitee.exchange.api.connector.ExchangeConnectorManager;
import io.gravitee.exchange.connector.core.command.goodbye.GoodByeCommandHandler;
import io.gravitee.exchange.connector.core.command.healtcheck.HealthCheckCommandHandler;
import io.gravitee.exchange.connector.core.command.primary.PrimaryCommandHandler;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Guillaume LAMIRAND (guillaume.lamirand at graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
public class DefaultExchangeConnectorManager implements ExchangeConnectorManager {

    private final Map<String, ExchangeConnector> exchangeConnectors = new ConcurrentHashMap<>();

    @Override
    public Maybe<ExchangeConnector> get(final String targetId) {
        return Maybe.fromCallable(() -> exchangeConnectors.get(targetId));
    }

    @Override
    public Completable register(final ExchangeConnector exchangeConnector) {
        return Completable
            .fromRunnable(() -> {
                log.debug("Registering new connector");
                // Add custom handlers to deal with healthcheck and primary commands
                exchangeConnector.addCommandHandlers(
                    List.of(
                        new GoodByeCommandHandler(exchangeConnector),
                        new HealthCheckCommandHandler(exchangeConnector),
                        new PrimaryCommandHandler(exchangeConnector)
                    )
                );
            })
            .andThen(exchangeConnector.initialize())
            .doOnComplete(() -> {
                log.debug("New connector successfully register for target '{}'", exchangeConnector.targetId());
                exchangeConnectors.put(exchangeConnector.targetId(), exchangeConnector);
            })
            .onErrorResumeNext(throwable -> {
                log.warn("Unable to register new connector for target '{}'", exchangeConnector.targetId(), throwable);
                return unregister(exchangeConnector).andThen(Completable.error(throwable));
            });
    }

    @Override
    public Completable unregister(final ExchangeConnector exchangeConnector) {
        return Completable
            .defer(() -> {
                if (exchangeConnector.targetId() != null) {
                    exchangeConnectors.remove(exchangeConnector.targetId(), exchangeConnector);
                }
                return exchangeConnector.close();
            })
            .doOnComplete(() -> log.debug("Connector successfully unregister for target '{}'", exchangeConnector.targetId()))
            .doOnError(throwable -> log.warn("Unable to unregister connector for target '{}'", exchangeConnector.targetId(), throwable));
    }
}
