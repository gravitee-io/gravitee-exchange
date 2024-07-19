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

import io.gravitee.common.service.AbstractService;
import io.gravitee.exchange.api.command.Command;
import io.gravitee.exchange.api.command.CommandStatus;
import io.gravitee.exchange.api.command.Reply;
import io.gravitee.exchange.api.command.healtcheck.HealthCheckCommand;
import io.gravitee.exchange.api.command.healtcheck.HealthCheckCommandPayload;
import io.gravitee.exchange.api.command.healtcheck.HealthCheckReply;
import io.gravitee.exchange.api.command.healtcheck.HealthCheckReplyPayload;
import io.gravitee.exchange.api.command.primary.PrimaryCommand;
import io.gravitee.exchange.api.command.primary.PrimaryCommandPayload;
import io.gravitee.exchange.api.command.primary.PrimaryReply;
import io.gravitee.exchange.api.configuration.IdentifyConfiguration;
import io.gravitee.exchange.api.controller.ControllerChannel;
import io.gravitee.exchange.api.controller.metrics.ChannelMetric;
import io.gravitee.exchange.api.controller.metrics.TargetChannelsMetric;
import io.gravitee.exchange.controller.core.channel.exception.NoChannelFoundException;
import io.gravitee.exchange.controller.core.channel.primary.ChannelEvent;
import io.gravitee.exchange.controller.core.channel.primary.PrimaryChannelElectedEvent;
import io.gravitee.exchange.controller.core.channel.primary.PrimaryChannelManager;
import io.gravitee.node.api.cache.Cache;
import io.gravitee.node.api.cache.CacheConfiguration;
import io.gravitee.node.api.cache.CacheManager;
import io.gravitee.node.api.cluster.ClusterManager;
import io.gravitee.node.api.cluster.messaging.Topic;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.Disposable;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Guillaume LAMIRAND (guillaume.lamirand at graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
public class ChannelManager extends AbstractService<ChannelManager> {

    public static final String CHANNEL_EVENTS_TOPIC = "controller-channel-events";
    private static final String CHANNELS_METRICS_CACHE = "controller-channels-metrics-registry";
    private static final int HEALTH_CHECK_DELAY = 30000;
    private static final TimeUnit HEALTH_CHECK_DELAY_UNIT = TimeUnit.MILLISECONDS;
    private final LocalChannelRegistry localChannelRegistry = new LocalChannelRegistry();
    private final PrimaryChannelManager primaryChannelManager;
    private final IdentifyConfiguration identifyConfiguration;
    private final ClusterManager clusterManager;
    private final CacheManager cacheManager;
    private Disposable healthCheckDisposable;
    private Cache<String, ChannelMetric> channelMetricsRegistry;
    private Topic<ChannelEvent> channelEventTopic;
    private String channelEventSubscriptionId;
    private Topic<PrimaryChannelElectedEvent> primaryChannelElectedEventTopic;
    private String primaryChannelElectedSubscriptionId;

    public ChannelManager(
        final IdentifyConfiguration identifyConfiguration,
        final ClusterManager clusterManager,
        final CacheManager cacheManager
    ) {
        this.identifyConfiguration = identifyConfiguration;
        this.clusterManager = clusterManager;
        this.cacheManager = cacheManager;
        this.primaryChannelManager = new PrimaryChannelManager(identifyConfiguration, clusterManager, cacheManager);
    }

    @Override
    protected void doStart() throws Exception {
        log.debug("[{}] Starting channel manager", this.identifyConfiguration.id());
        super.doStart();
        channelMetricsRegistry =
            cacheManager.getOrCreateCache(
                identifyConfiguration.identifyName(CHANNELS_METRICS_CACHE),
                CacheConfiguration.builder().distributed(true).build()
            );

        primaryChannelManager.start();

        channelEventTopic = clusterManager.topic(identifyConfiguration.identifyName(CHANNEL_EVENTS_TOPIC));
        channelEventSubscriptionId =
            channelEventTopic.addMessageListener(message -> {
                ChannelEvent channelEvent = message.content();
                if (channelEvent.targetId() == null) {
                    log.warn(
                        "[{}] ChannelEvent received for channel '{}' without any target",
                        this.identifyConfiguration.id(),
                        channelEvent.channelId()
                    );
                } else {
                    log.debug(
                        "[{}] ChannelEvent received for channel '{}' on target '{}'",
                        this.identifyConfiguration.id(),
                        channelEvent.channelId(),
                        channelEvent.targetId()
                    );
                    if (clusterManager.self().primary()) {
                        log.debug(
                            "[{}] Handling ChannelEvent for channel '{}' on target '{}'",
                            this.identifyConfiguration.id(),
                            channelEvent.channelId(),
                            channelEvent.targetId()
                        );
                        handleChannelEvent(channelEvent);
                    }
                }
            });

        primaryChannelElectedEventTopic =
            clusterManager.topic(identifyConfiguration.identifyName(PrimaryChannelManager.PRIMARY_CHANNEL_ELECTED_EVENTS_TOPIC));
        primaryChannelElectedSubscriptionId =
            primaryChannelElectedEventTopic.addMessageListener(message -> handlePrimaryChannelElectedEvent(message.content()));

        healthCheckDisposable =
            Flowable
                .<Long, Long>generate(
                    () -> 0L,
                    (state, emitter) -> {
                        emitter.onNext(state);
                        return state + 1;
                    }
                )
                .delay(
                    identifyConfiguration.getProperty("controller.channel.healthcheck.delay", Integer.class, HEALTH_CHECK_DELAY),
                    HEALTH_CHECK_DELAY_UNIT
                )
                .rebatchRequests(1)
                .doOnNext(aLong -> log.debug("[{}] Sending healthcheck command to all registered channels", this.identifyConfiguration.id())
                )
                .concatMapCompletable(interval -> sendHealthCheckCommand())
                .onErrorComplete()
                .subscribe();
    }

    private void handleChannelEvent(final ChannelEvent channelEvent) {
        if (channelEvent.closed()) {
            channelMetricsRegistry.evict(channelEvent.channelId());
        } else {
            channelMetricsRegistry.put(
                channelEvent.channelId(),
                ChannelMetric.builder().id(channelEvent.channelId()).targetId(channelEvent.targetId()).active(channelEvent.active()).build()
            );
        }
        primaryChannelManager.handleChannelCandidate(channelEvent);
    }

    private void handlePrimaryChannelElectedEvent(final PrimaryChannelElectedEvent event) {
        Completable
            .defer(() -> {
                String channelId = event.channelId();
                String targetId = event.targetId();
                log.debug(
                    "[{}] Handling primary channel elected event for channel '{}' on target '{}'.",
                    this.identifyConfiguration.id(),
                    channelId,
                    targetId
                );

                return Maybe
                    .fromOptional(localChannelRegistry.getById(channelId))
                    .switchIfEmpty(
                        Maybe.fromRunnable(() ->
                            log.debug(
                                "[{}] Primary elected channel '{}' on target '{}' was not found from the local registry, ignore it.",
                                this.identifyConfiguration.id(),
                                channelId,
                                targetId
                            )
                        )
                    )
                    .flatMapCompletable(channel -> sendPrimaryCommand(channel, true))
                    .andThen(
                        Flowable
                            .fromIterable(localChannelRegistry.getAllByTargetId(targetId))
                            .filter(controllerChannel -> !controllerChannel.id().equals(channelId))
                            .flatMapCompletable(controllerChannel -> sendPrimaryCommand(controllerChannel, false))
                    );
            })
            .subscribe(
                () -> log.debug("[{}] Primary channel elected event properly handled", this.identifyConfiguration.id()),
                throwable ->
                    log.error(
                        "[{}] Unable to send primary commands to local registered channels",
                        this.identifyConfiguration.id(),
                        throwable
                    )
            );
    }

    private Completable sendPrimaryCommand(final ControllerChannel channel, final boolean isPrimary) {
        String channelId = channel.id();
        String targetId = channel.targetId();
        log.debug(
            "[{}] Sending primary command to channel '{}' on target '{}' with primary '{}'",
            this.identifyConfiguration.id(),
            channelId,
            targetId,
            isPrimary
        );
        channelMetricsRegistry.put(
            channel.id(),
            ChannelMetric.builder().id(channel.id()).targetId(channel.targetId()).active(channel.isActive()).primary(isPrimary).build()
        );

        return channel
            .<PrimaryCommand, PrimaryReply>send(new PrimaryCommand(new PrimaryCommandPayload(isPrimary)))
            .doOnSuccess(primaryReply -> {
                log.debug("[{}] Primary command successfully sent to channel '{}'", this.identifyConfiguration.id(), channelId);
                if (primaryReply.getCommandStatus() == CommandStatus.SUCCEEDED) {
                    log.debug("[{}] Channel '{}' successfully replied from primary command", this.identifyConfiguration.id(), channelId);
                } else if (primaryReply.getCommandStatus() == CommandStatus.ERROR) {
                    log.warn("[{}] Channel '{}' replied in error from primary command", this.identifyConfiguration.id(), channelId);
                }
            })
            .doOnError(throwable ->
                log.warn("[{}] Unable to send primary command to channel '{}'", this.identifyConfiguration.id(), channelId, throwable)
            )
            .ignoreElement();
    }

    private Completable sendHealthCheckCommand() {
        return Flowable
            .fromIterable(localChannelRegistry.getAll())
            .filter(ControllerChannel::isActive)
            .flatMapCompletable(controllerChannel ->
                controllerChannel
                    .send(new HealthCheckCommand(new HealthCheckCommandPayload()))
                    .cast(HealthCheckReply.class)
                    .doOnSuccess(reply -> {
                        log.debug(
                            "[{}] Health check command successfully sent for channel '{}' on target '{}'",
                            this.identifyConfiguration.id(),
                            controllerChannel.id(),
                            controllerChannel.targetId()
                        );
                        HealthCheckReplyPayload payload = reply.getPayload();
                        controllerChannel.enforceActiveStatus(payload.healthy());
                        publishChannelEvent(controllerChannel, reply.getPayload().healthy(), false);
                    })
                    .ignoreElement()
                    .onErrorResumeNext(throwable -> {
                        log.debug(
                            "[{}] Unable to send health check command for channel '{}' on target '{}'",
                            this.identifyConfiguration.id(),
                            controllerChannel.id(),
                            controllerChannel.targetId()
                        );
                        controllerChannel.enforceActiveStatus(false);
                        publishChannelEvent(controllerChannel, false, false);
                        return Completable.complete();
                    })
            );
    }

    @Override
    protected void doStop() throws Exception {
        log.debug("[{}] Stopping channel manager", this.identifyConfiguration.id());

        // Unregister all local channel
        Flowable
            .fromIterable(this.localChannelRegistry.getAll())
            .flatMapCompletable(controllerChannel -> unregister(controllerChannel).onErrorComplete())
            .doOnComplete(() -> log.debug("[{}] All local channel unregistered.", this.identifyConfiguration.id()))
            .blockingAwait();

        primaryChannelManager.stop();
        if (healthCheckDisposable != null) {
            healthCheckDisposable.dispose();
        }
        if (primaryChannelElectedEventTopic != null && primaryChannelElectedSubscriptionId != null) {
            primaryChannelElectedEventTopic.removeMessageListener(primaryChannelElectedSubscriptionId);
        }
        if (channelEventTopic != null && channelEventSubscriptionId != null) {
            channelEventTopic.removeMessageListener(channelEventSubscriptionId);
        }
        super.doStop();
    }

    public Flowable<TargetChannelsMetric> channelsMetricsByTarget() {
        return this.channelMetricsRegistry.rxValues()
            .groupBy(ChannelMetric::targetId)
            .flatMapSingle(group -> {
                String targetId = group.getKey();
                return group.toList().map(channelMetrics -> TargetChannelsMetric.builder().id(targetId).channels(channelMetrics).build());
            });
    }

    public Flowable<ChannelMetric> channelsMetricsForTarget(final String targetId) {
        return this.channelMetricsRegistry.rxValues().filter(channelMetric -> channelMetric.targetId().equals(targetId));
    }

    public Maybe<ChannelMetric> channelMetric(final String channelId) {
        return this.channelMetricsRegistry.rxGet(channelId);
    }

    public Optional<ControllerChannel> getChannelById(final String id) {
        return localChannelRegistry.getById(id);
    }

    public List<ControllerChannel> getChannels() {
        return localChannelRegistry.getAll();
    }

    public Optional<ControllerChannel> getOneActiveChannelByTargetId(final String targetId) {
        return localChannelRegistry.getAllByTargetId(targetId).stream().filter(ControllerChannel::isActive).findFirst();
    }

    public Completable register(ControllerChannel controllerChannel) {
        return Completable
            .fromRunnable(() -> localChannelRegistry.add(controllerChannel))
            .andThen(controllerChannel.initialize())
            .doOnComplete(() -> {
                log.debug(
                    "[{}] Channel '{}' successfully register for target '{}'",
                    this.identifyConfiguration.id(),
                    controllerChannel.id(),
                    controllerChannel.targetId()
                );
                publishChannelEvent(controllerChannel, true, false);
            })
            .onErrorResumeNext(throwable -> {
                log.warn(
                    "[{}] Unable to register channel '{}' for target '{}'",
                    this.identifyConfiguration.id(),
                    controllerChannel.id(),
                    controllerChannel.targetId(),
                    throwable
                );
                return unregister(controllerChannel);
            });
    }

    public Completable unregister(ControllerChannel controllerChannel) {
        return Completable
            .fromRunnable(() -> localChannelRegistry.remove(controllerChannel))
            .andThen(controllerChannel.close())
            .doOnComplete(() ->
                log.debug(
                    "[{}] Channel '{}' successfully unregister for target '{}'",
                    this.identifyConfiguration.id(),
                    controllerChannel.id(),
                    controllerChannel.targetId()
                )
            )
            .doOnError(throwable ->
                log.warn(
                    "[{}] Unable to unregister channel '{}' for target '{}'",
                    this.identifyConfiguration.id(),
                    controllerChannel.id(),
                    controllerChannel.targetId(),
                    throwable
                )
            )
            .doFinally(() -> publishChannelEvent(controllerChannel, false, true));
    }

    public <C extends Command<?>, R extends Reply<?>> Single<R> send(C command, String targetId) {
        return Maybe
            .defer(() -> Maybe.fromOptional(getOneActiveChannelByTargetId(targetId)))
            .doOnComplete(() ->
                log.debug(
                    "[{}] No channel found for target '{}' to handle command '{}'",
                    this.identifyConfiguration.id(),
                    targetId,
                    command.getType()
                )
            )
            .switchIfEmpty(Single.error(new NoChannelFoundException()))
            .<R>flatMap(controllerChannel -> {
                log.debug(
                    "[{}] Sending command '{}' with id '{}' to channel '{}'",
                    this.identifyConfiguration.id(),
                    command.getType(),
                    command.getId(),
                    controllerChannel
                );
                return controllerChannel.send(command);
            })
            .doOnSuccess(reply ->
                log.debug(
                    "[{}] Command '{}' with id  '{}' successfully sent",
                    this.identifyConfiguration.id(),
                    command.getType(),
                    command.getId()
                )
            )
            .doOnError(throwable ->
                log.warn(
                    "[{}] Unable to send command or receive reply for command '{}' with id '{}'",
                    this.identifyConfiguration.id(),
                    command.getType(),
                    command.getId(),
                    throwable
                )
            );
    }

    private void publishChannelEvent(final ControllerChannel controllerChannel, final boolean active, final boolean closed) {
        channelEventTopic.publish(
            ChannelEvent
                .builder()
                .channelId(controllerChannel.id())
                .targetId(controllerChannel.targetId())
                .active(active)
                .closed(closed)
                .build()
        );
    }
}
