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
import io.gravitee.exchange.api.configuration.IdentifyConfiguration;
import io.gravitee.exchange.api.controller.ControllerChannel;
import io.gravitee.exchange.api.controller.ExchangeController;
import io.gravitee.exchange.api.controller.listeners.TargetListener;
import io.gravitee.exchange.api.controller.metrics.BatchMetric;
import io.gravitee.exchange.api.controller.metrics.ChannelMetric;
import io.gravitee.exchange.api.controller.metrics.TargetBatchsMetric;
import io.gravitee.exchange.api.controller.metrics.TargetChannelsMetric;
import io.gravitee.exchange.controller.core.batch.BatchStore;
import io.gravitee.exchange.controller.core.batch.exception.BatchDisabledException;
import io.gravitee.exchange.controller.core.channel.primary.PrimaryChannelEvictedEvent;
import io.gravitee.exchange.controller.core.channel.primary.PrimaryChannelManager;
import io.gravitee.exchange.controller.core.cluster.ControllerClusterManager;
import io.gravitee.exchange.controller.core.management.ControllerTargetIdMetricsEndpoint;
import io.gravitee.exchange.controller.core.management.ControllerTargetsMetricsEndpoint;
import io.gravitee.exchange.controller.core.management.batch.ControllerBatchMetricsEndpoint;
import io.gravitee.exchange.controller.core.management.batch.ControllerBatchsMetricsEndpoint;
import io.gravitee.exchange.controller.core.management.batch.ControllerTargetIdBatchsMetricsEndpoint;
import io.gravitee.exchange.controller.core.management.channel.ControllerChannelMetricsEndpoint;
import io.gravitee.exchange.controller.core.management.channel.ControllerChannelsMetricsEndpoint;
import io.gravitee.exchange.controller.core.management.channel.ControllerTargetIdChannelsMetricsEndpoint;
import io.gravitee.node.api.cache.CacheConfiguration;
import io.gravitee.node.api.cache.CacheManager;
import io.gravitee.node.api.cluster.ClusterManager;
import io.gravitee.node.api.cluster.messaging.Topic;
import io.gravitee.node.management.http.endpoint.ManagementEndpoint;
import io.gravitee.node.management.http.endpoint.ManagementEndpointManager;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Guillaume LAMIRAND (guillaume.lamirand at graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
public class DefaultExchangeController extends AbstractService<ExchangeController> implements ExchangeController {

    public static final long DEFAULT_BATCH_RETRY_SCHEDULER_PERIOD_IN_SECONDS = 60;

    private final Map<String, List<BatchObserver>> keyBasedBatchObservers = new ConcurrentHashMap<>();
    private final Map<String, BatchObserver> idBasedBatchObservers = new ConcurrentHashMap<>();
    private final List<TargetListener> targetListeners = new ArrayList<>();

    protected final IdentifyConfiguration identifyConfiguration;
    protected final ClusterManager clusterManager;
    protected final CacheManager cacheManager;
    protected final ManagementEndpointManager managementEndpointManager;
    protected final ControllerClusterManager controllerClusterManager;
    private List<ManagementEndpoint> managementEndpoints;
    private BatchStore batchStore;
    private Topic<PrimaryChannelEvictedEvent> primaryChannelEvictedTopic;
    private String primaryChannelEvictedSubscriptionId;
    private Disposable batchSchedulerDisposable;
    private Long batchRetryDelayMs;

    public DefaultExchangeController(
        final IdentifyConfiguration identifyConfiguration,
        final ClusterManager clusterManager,
        final CacheManager cacheManager
    ) {
        this(identifyConfiguration, clusterManager, cacheManager, null);
    }

    public DefaultExchangeController(
        final IdentifyConfiguration identifyConfiguration,
        final ClusterManager clusterManager,
        final CacheManager cacheManager,
        final ManagementEndpointManager managementEndpointManager
    ) {
        this.identifyConfiguration = identifyConfiguration;
        this.clusterManager = clusterManager;
        this.cacheManager = cacheManager;
        this.managementEndpointManager = managementEndpointManager;
        this.controllerClusterManager = new ControllerClusterManager(identifyConfiguration, clusterManager, cacheManager);

        boolean managementEnabled = identifyConfiguration.getProperty("controller.management.enabled", Boolean.class, true);
        if (managementEnabled) {
            managementEndpoints = new ArrayList<>();
            managementEndpoints.add(new ControllerTargetsMetricsEndpoint(identifyConfiguration, this));
            managementEndpoints.add(new ControllerTargetIdMetricsEndpoint(identifyConfiguration, this));
            managementEndpoints.add(new ControllerChannelsMetricsEndpoint(identifyConfiguration, this));
            managementEndpoints.add(new ControllerChannelMetricsEndpoint(identifyConfiguration, this));
            managementEndpoints.add(new ControllerTargetIdChannelsMetricsEndpoint(identifyConfiguration, this));
            if (isBatchFeatureEnabled()) {
                managementEndpoints.add(new ControllerBatchMetricsEndpoint(identifyConfiguration, this));
                managementEndpoints.add(new ControllerBatchsMetricsEndpoint(identifyConfiguration, this));
                managementEndpoints.add(new ControllerTargetIdBatchsMetricsEndpoint(identifyConfiguration, this));
            }
        }
    }

    @Override
    protected void doStart() throws Exception {
        log.debug("[{}] Starting {} controller", this.identifyConfiguration.id(), this.getClass().getSimpleName());
        super.doStart();
        controllerClusterManager.start();
        startBatchFeature();

        primaryChannelEvictedTopic =
            clusterManager.topic(identifyConfiguration.identifyName(PrimaryChannelManager.PRIMARY_CHANNEL_EVICTED_EVENTS_TOPIC));
        primaryChannelEvictedSubscriptionId =
            primaryChannelEvictedTopic.addMessageListener(message -> {
                if (clusterManager.self().primary()) {
                    targetListeners.forEach(listener -> listener.onPrimaryChannelEvicted(message.content().targetId()));
                }
            });

        if (managementEndpointManager != null && managementEndpoints != null) {
            log.debug("[{}] Registering controller management endpoints", this.identifyConfiguration.id());
            managementEndpoints.forEach(managementEndpointManager::register);
        }
    }

    private void startBatchFeature() {
        boolean enabled = isBatchFeatureEnabled();
        if (enabled) {
            log.debug("[{}] Starting controller batch feature", this.identifyConfiguration.id());
            if (batchStore == null) {
                batchStore =
                    new BatchStore(
                        cacheManager.getOrCreateCache(
                            identifyConfiguration.identifyName("controller-batch-store"),
                            CacheConfiguration.builder().timeToLiveInMs(3600000).distributed(true).build()
                        )
                    );
            }
            resetPendingBatches();
            startBatchScheduler();
        }
    }

    private void startBatchScheduler() {
        batchRetryDelayMs =
            identifyConfiguration.getProperty(
                "controller.batch.retry_interval_ms",
                Long.class,
                DEFAULT_BATCH_RETRY_SCHEDULER_PERIOD_IN_SECONDS * 1000
            );
        log.debug("[{}] Starting batch scheduler with delay [{}ms]", this.identifyConfiguration.id(), batchRetryDelayMs);
        batchSchedulerDisposable =
            Flowable
                .<Long, Long>generate(
                    () -> 0L,
                    (state, emitter) -> {
                        emitter.onNext(state);
                        return state + 1;
                    }
                )
                .delay(batchRetryDelayMs, TimeUnit.MILLISECONDS)
                .rebatchRequests(1)
                .flatMapCompletable(interval -> {
                    if (clusterManager.self().primary()) {
                        log.debug("[{}] Executing Batch scheduled tasks", this.identifyConfiguration.id());
                        return this.batchStore.findByStatus(BatchStatus.PENDING)
                            .filter(batch -> batch.shouldRetryNow(batchRetryDelayMs))
                            .doOnNext(batch ->
                                log.info(
                                    "[{}] Retrying batch '{}' with key '{}' and target '{}'",
                                    this.identifyConfiguration.id(),
                                    batch.id(),
                                    batch.key(),
                                    batch.targetId()
                                )
                            )
                            .flatMapCompletable(batch ->
                                sendBatchCommands(batch)
                                    .onErrorComplete(throwable -> {
                                        log.error(
                                            "[{}] Unable to retry batch '{}' with key '{}' and target '{}'",
                                            this.identifyConfiguration.id(),
                                            batch.id(),
                                            batch.key(),
                                            batch.targetId(),
                                            throwable
                                        );
                                        return true;
                                    })
                                    .ignoreElement()
                            );
                    }
                    return Completable.complete();
                })
                .onErrorComplete(throwable -> {
                    log.debug("Unable to find batch to retry", throwable);
                    return true;
                })
                .subscribe();
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
        log.debug("[{}] Stopping {} controller", this.identifyConfiguration.id(), this.getClass().getSimpleName());
        super.doStop();

        if (primaryChannelEvictedTopic != null && primaryChannelEvictedSubscriptionId != null) {
            primaryChannelEvictedTopic.removeMessageListener(primaryChannelEvictedSubscriptionId);
        }

        controllerClusterManager.stop();
        stopBatchFeature();

        if (managementEndpointManager != null && managementEndpoints != null) {
            log.debug("[{}] Unregistering controller management endpoints", this.identifyConfiguration.id());
            managementEndpoints.forEach(managementEndpointManager::unregister);
        }
    }

    private void stopBatchFeature() {
        boolean enabled = isBatchFeatureEnabled();
        if (enabled) {
            log.debug("[{}] Stopping controller batch feature", this.identifyConfiguration.id());
            if (batchSchedulerDisposable != null) {
                batchSchedulerDisposable.dispose();
            }

            if (batchStore != null) {
                batchStore.clear();
            }
        }
    }

    @Override
    public ExchangeController addListener(TargetListener targetListener) {
        this.targetListeners.add(targetListener);
        return this;
    }

    @Override
    public ExchangeController removeListener(TargetListener targetListener) {
        this.targetListeners.remove(targetListener);
        return this;
    }

    @Override
    public Flowable<TargetBatchsMetric> batchsMetricsByTarget() {
        if (isBatchFeatureEnabled()) {
            return batchStore
                .getAll()
                .groupBy(Batch::targetId)
                .flatMapSingle(targetGroup ->
                    targetGroup
                        .map(BatchMetric::new)
                        .toList()
                        .map(batchMetrics -> TargetBatchsMetric.builder().id(targetGroup.getKey()).batchs(batchMetrics).build())
                );
        } else {
            return Flowable.empty();
        }
    }

    @Override
    public Flowable<BatchMetric> batchsMetricsForTarget(final String targetId) {
        if (isBatchFeatureEnabled()) {
            return batchStore.getAll().filter(batch -> targetId.equals(batch.targetId())).map(BatchMetric::new);
        } else {
            return Flowable.empty();
        }
    }

    @Override
    public Maybe<BatchMetric> batchMetric(final String id) {
        if (isBatchFeatureEnabled()) {
            return batchStore.getById(id).map(BatchMetric::new);
        } else {
            return Maybe.empty();
        }
    }

    @Override
    public Flowable<TargetChannelsMetric> channelsMetricsByTarget() {
        return controllerClusterManager.channelsMetricsByTarget();
    }

    @Override
    public Flowable<ChannelMetric> channelsMetricsForTarget(final String targetId) {
        return controllerClusterManager.channelsMetricsForTarget(targetId);
    }

    @Override
    public Maybe<ChannelMetric> channelMetric(final String id) {
        return controllerClusterManager.channelMetric(id);
    }

    @Override
    public Completable register(final ControllerChannel channel) {
        return controllerClusterManager
            .register(channel)
            .doOnComplete(() ->
                log.debug(
                    "[{}] Channel '{}' for target '{}' has been registered",
                    this.identifyConfiguration.id(),
                    channel.id(),
                    channel.targetId()
                )
            )
            .doOnError(throwable ->
                log.warn(
                    "[{}] Unable to register channel '{}' for target '{}'",
                    this.identifyConfiguration.id(),
                    channel.id(),
                    channel.targetId(),
                    throwable
                )
            );
    }

    @Override
    public Completable unregister(final ControllerChannel channel) {
        return controllerClusterManager
            .unregister(channel)
            .doOnComplete(() ->
                log.debug(
                    "[{}] Channel '{}' for target '{}' has been unregistered",
                    this.identifyConfiguration.id(),
                    channel.id(),
                    channel.targetId()
                )
            )
            .doOnError(throwable ->
                log.warn(
                    "[{}] Unable to unregister channel '{}' for target '{}'",
                    this.identifyConfiguration.id(),
                    channel.id(),
                    channel.targetId(),
                    throwable
                )
            );
    }

    @Override
    public Single<Reply<?>> sendCommand(final Command<?> command, final String targetId) {
        return controllerClusterManager
            .sendCommand(command, targetId)
            .doOnSuccess(reply ->
                log.debug(
                    "[{}] Command '{}' has been successfully sent to  target '{}'",
                    this.identifyConfiguration.id(),
                    command.getId(),
                    targetId
                )
            )
            .doOnError(throwable ->
                log.warn(
                    "[{}] Unable to send command '{}' to  target '{}'",
                    this.identifyConfiguration.id(),
                    command.getId(),
                    targetId,
                    throwable
                )
            );
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
                .doOnSuccess(b -> log.debug("[{}] Executing batch '{}' with key '{}'", this.identifyConfiguration.id(), b.id(), b.key()))
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
            .doOnSuccess(b ->
                log.debug(
                    "[{}] Batch '{}' for target '{}' and key '{}' in progress",
                    this.identifyConfiguration.id(),
                    b.id(),
                    b.targetId(),
                    b.key()
                )
            )
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
                        "[{}] Batch '{}' for target '{}' and key '{}' is scheduled for retry",
                        this.identifyConfiguration.id(),
                        b.id(),
                        b.targetId(),
                        b.key()
                    );
                    case SUCCEEDED -> {
                        log.info(
                            "[{}] Batch '{}' for target '{}' and key '{}' has succeed",
                            this.identifyConfiguration.id(),
                            b.id(),
                            b.targetId(),
                            b.key()
                        );
                        notifyObservers(b);
                    }
                    case ERROR -> {
                        log.info(
                            "[{}] Batch '{}' for target '{}' and key '{}' stopped in error",
                            this.identifyConfiguration.id(),
                            b.id(),
                            b.targetId(),
                            b.key()
                        );
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
                            "[{}] Unable to notify batch observer with batch '{}' for target '{}' and key '{}' has succeed",
                            this.identifyConfiguration.id(),
                            batch.id(),
                            batch.targetId(),
                            batch.key()
                        )
                    )
                    .doOnComplete(() ->
                        log.debug(
                            "[{}] Notify batch observer in success with batch '{}' for target '{}' and key '{}' has succeed",
                            this.identifyConfiguration.id(),
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
        return identifyConfiguration.getProperty("controller.batch.enabled", Boolean.class, true);
    }
}
