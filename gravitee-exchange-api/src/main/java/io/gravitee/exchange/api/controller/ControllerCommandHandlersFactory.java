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
package io.gravitee.exchange.api.controller;

import io.gravitee.exchange.api.command.Command;
import io.gravitee.exchange.api.command.CommandAdapter;
import io.gravitee.exchange.api.command.CommandHandler;
import io.gravitee.exchange.api.command.Reply;
import io.gravitee.exchange.api.command.ReplyAdapter;
import io.gravitee.exchange.api.websocket.protocol.ProtocolVersion;
import java.util.List;

public interface ControllerCommandHandlersFactory {
    /**
     * Build a map of command handlers dedicated to the specified context.
     *
     * @param controllerCommandContext the command context
     * @return a list of command handlers indexed by type.
     */
    List<CommandHandler<? extends Command<?>, ? extends Reply<?>>> buildCommandHandlers(
        final ControllerCommandContext controllerCommandContext
    );

    /**
     * Build a list of command decorators dedicated to the specified context.
     *
     * @param controllerCommandContext the command context
     * @return a list of command decorators
     */
    default List<CommandAdapter<? extends Command<?>, ? extends Command<?>, ? extends Reply<?>>> buildCommandAdapters(
        final ControllerCommandContext controllerCommandContext,
        final ProtocolVersion protocolVersion
    ) {
        return List.of();
    }

    /**
     * Build a list of reply decorators dedicated to the specified context.
     *
     * @param controllerCommandContext the command context
     * @return a list of reply decorators
     */
    default List<ReplyAdapter<? extends Reply<?>, ? extends Reply<?>>> buildReplyAdapters(
        final ControllerCommandContext controllerCommandContext,
        final ProtocolVersion protocolVersion
    ) {
        return List.of();
    }
}
