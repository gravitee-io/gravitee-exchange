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
package io.gravitee.exchange.controller.embedded.channel;

import io.gravitee.exchange.api.channel.exception.ChannelInactiveException;
import io.gravitee.exchange.api.channel.exception.ChannelNoReplyException;
import io.gravitee.exchange.api.channel.exception.ChannelTimeoutException;
import io.gravitee.exchange.api.command.Command;
import io.gravitee.exchange.api.command.CommandHandler;
import io.gravitee.exchange.api.command.Reply;
import io.gravitee.exchange.api.command.ReplyHandler;
import io.gravitee.exchange.api.connector.ConnectorChannel;
import io.gravitee.exchange.api.controller.ControllerChannel;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Guillaume LAMIRAND (guillaume.lamirand at graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
public class EmbeddedChannel implements ControllerChannel, ConnectorChannel {

    private final String id = UUID.randomUUID().toString();
    private final String targetId;
    private final Map<String, CommandHandler<? extends Command<?>, ? extends Reply<?>>> commandHandlers = new ConcurrentHashMap<>();
    private final Map<String, ReplyHandler<? extends Command<?>, ? extends Command<?>, ? extends Reply<?>>> replyHandlers = new ConcurrentHashMap<>();
    private boolean active = false;

    @Builder
    public EmbeddedChannel(
        final String targetId,
        final List<CommandHandler<? extends Command<?>, ? extends Reply<?>>> commandHandlers,
        final List<ReplyHandler<? extends Command<?>, ? extends Command<?>, ? extends Reply<?>>> replyHandlers
    ) {
        this.targetId = targetId;
        addCommandHandlers(commandHandlers);
        addReplyHandlers(replyHandlers);
    }

    @Override
    public boolean isActive() {
        return this.active;
    }

    @Override
    public void enforceActiveStatus(final boolean isActive) {
        this.active = isActive;
    }

    @Override
    public String id() {
        return this.id;
    }

    @Override
    public String targetId() {
        return this.targetId;
    }

    @Override
    public Completable initialize() {
        return Completable.fromRunnable(() -> this.active = true);
    }

    @Override
    public Completable close() {
        return Completable.fromRunnable(() -> this.active = false);
    }

    @Override
    public <C extends Command<?>, R extends Reply<?>> Single<R> send(final C command) {
        CommandHandler<?, ?> commandHandler = commandHandlers.get(command.getType());
        if (commandHandler != null) {
            return Single
                .defer(() -> {
                    if (!active) {
                        return Single.error(new ChannelInactiveException());
                    }
                    ReplyHandler<Command<?>, Command<?>, Reply<?>> replyHandler = (ReplyHandler<Command<?>, Command<?>, Reply<?>>) replyHandlers.get(
                        command.getType()
                    );
                    if (replyHandler != null) {
                        return replyHandler.decorate(command);
                    } else {
                        return Single.just(command);
                    }
                })
                .flatMap(single -> {
                    CommandHandler<C, R> castHandler = (CommandHandler<C, R>) commandHandler;
                    return castHandler.handle(command);
                })
                .flatMap(reply -> {
                    ReplyHandler<Command<?>, Command<?>, Reply<?>> replyHandler = (ReplyHandler<Command<?>, Command<?>, Reply<?>>) replyHandlers.get(
                        reply.getType()
                    );
                    if (replyHandler != null) {
                        return (Single<R>) replyHandler.handle(reply);
                    } else {
                        return Single.just(reply);
                    }
                })
                .timeout(
                    command.getReplyTimeoutMs(),
                    TimeUnit.MILLISECONDS,
                    Single.fromCallable(() -> {
                        log.warn("No reply received in time for command [{}, {}]", command.getType(), command.getId());
                        throw new ChannelTimeoutException();
                    })
                );
        } else {
            return Single.error(() -> {
                if (!active) {
                    throw new ChannelInactiveException();
                }
                String message = "No handler found for command type %s".formatted(command.getType());
                throw new ChannelNoReplyException(message);
            });
        }
    }

    @Override
    public void addCommandHandlers(final List<CommandHandler<? extends Command<?>, ? extends Reply<?>>> commandHandlers) {
        if (commandHandlers != null) {
            commandHandlers.forEach(commandHandler -> this.commandHandlers.putIfAbsent(commandHandler.handleType(), commandHandler));
        }
    }

    @Override
    public void addReplyHandlers(final List<ReplyHandler<? extends Command<?>, ? extends Command<?>, ? extends Reply<?>>> replyHandlers) {
        if (replyHandlers != null) {
            replyHandlers.forEach(replyHandler -> this.replyHandlers.putIfAbsent(replyHandler.handleType(), replyHandler));
        }
    }
}
