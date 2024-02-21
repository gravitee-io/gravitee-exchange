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

import io.gravitee.common.service.Service;
import io.gravitee.exchange.api.command.Batch;
import io.gravitee.exchange.api.command.Command;
import io.gravitee.exchange.api.command.Reply;
import io.gravitee.exchange.api.controller.metrics.ChannelMetric;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;

/**
 * @author Guillaume LAMIRAND (guillaume.lamirand at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface ExchangeController extends Service<ExchangeController> {
    /**
     * Return metric information about channels managed
     *
     * @return channel metrics.
     */
    Flowable<ChannelMetric> metrics();

    /**
     * Register a new {@code ControllerChannel} on this controller
     *
     * @param channel the new channel.
     */
    Completable register(final ControllerChannel channel);

    /**
     * Unregister a {@code ControllerChannel} on this controller
     *
     * @param channel the new channel.
     */
    Completable unregister(final ControllerChannel channel);

    /**
     * Send a {@code Command} to a specific target
     *
     * @param command the command to send
     * @param targetId the if of the target
     */
    Single<Reply<?>> sendCommand(final Command<?> command, final String targetId);
    /**
     * Execute a {@code Batch} of command
     *
     * @param batch the batch to execute
     */
    Single<Batch> executeBatch(final Batch batch);

    Single<Batch> watchBatch(String batchId);
}
