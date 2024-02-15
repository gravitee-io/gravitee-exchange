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
package io.gravitee.exchange.connector.embedded;

import io.gravitee.exchange.api.command.Command;
import io.gravitee.exchange.api.command.CommandHandler;
import io.gravitee.exchange.api.command.Reply;
import io.gravitee.exchange.api.connector.ConnectorChannel;
import io.gravitee.exchange.api.connector.ExchangeConnector;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * @author Guillaume LAMIRAND (guillaume.lamirand at graviteesource.com)
 * @author GraviteeSource Team
 */
@SuperBuilder
@NoArgsConstructor
public class EmbeddedExchangeConnector implements ExchangeConnector {

    protected ConnectorChannel connectorChannel;

    @Builder.Default
    private boolean primary = true;

    @Override
    public Completable initialize() {
        return connectorChannel.initialize();
    }

    @Override
    public Completable close() {
        return connectorChannel.close();
    }

    @Override
    public String targetId() {
        return connectorChannel.targetId();
    }

    @Override
    public boolean isActive() {
        return connectorChannel.isActive();
    }

    @Override
    public boolean isPrimary() {
        return primary;
    }

    @Override
    public void setPrimary(final boolean isPrimary) {
        this.primary = isPrimary;
    }

    @Override
    public Single<Reply<?>> sendCommand(final Command<?> command) {
        return connectorChannel.send(command);
    }

    @Override
    public void addCommandHandlers(final List<CommandHandler<? extends Command<?>, ? extends Reply<?>>> commandHandlers) {
        this.connectorChannel.addCommandHandlers(commandHandlers);
    }
}
