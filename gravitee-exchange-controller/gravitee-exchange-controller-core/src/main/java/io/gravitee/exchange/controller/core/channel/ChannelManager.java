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
import io.gravitee.exchange.api.controller.metrics.TargetMetric;
import io.gravitee.exchange.controller.core.channel.exception.NoChannelFoundException;
import io.gravitee.exchange.controller.core.channel.primary.PrimaryChannelElectedEvent;
import io.gravitee.exchange.controller.core.channel.primary.PrimaryChannelManager;
import io.gravitee.node.api.cache.CacheManager;
import io.gravitee.node.api.cluster.ClusterManager;
import io.gravitee.node.api.cluster.messaging.Topic;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.Disposable;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Guillaume LAMIRAND (guillaume.lamirand at graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
public class ChannelManager extends AbstractService<ChannelManager> {

    private static final int HEALTH_CHECK_DELAY = 30000;
    private static final TimeUnit HEALTH_CHECK_DELAY_UNIT = TimeUnit.MILLISECONDS;
    private final LocalChannelRegistry localChannelRegistry = new LocalChannelRegistry();
    private final PrimaryChannelManager primaryChannelManager;
    private final IdentifyConfiguration identifyConfiguration;
    private final ClusterManager clusterManager;
    private Disposable healthCheckDisposable;
    private Topic<PrimaryChannelElectedEvent> primaryChannelElectedEventTopic;
    private String primaryChannelElectedSubscriptionId;

    public ChannelManager(
        final IdentifyConfiguration identifyConfiguration,
        final ClusterManager clusterManager,
        final CacheManager cacheManager
    ) {
        this.identifyConfiguration = identifyConfiguration;
        this.clusterManager = clusterManager;
        this.primaryChannelManager = new PrimaryChannelManager(identifyConfiguration, clusterManager, cacheManager);
    }

    @Override
    protected void doStart() throws Exception {
        log.debug("[{}] Starting channel manager", this.identifyConfiguration.id());
        super.doStart();
        primaryChannelManager.start();
        primaryChannelElectedEventTopic =
            clusterManager.topic(identifyConfiguration.identifyName(PrimaryChannelManager.PRIMARY_CHANNEL_EVENTS_ELECTED_TOPIC));
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
                        signalChannelAlive(controllerChannel, reply.getPayload().healthy());
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
                        signalChannelAlive(controllerChannel, false);
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
        super.doStop();
    }

    public Flowable<TargetMetric> targetsMetric() {
        return this.primaryChannelManager.candidatesChannel()
            .flatMapSingle(candidatesChannelEntries -> {
                String targetId = candidatesChannelEntries.getKey();
                Set<String> channelIds = candidatesChannelEntries.getValue();
                return this.primaryChannelManager.primaryChannelBy(targetId)
                    .defaultIfEmpty("unknown")
                    .flattenStreamAsFlowable(primaryChannel -> getChanelMetrics(channelIds, primaryChannel))
                    .toList()
                    .map(channelMetrics -> TargetMetric.builder().id(targetId).channelMetrics(channelMetrics).build());
            });
    }

    public Flowable<ChannelMetric> channelsMetric(final String targetId) {
        return this.primaryChannelManager.primaryChannelBy(targetId)
            .defaultIfEmpty("unknown")
            .flatMapPublisher(primaryChannel ->
                this.primaryChannelManager.candidatesChannel(targetId)
                    .flattenStreamAsFlowable(candidatesChannel -> getChanelMetrics(candidatesChannel, primaryChannel))
            );
    }

    private Stream<ChannelMetric> getChanelMetrics(final Set<String> channelIds, final String primaryChannel) {
        return channelIds
            .stream()
            .map(channelId -> {
                ChannelMetric.ChannelMetricBuilder metricBuilder = ChannelMetric
                    .builder()
                    .id(channelId)
                    .primary(channelId.equals(primaryChannel));
                this.localChannelRegistry.getById(channelId)
                    .ifPresent(controllerChannel ->
                        metricBuilder.active(controllerChannel.isActive()).pendingCommands(controllerChannel.hasPendingCommands())
                    );
                return metricBuilder.build();
            });
    }

    public ControllerChannel getChannelById(final String id) {
        return localChannelRegistry.getById(id).filter(ControllerChannel::isActive).orElse(null);
    }

    public ControllerChannel getOneChannelByTargetId(final String targetId) {
        return localChannelRegistry.getAllByTargetId(targetId).stream().filter(ControllerChannel::isActive).findFirst().orElse(null);
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
                signalChannelAlive(controllerChannel, true);
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
            .doOnComplete(() -> {
                log.debug(
                    "[{}] Channel '{}' successfully unregister for target '{}'",
                    this.identifyConfiguration.id(),
                    controllerChannel.id(),
                    controllerChannel.targetId()
                );
                signalChannelAlive(controllerChannel, false);
            })
            .doOnError(throwable ->
                log.warn(
                    "[{}] Unable to unregister channel '{}' for target '{}'",
                    this.identifyConfiguration.id(),
                    controllerChannel.id(),
                    controllerChannel.targetId(),
                    throwable
                )
            );
    }

    public <C extends Command<?>, R extends Reply<?>> Single<R> send(C command, String targetId) {
        return Maybe
            .fromCallable(() -> getOneChannelByTargetId(targetId))
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

    public void signalChannelAlive(final ControllerChannel controllerChannel, final boolean isAlive) {
        this.primaryChannelManager.sendChannelEvent(controllerChannel, isAlive);
    }
}
