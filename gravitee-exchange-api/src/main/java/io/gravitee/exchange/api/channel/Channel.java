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
package io.gravitee.exchange.api.channel;

import io.gravitee.exchange.api.channel.exception.ChannelClosedException;
import io.gravitee.exchange.api.channel.exception.ChannelInitializationException;
import io.gravitee.exchange.api.channel.exception.ChannelNoReplyException;
import io.gravitee.exchange.api.channel.exception.ChannelTimeoutException;
import io.gravitee.exchange.api.channel.exception.ChannelUnknownCommandException;
import io.gravitee.exchange.api.command.Command;
import io.gravitee.exchange.api.command.CommandHandler;
import io.gravitee.exchange.api.command.Reply;
import io.gravitee.exchange.api.command.ReplyHandler;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import java.util.List;
import java.util.Map;

/**
 * @author Guillaume LAMIRAND (guillaume.lamirand at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface Channel {
    /**
     * Get the unique id of this channel.
     *
     * @return the id of the channel.
     */
    String id();

    /**
     * Get the target id the channel is opened for.
     *
     * @return target id.
     */
    String targetId();

    /**
     * Must be called to initialize channel connectivity
     *
     * @return returns a {@code Completable} instance that completes in case of success or a {@link ChannelInitializationException} is emitted
     */
    Completable initialize();

    /**
     * Must be called to properly close channel
     *
     * @return returns a {@code Completable} instance that completes in case of success or a {@link ChannelClosedException} is emitted
     */
    Completable close();

    /**
     * Send the actual commands to the current channel. In case of error, different exception could be returned:
     * <ul>
     * <li>if the channel is inactive {@link ChannelInitializationException} is signaled</li>
     * <li>if no reply has been received before the timeout is reached {@link ChannelTimeoutException} is signaled</li>
     * <li>if command is unknown by the receiver {@link ChannelUnknownCommandException} is signaled</li>
     * <li>if the receiver cannot reply {@link ChannelNoReplyException} is signaled</li>
     *</ul>
     * @return a {@code Single} with the {@code Reply} of the command
     */
    <C extends Command<?>, R extends Reply<?>> Single<R> send(final C command);

    /**
     * Add customs {@link CommandHandler} to this channel
     */
    void addCommandHandlers(final List<CommandHandler<? extends Command<?>, ? extends Reply<?>>> commandHandlers);
    /**
     * Add customs {@link Reply} to this channel
     */
    void addReplyHandlers(final List<ReplyHandler<? extends Command<?>, ? extends Command<?>, ? extends Reply<?>>> replyHandlers);
}
