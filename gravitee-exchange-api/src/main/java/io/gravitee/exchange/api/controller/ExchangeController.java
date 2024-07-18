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
import io.gravitee.exchange.api.batch.Batch;
import io.gravitee.exchange.api.batch.BatchObserver;
import io.gravitee.exchange.api.batch.KeyBatchObserver;
import io.gravitee.exchange.api.command.Command;
import io.gravitee.exchange.api.command.Reply;
import io.gravitee.exchange.api.controller.listeners.TargetListener;
import io.gravitee.exchange.api.controller.metrics.BatchMetric;
import io.gravitee.exchange.api.controller.metrics.ChannelMetric;
import io.gravitee.exchange.api.controller.metrics.TargetBatchsMetric;
import io.gravitee.exchange.api.controller.metrics.TargetChannelsMetric;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

/**
 * @author Guillaume LAMIRAND (guillaume.lamirand at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface ExchangeController extends Service<ExchangeController> {
    /**
     * Add a {@link TargetListener} that will be notified
     * @param targetListener {@link TargetListener} that will be notified
     */
    ExchangeController addListener(TargetListener targetListener);

    /**
     * Remove a {@link TargetListener}
     * @param targetListener {@link TargetListener} to remove.
     */
    ExchangeController removeListener(TargetListener targetListener);

    /**
     * Return batchs metrics grouped by target
     *
     * @return batchs metrics by target
     */
    Flowable<TargetBatchsMetric> batchsMetricsByTarget();

    /**
     * Return all batchs metrics for the given target
     *
     * @param targetId the target id to retrieve batchs metrics
     * @return batchs metrics.
     */
    Flowable<BatchMetric> batchsMetricsForTarget(String targetId);

    /**
     * Return batch metric for the given id
     *
     * @param batchId the batch id to retrieve
     * @return batch metric.
     */
    Maybe<BatchMetric> batchMetric(String batchId);

    /**
     * Return channels metrics grouped by target
     *
     * @return channel metrics by target
     */
    Flowable<TargetChannelsMetric> channelsMetricsByTarget();

    /**
     * Return channel metrics for the given target
     *
     * @param targetId the target id to retrieve channel metrics
     * @return channel metrics.
     */
    Flowable<ChannelMetric> channelsMetricsForTarget(String targetId);

    /**
     * Return channel metric for the given id
     *
     * @param channelId the channel id to retrieve
     * @return channel metric.
     */
    Maybe<ChannelMetric> channelMetric(String channelId);

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
     * Execute a {@code Batch} of command.
     * If any key based {@link KeyBatchObserver} has been registered, they will be notified when the batch finishes
     * in {@link io.gravitee.exchange.api.batch.BatchStatus#SUCCEEDED}
     * or {@link io.gravitee.exchange.api.batch.BatchStatus#ERROR}.
     *
     * @param batch the batch to execute
     */
    Single<Batch> executeBatch(final Batch batch);

    /**
     * As {@link ExchangeController#executeBatch(Batch)} but with an {@link BatchObserver} which will be notified when
     * the batch finished in {@link io.gravitee.exchange.api.batch.BatchStatus#SUCCEEDED}
     * or {@link io.gravitee.exchange.api.batch.BatchStatus#ERROR}
     *
     * @param batch the batch to execute
     * @param batchObserver the given will be executed when the given batch finished
     */
    Completable executeBatch(final Batch batch, final BatchObserver batchObserver);

    /**
     * Add a key based {@link BatchObserver} which will be called when any batches with the according key finish
     *
     * @param keyBasedObserver the given will be executed when any batch with the according key finish
     */
    void addKeyBasedBatchObserver(final KeyBatchObserver keyBasedObserver);

    /**
     * Remove a key based {@link BatchObserver} which will be called when any batches with the according key finish
     *
     * @param keyBasedObserver the observer to unregister
     */
    void removeKeyBasedBatchObserver(final KeyBatchObserver keyBasedObserver);
}
