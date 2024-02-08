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
import io.gravitee.exchange.api.command.CommandAdapter;
import io.gravitee.exchange.api.command.CommandHandler;
import io.gravitee.exchange.api.command.Reply;
import io.gravitee.exchange.api.command.ReplyAdapter;
import java.util.List;

public interface ConnectorCommandHandlersFactory {
    /**
     * Build a list of command handlers dedicated to the specified context.
     *
     * @param connectorCommandContext the command context
     * @return a list of command handlers
     */
    List<CommandHandler<? extends Command<?>, ? extends Reply<?>>> buildCommandHandlers(
        final ConnectorCommandContext connectorCommandContext
    );

    /**
     * Build a list of command decorators dedicated to the specified context.
     *
     * @param connectorCommandContext the command context
     * @return a list of command decorators
     */
    List<CommandAdapter<? extends Command<?>, ? extends Command<?>, ? extends Reply<?>>> buildCommandAdapters(
        final ConnectorCommandContext connectorCommandContext
    );

    /**
     * Build a list of command decorators dedicated to the specified context.
     *
     * @param connectorCommandContext the command context
     * @return a list of command decorators
     */
    List<ReplyAdapter<? extends Reply<?>, ? extends Reply<?>>> buildReplyAdapters(final ConnectorCommandContext connectorCommandContext);
}
