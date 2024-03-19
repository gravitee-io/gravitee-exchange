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
package io.gravitee.exchange.api.connector;

import io.gravitee.exchange.api.command.Command;
import io.gravitee.exchange.api.command.CommandHandler;
import io.gravitee.exchange.api.command.Reply;
import io.gravitee.exchange.api.connector.exception.ConnectorClosedException;
import io.gravitee.exchange.api.connector.exception.ConnectorInitializationException;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import java.util.List;
import java.util.Map;

/**
 * @author Guillaume LAMIRAND (guillaume.lamirand at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface ExchangeConnector {
    /**
     * Must be called to initialize connector
     *
     * @return returns a {@code Completable} instance that completes in case of success or a {@link ConnectorInitializationException} is emitted
     */
    Completable initialize();

    /**
     * Must be called to properly close the connector
     *
     * @return returns a {@code Completable} instance that completes in case of success or a {@link ConnectorClosedException} is emitted
     */
    Completable close();

    /**
     * Get the target id of the connector .
     *
     * @return target id.
     */
    String targetId();

    /**
     * Return <code>true</code> when the current instance is active and ready, <code>false</code> otherwise.
     *
     * @return status of the connector.
     */
    boolean isActive();

    /**
     * Returns <code>true</code> when the current instance is PRIMARY.
     *
     * @return boolean about primary status
     */
    boolean isPrimary();

    /**
     * Set primary status of this connector.
     *
     * @param isPrimary {@code true} if this node should be primary, {@code false} otherwise.
     */
    void setPrimary(final boolean isPrimary);

    /**
     * Send a command to the target.
     *
     * @param command the command to send.
     */
    Single<Reply<?>> sendCommand(Command<?> command);

    /**
     * Add customs {@link CommandHandler} to this connector. This will only happen new handlers to the existing handlers
     */
    void addCommandHandlers(final List<CommandHandler<? extends Command<?>, ? extends Reply<?>>> commandHandlers);
}
