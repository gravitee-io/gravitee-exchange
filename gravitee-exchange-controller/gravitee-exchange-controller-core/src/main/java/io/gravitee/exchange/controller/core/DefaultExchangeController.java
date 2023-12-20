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
package io.gravitee.exchange.controller.core;

import io.gravitee.common.service.AbstractService;
import io.gravitee.exchange.api.command.Batch;
import io.gravitee.exchange.api.command.BatchCommand;
import io.gravitee.exchange.api.command.BatchStatus;
import io.gravitee.exchange.api.command.Command;
import io.gravitee.exchange.api.command.CommandStatus;
import io.gravitee.exchange.api.command.Reply;
import io.gravitee.exchange.api.configuration.PrefixConfiguration;
import io.gravitee.exchange.api.controller.ControllerChannel;
import io.gravitee.exchange.api.controller.ExchangeController;
import io.gravitee.exchange.controller.core.batch.BatchStore;
import io.gravitee.exchange.controller.core.batch.exception.BatchDisabledException;
import io.gravitee.exchange.controller.core.cluster.ControllerClusterManager;
import io.gravitee.node.api.cache.CacheConfiguration;
import io.gravitee.node.api.cache.CacheManager;
import io.gravitee.node.api.cluster.ClusterManager;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;

/**
 * @author Guillaume LAMIRAND (guillaume.lamirand at graviteesource.com)
 * @author GraviteeSource Team
 */
@RequiredArgsConstructor
@Slf4j
public class DefaultExchangeController extends AbstractService<ExchangeController> implements ExchangeController {

    protected final PrefixConfiguration prefixConfiguration;
    protected final ControllerClusterManager controllerClusterManager;
    protected final ClusterManager clusterManager;
    protected final CacheManager cacheManager;
    private BatchStore batchStore;
    private ScheduledFuture<?> scheduledFuture;

    @Override
    protected void doStart() throws Exception {
        log.debug("Starting %s controller".formatted(this.getClass().getSimpleName()));
        super.doStart();
        controllerClusterManager.start();
        startBatchFeature();
    }

    private void startBatchFeature() {
        boolean enabled = isBatchFeatureEnabled();
        if (enabled) {
            if (batchStore == null) {
                batchStore =
                    new BatchStore(
                        cacheManager.getOrCreateCache(
                            "controller-exchange-batch-store",
                            CacheConfiguration.builder().timeToLiveInMs(3600000).distributed(true).build()
                        )
                    );
            }
            resetPendingBatches();
            startBatchScheduler();
        }
    }

    private void startBatchScheduler() {
        log.debug("Starting batch scheduler");
        ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.setThreadNamePrefix("controller-exchange-batch-scheduler-");
        taskScheduler.initialize();
        scheduledFuture =
            taskScheduler.schedule(
                () -> {
                    if (clusterManager.self().primary()) {
                        log.debug("Executing Batch scheduled tasks");
                        this.batchStore.findByStatus(BatchStatus.PENDING)
                            .doOnNext(batch -> log.debug("Retry Batch {} for target id {}", batch.id(), batch.targetId()))
                            .flatMapSingle(this::sendBatchCommands)
                            .ignoreElements()
                            .blockingAwait();
                        log.debug("Batch scheduled tasks executed");
                    }
                },
                new CronTrigger(prefixConfiguration.getProperty("exchange.controller.batch.cron", String.class, "*/60 * * * * *"))
            );
    }

    private void resetPendingBatches() {
        if (clusterManager.self().primary()) {
            this.batchStore.findByStatus(BatchStatus.IN_PROGRESS)
                .flatMapSingle(batch -> updateBatch(batch.reset()))
                .ignoreElements()
                .blockingAwait();
        }
    }

    @Override
    protected void doStop() throws Exception {
        log.debug("Stopping %s controller".formatted(this.getClass().getSimpleName()));
        super.doStop();
        controllerClusterManager.stop();
        stopBatchFeature();
    }

    private void stopBatchFeature() {
        boolean enabled = isBatchFeatureEnabled();
        if (enabled) {
            if (scheduledFuture != null) {
                scheduledFuture.cancel(true);
            }
            if (batchStore != null) {
                batchStore.clear();
            }
        }
    }

    @Override
    public Completable register(final ControllerChannel channel) {
        return controllerClusterManager.register(channel);
    }

    @Override
    public Completable unregister(final ControllerChannel channel) {
        return controllerClusterManager.unregister(channel);
    }

    @Override
    public Single<Reply<?>> sendCommand(final Command<?> command, final String targetId) {
        return controllerClusterManager.sendCommand(command, targetId);
    }

    @Override
    public Single<Batch> executeBatch(final Batch batch) {
        if (isBatchFeatureEnabled()) {
            return this.batchStore.add(batch).flatMap(this::sendBatchCommands);
        } else {
            return Single.error(new BatchDisabledException());
        }
    }

    @Override
    public Single<Batch> watchBatch(final String batchId) {
        if (isBatchFeatureEnabled()) {
            return this.batchStore.getById(batchId)
                .filter(batch -> batch.status() == BatchStatus.SUCCEEDED || batch.status() == BatchStatus.ERROR)
                .toSingle()
                .retry((integer, throwable) -> throwable instanceof NoSuchElementException);
        } else {
            return Single.error(new BatchDisabledException());
        }
    }

    private Single<Batch> sendBatchCommands(final Batch batch) {
        return this.updateBatch(batch.start())
            .filter(a -> a.status().equals(BatchStatus.IN_PROGRESS))
            .flatMapSingle(updateBatch -> {
                List<BatchCommand> commands = updateBatch
                    .batchCommands()
                    .stream()
                    .filter(command -> !Objects.equals(CommandStatus.SUCCEEDED, command.status()))
                    .toList();
                return sendCommands(updateBatch, commands);
            })
            .defaultIfEmpty(batch);
    }

    private Single<Batch> sendCommands(final Batch batch, final List<BatchCommand> batchCommands) {
        if (batchCommands.isEmpty()) {
            return Single.just(batch);
        }

        return Flowable
            .fromIterable(batchCommands)
            .concatMapSingle(batchCommand ->
                Single
                    .just(batch.markCommandInProgress(batchCommand.command().getId()))
                    .flatMap(this::updateBatch)
                    .flatMap(updatedBatch ->
                        sendCommand(batchCommand.command(), updatedBatch.targetId())
                            .map(reply -> updatedBatch.setCommandReply(batchCommand.command().getId(), reply))
                            .onErrorReturn(throwable ->
                                updatedBatch.markCommandInError(batchCommand.command().getId(), throwable.getMessage())
                            )
                    )
                    .flatMap(this::updateBatch)
            )
            .takeWhile(updatedBatch -> updatedBatch.status() == BatchStatus.IN_PROGRESS)
            .last(batch);
    }

    private Single<Batch> updateBatch(final Batch batch) {
        return this.batchStore.update(batch);
    }

    private boolean isBatchFeatureEnabled() {
        return prefixConfiguration.getProperty("exchange.controller.batch.enabled", Boolean.class, true);
    }
}
