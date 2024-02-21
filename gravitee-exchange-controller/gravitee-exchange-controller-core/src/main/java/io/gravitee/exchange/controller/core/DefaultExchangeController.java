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
import io.gravitee.exchange.api.batch.Batch;
import io.gravitee.exchange.api.batch.BatchCommand;
import io.gravitee.exchange.api.batch.BatchObserver;
import io.gravitee.exchange.api.batch.BatchStatus;
import io.gravitee.exchange.api.batch.KeyBatchObserver;
import io.gravitee.exchange.api.command.Command;
import io.gravitee.exchange.api.command.CommandStatus;
import io.gravitee.exchange.api.command.Reply;
import io.gravitee.exchange.api.configuration.PrefixConfiguration;
import io.gravitee.exchange.api.controller.ControllerChannel;
import io.gravitee.exchange.api.controller.ExchangeController;
import io.gravitee.exchange.api.controller.metrics.ChannelMetric;
import io.gravitee.exchange.controller.core.batch.BatchStore;
import io.gravitee.exchange.controller.core.batch.exception.BatchDisabledException;
import io.gravitee.exchange.controller.core.cluster.ControllerClusterManager;
import io.gravitee.node.api.cache.CacheConfiguration;
import io.gravitee.node.api.cache.CacheManager;
import io.gravitee.node.api.cluster.ClusterManager;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
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

    private final Map<String, List<BatchObserver>> keyBasedBatchObservers = new ConcurrentHashMap<>();
    private final Map<String, BatchObserver> idBasedBatchObservers = new ConcurrentHashMap<>();
    protected final PrefixConfiguration prefixConfiguration;
    protected final ControllerClusterManager controllerClusterManager;
    protected final ClusterManager clusterManager;
    protected final CacheManager cacheManager;
    private BatchStore batchStore;
    private ScheduledFuture<?> scheduledFuture;

    public DefaultExchangeController(
        final PrefixConfiguration prefixConfiguration,
        final ClusterManager clusterManager,
        final CacheManager cacheManager
    ) {
        this.prefixConfiguration = prefixConfiguration;
        this.clusterManager = clusterManager;
        this.cacheManager = cacheManager;
        this.controllerClusterManager = new ControllerClusterManager(clusterManager, cacheManager);
    }

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
                            .doOnNext(batch ->
                                log.info("Retrying batch '{}' with key '{}' and target id '{}'", batch.id(), batch.key(), batch.targetId())
                            )
                            .flatMapSingle(this::sendBatchCommands)
                            .ignoreElements()
                            .blockingAwait();
                        log.debug("Batch scheduled tasks executed");
                    }
                },
                new CronTrigger(prefixConfiguration.getProperty("controller.batch.cron", String.class, "*/60 * * * * *"))
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
    public Flowable<ChannelMetric> metrics() {
        return controllerClusterManager.channelMetrics();
    }

    @Override
    public Completable register(final ControllerChannel channel) {
        return controllerClusterManager
            .register(channel)
            .doOnComplete(() -> log.debug("Channel '{}' for target '{}' has been registered", channel.id(), channel.targetId()))
            .doOnError(throwable -> log.warn("Unable to register channel '{}' for target '{}'", channel.id(), channel.targetId(), throwable)
            );
    }

    @Override
    public Completable unregister(final ControllerChannel channel) {
        return controllerClusterManager
            .unregister(channel)
            .doOnComplete(() -> log.debug("Channel '{}' for target '{}' has been unregistered", channel.id(), channel.targetId()))
            .doOnError(throwable ->
                log.warn("Unable to unregister channel '{}' for target '{}'", channel.id(), channel.targetId(), throwable)
            );
    }

    @Override
    public Single<Reply<?>> sendCommand(final Command<?> command, final String targetId) {
        return controllerClusterManager
            .sendCommand(command, targetId)
            .doOnSuccess(reply -> log.debug("Command '{}' has been successfully sent to  target '{}'", command.getId(), targetId))
            .doOnError(throwable -> log.warn("Unable to send command '{}' to  target '{}'", command.getId(), targetId, throwable));
    }

    @Override
    public void addKeyBasedBatchObserver(final KeyBatchObserver keyBasedObserver) {
        this.keyBasedBatchObservers.compute(
                keyBasedObserver.batchKey(),
                (k, v) -> {
                    if (v == null) {
                        v = new ArrayList<>();
                    }
                    v.add(keyBasedObserver);
                    return v;
                }
            );
    }

    @Override
    public void removeKeyBasedBatchObserver(final KeyBatchObserver keyBasedObserver) {
        this.keyBasedBatchObservers.computeIfPresent(
                keyBasedObserver.batchKey(),
                (k, v) -> {
                    v.remove(keyBasedObserver);
                    return v;
                }
            );
    }

    @Override
    public Single<Batch> executeBatch(final Batch batch) {
        if (isBatchFeatureEnabled()) {
            return this.batchStore.add(batch)
                .doOnSuccess(b -> log.debug("Executing batch '%s' with key '%s'".formatted(b.id(), b.key())))
                .flatMap(this::sendBatchCommands);
        } else {
            return Single.error(new BatchDisabledException());
        }
    }

    @Override
    public Completable executeBatch(final Batch batch, final BatchObserver batchObserver) {
        return Completable
            .fromRunnable(() -> this.idBasedBatchObservers.put(batch.id(), batchObserver))
            .andThen(executeBatch(batch).ignoreElement())
            .doOnError(throwable -> this.idBasedBatchObservers.remove(batch.id()));
    }

    private Single<Batch> sendBatchCommands(final Batch batch) {
        return this.updateBatch(batch.start())
            .filter(a -> a.status().equals(BatchStatus.IN_PROGRESS))
            .doOnSuccess(b -> log.debug("Batch '%s' for target '%s' and key '%s' in progress".formatted(b.id(), b.targetId(), b.key())))
            .flatMapSingle(updateBatch -> {
                List<BatchCommand> commands = updateBatch
                    .batchCommands()
                    .stream()
                    .filter(command -> !Objects.equals(CommandStatus.SUCCEEDED, command.status()))
                    .toList();
                return sendCommands(updateBatch, commands);
            })
            .doOnSuccess(b -> {
                switch (b.status()) {
                    case PENDING -> log.info(
                        "Batch '%s' for target id '%s' and key '%s' is scheduled for retry".formatted(b.id(), b.targetId(), b.key())
                    );
                    case SUCCEEDED -> {
                        log.info("Batch '%s' for target id '%s' and key '%s' has succeed".formatted(b.id(), b.targetId(), b.key()));
                        notifyObservers(b);
                    }
                    case ERROR -> {
                        log.info("Batch '%s' for target id '%s' and key '%s' stopped in error".formatted(b.id(), b.targetId(), b.key()));
                        notifyObservers(b);
                    }
                }
            })
            .defaultIfEmpty(batch);
    }

    private void notifyObservers(final Batch batch) {
        List<BatchObserver> batchObservers = new ArrayList<>();
        if (idBasedBatchObservers.containsKey(batch.id())) {
            batchObservers.add(idBasedBatchObservers.get(batch.id()));
        }
        if (keyBasedBatchObservers.containsKey(batch.key())) {
            batchObservers.addAll(keyBasedBatchObservers.get(batch.key()));
        }
        Flowable
            .fromIterable(batchObservers)
            .flatMapCompletable(batchObserver ->
                batchObserver
                    .notify(batch)
                    .subscribeOn(Schedulers.computation())
                    .doOnError(throwable ->
                        log.warn(
                            "Unable to notify batch observer with batch '{}' for target id '{}' and key '{}' has succeed",
                            batch.id(),
                            batch.targetId(),
                            batch.key()
                        )
                    )
                    .doOnComplete(() ->
                        log.debug(
                            "Notify batch observer in success with batch '{}' for target id '{}' and key '{}' has succeed",
                            batch.id(),
                            batch.targetId(),
                            batch.key()
                        )
                    )
                    .onErrorComplete()
            )
            .doOnComplete(() -> this.idBasedBatchObservers.remove(batch.id()))
            .subscribe();
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
