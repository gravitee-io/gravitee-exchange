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
package io.gravitee.exchange.controller.core.channel;

import io.gravitee.exchange.api.channel.exception.ChannelNoReplyException;
import io.gravitee.exchange.api.channel.exception.ChannelTimeoutException;
import io.gravitee.exchange.api.command.Command;
import io.gravitee.exchange.api.command.CommandAdapter;
import io.gravitee.exchange.api.command.CommandHandler;
import io.gravitee.exchange.api.command.Reply;
import io.gravitee.exchange.api.command.ReplyAdapter;
import io.gravitee.exchange.api.controller.ControllerChannel;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.CompletableSource;
import io.reactivex.rxjava3.core.Single;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class SampleChannel implements ControllerChannel {

    private final Map<String, CommandHandler<? extends Command<?>, ? extends Reply<?>>> commandHandlers = new ConcurrentHashMap<>();
    private final Map<String, CommandAdapter<? extends Command<?>, ? extends Command<?>, ? extends Reply<?>>> commandAdapters =
        new ConcurrentHashMap<>();
    private final Map<String, ReplyAdapter<? extends Reply<?>, ? extends Reply<?>>> replyAdapters = new ConcurrentHashMap<>();
    private final String channelId;
    private final String targetId;
    private boolean active;
    private final boolean pendingCommands;
    private CompletableSource initializeCompletableSource = Completable.complete();
    private CompletableSource closeCompletableSource = Completable.complete();

    public SampleChannel(String channelId, String targetId) {
        this(channelId, targetId, true);
    }

    public SampleChannel(String channelId, String targetId, boolean active) {
        this(channelId, targetId, active, false);
    }

    public SampleChannel(String channelId, String targetId, boolean active, boolean pendingCommands) {
        this.channelId = channelId;
        this.targetId = targetId;
        this.active = active;
        this.pendingCommands = pendingCommands;
    }

    @Override
    public String id() {
        return channelId;
    }

    @Override
    public String targetId() {
        return targetId;
    }

    @Override
    public Completable initialize() {
        return Completable.wrap(this.initializeCompletableSource);
    }

    public void setInitialize(final CompletableSource completableSource) {
        this.initializeCompletableSource = completableSource;
    }

    @Override
    public Completable close() {
        return Completable.wrap(this.closeCompletableSource);
    }

    public void setClose(final CompletableSource completableSource) {
        this.closeCompletableSource = completableSource;
    }

    @Override
    public boolean isActive() {
        return this.active;
    }

    @Override
    public boolean hasPendingCommands() {
        return this.pendingCommands;
    }

    @Override
    public <C extends Command<?>, R extends Reply<?>> Single<R> send(final C command) {
        CommandHandler<?, ?> commandHandler = commandHandlers.get(command.getType());
        if (commandHandler != null) {
            return Single
                .defer(() -> {
                    CommandAdapter<C, Command<?>, Reply<?>> commandAdapter = (CommandAdapter<C, Command<?>, Reply<?>>) commandAdapters.get(
                        command.getType()
                    );
                    if (commandAdapter != null) {
                        return commandAdapter.adapt(targetId, command);
                    } else {
                        return Single.just(command);
                    }
                })
                .flatMap(single -> {
                    CommandHandler<Command<?>, Reply<?>> castHandler = (CommandHandler<Command<?>, Reply<?>>) commandHandler;
                    return castHandler.handle(command);
                })
                .timeout(
                    command.getReplyTimeoutMs(),
                    TimeUnit.MILLISECONDS,
                    Single.fromCallable(() -> {
                        throw new ChannelTimeoutException();
                    })
                )
                .onErrorResumeNext(throwable -> {
                    CommandAdapter<C, Command<?>, R> commandAdapter = (CommandAdapter<C, Command<?>, R>) commandAdapters.get(
                        command.getType()
                    );
                    if (commandAdapter != null) {
                        return commandAdapter.onError(command, throwable);
                    } else {
                        return Single.error(throwable);
                    }
                })
                .flatMap(reply -> {
                    ReplyAdapter<Reply<?>, R> replyAdapter = (ReplyAdapter<Reply<?>, R>) replyAdapters.get(reply.getType());
                    if (replyAdapter != null) {
                        return replyAdapter.adapt(targetId, reply);
                    } else {
                        return Single.just((R) reply);
                    }
                });
        } else {
            return Single.error(() -> {
                String message = "No handler found for command type %s".formatted(command.getType());
                throw new ChannelNoReplyException(message);
            });
        }
    }

    @Override
    public void addCommandHandlers(final List<CommandHandler<? extends Command<?>, ? extends Reply<?>>> commandHandlers) {
        if (commandHandlers != null) {
            commandHandlers.forEach(commandHandler -> this.commandHandlers.putIfAbsent(commandHandler.supportType(), commandHandler));
        }
    }

    @Override
    public void addCommandAdapters(
        final List<CommandAdapter<? extends Command<?>, ? extends Command<?>, ? extends Reply<?>>> commandAdapters
    ) {
        if (commandAdapters != null) {
            commandAdapters.forEach(commandDecorator -> this.commandAdapters.putIfAbsent(commandDecorator.supportType(), commandDecorator));
        }
    }

    @Override
    public void addReplyAdapters(final List<ReplyAdapter<? extends Reply<?>, ? extends Reply<?>>> replyAdapters) {
        if (replyAdapters != null) {
            replyAdapters.forEach(replyDecorator -> this.replyAdapters.putIfAbsent(replyDecorator.supportType(), replyDecorator));
        }
    }

    @Override
    public void enforceActiveStatus(final boolean isActive) {
        this.active = isActive;
    }
}
