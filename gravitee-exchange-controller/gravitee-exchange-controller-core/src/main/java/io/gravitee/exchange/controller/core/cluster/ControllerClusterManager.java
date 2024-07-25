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
package io.gravitee.exchange.controller.core.cluster;

import io.gravitee.common.service.AbstractService;
import io.gravitee.exchange.api.channel.Channel;
import io.gravitee.exchange.api.command.Command;
import io.gravitee.exchange.api.command.Reply;
import io.gravitee.exchange.api.configuration.IdentifyConfiguration;
import io.gravitee.exchange.api.controller.ControllerChannel;
import io.gravitee.exchange.api.controller.metrics.ChannelMetric;
import io.gravitee.exchange.api.controller.metrics.TargetChannelsMetric;
import io.gravitee.exchange.controller.core.channel.ChannelManager;
import io.gravitee.exchange.controller.core.cluster.command.ClusteredCommand;
import io.gravitee.exchange.controller.core.cluster.command.ClusteredReply;
import io.gravitee.exchange.controller.core.cluster.exception.ControllerClusterException;
import io.gravitee.exchange.controller.core.cluster.exception.ControllerClusterNoChannelException;
import io.gravitee.exchange.controller.core.cluster.exception.ControllerClusterShutdownException;
import io.gravitee.exchange.controller.core.cluster.exception.ControllerClusterTimeoutException;
import io.gravitee.node.api.cache.CacheManager;
import io.gravitee.node.api.cluster.ClusterManager;
import io.gravitee.node.api.cluster.Member;
import io.gravitee.node.api.cluster.MemberListener;
import io.gravitee.node.api.cluster.messaging.Message;
import io.gravitee.node.api.cluster.messaging.Queue;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.core.SingleEmitter;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * @author Guillaume LAMIRAND (guillaume.lamirand at graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
@Service
public class ControllerClusterManager extends AbstractService<ControllerClusterManager> {

    private static final String AUTO_REBALANCING_ENABLED = "controller.auto-rebalancing.enabled";
    private static final String AUTO_REBALANCING_DELAY = "controller.auto-rebalancing.delay";
    private static final long AUTO_REBALANCING_DELAY_DEFAULT = 60_000;
    private static final String AUTO_REBALANCING_UNIT = "controller.auto-rebalancing.unit";
    private static final TimeUnit AUTO_REBALANCING_UNIT_DEFAULT = TimeUnit.MILLISECONDS;
    private final IdentifyConfiguration identifyConfiguration;
    private final ClusterManager clusterManager;
    private final ChannelManager channelManager;
    private final Map<String, SingleEmitter<Reply<?>>> resultEmittersByCommand = new ConcurrentHashMap<>();
    private final Map<String, String> subscriptionsListenersByChannel = new ConcurrentHashMap<>();
    private final String replyQueueName;
    private final long rebalancingDelay;
    private final TimeUnit rebalancingUnit;
    private final Boolean rebalancingEnabled;

    private Queue<ClusteredReply<?>> clusteredReplyQueue;
    private String clusteredReplySubscriptionId;
    private MemberListener memberListener;
    private ScheduledExecutorService rebalancingExecutorService;
    private LocalDateTime lastMemberAddedTime;

    public ControllerClusterManager(
        final IdentifyConfiguration identifyConfiguration,
        final ClusterManager clusterManager,
        final CacheManager cacheManager
    ) {
        this.identifyConfiguration = identifyConfiguration;
        this.clusterManager = clusterManager;
        this.channelManager = new ChannelManager(identifyConfiguration, clusterManager, cacheManager);
        this.replyQueueName = identifyConfiguration.identifyName("controller-cluster-replies-" + UUID.randomUUID());
        this.rebalancingEnabled = identifyConfiguration.getProperty(AUTO_REBALANCING_ENABLED, Boolean.class, true);
        this.rebalancingDelay = identifyConfiguration.getProperty(AUTO_REBALANCING_DELAY, Long.class, AUTO_REBALANCING_DELAY_DEFAULT);
        this.rebalancingUnit = identifyConfiguration.getProperty(AUTO_REBALANCING_UNIT, TimeUnit.class, AUTO_REBALANCING_UNIT_DEFAULT);
    }

    @Override
    protected void doStart() throws Exception {
        log.debug("[{}] Starting controller cluster manager", identifyConfiguration.id());
        super.doStart();
        channelManager.start();

        // Create a queue to receive replies on and start to listen it.
        clusteredReplyQueue = clusterManager.queue(replyQueueName);
        clusteredReplySubscriptionId = clusteredReplyQueue.addMessageListener(this::handleClusteredReply);

        /*
         * Handle re-balancing mechanism
         *
         * When a member of the cluster is joining, we must ensure that all channels are properly scaled across
         * controllers, to avoid too many channels on the same controller.
         *
         * The mechanism will :
         *   - save the last member time
         *   - scheduled a task after a certain delay to avoid doing the processus too many times when rolling update
         *   - discard any process after the delay if the last member is too young
         *   - filters the local connected channels based on the cluster size
         *   - disconnected all of them which have not any pending commands
         */
        if (Boolean.TRUE.equals(rebalancingEnabled)) {
            rebalancingExecutorService = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "gio-exchange-rebalancing"));
            memberListener =
                new MemberListener() {
                    @Override
                    public void onMemberAdded(final Member member) {
                        lastMemberAddedTime = LocalDateTime.now();
                        rebalancingExecutorService.schedule(() -> executeChannelsRebalancing(), rebalancingDelay, rebalancingUnit);
                    }
                };
            clusterManager.addMemberListener(memberListener);
        }
    }

    private void handleClusteredReply(Message<ClusteredReply<?>> clusteredReplyMessage) {
        ClusteredReply<?> clusteredReply = clusteredReplyMessage.content();
        SingleEmitter<Reply<?>> emitter = resultEmittersByCommand.remove(clusteredReply.getCommandId());

        if (emitter != null) {
            if (clusteredReply.isError()) {
                emitter.onError(clusteredReply.getControllerClusterException());
            } else {
                emitter.onSuccess(clusteredReply.getReply());
            }
        }
    }

    private void executeChannelsRebalancing() {
        log.debug("[{}] Starting re-balancing process", identifyConfiguration.id());
        if (lastMemberAddedTime.plus(rebalancingDelay, rebalancingUnit.toChronoUnit()).isBefore(LocalDateTime.now())) {
            int clusterSize = clusterManager.members().size();
            if (clusterSize > 1) {
                Flowable
                    .fromIterable(channelManager.getChannels())
                    .groupBy(Channel::targetId)
                    .flatMap(group ->
                        group
                            .toList()
                            // Keep only target with more than 1 channel
                            .filter(channels -> channels.size() > 1)
                            // Reduce the list of 1/members elements
                            .flattenStreamAsFlowable(channelMetrics -> {
                                int size = channelMetrics.size();
                                int reducedSize = (int) Math.ceil((float) size / clusterSize);
                                log.debug(
                                    "[{}] Maximum '{}' channels for target '{}'} will be scheduled for re-balancing",
                                    identifyConfiguration.id(),
                                    group.getKey(),
                                    reducedSize
                                );
                                // Filter channel with pending commands
                                return channelMetrics
                                    .stream()
                                    .filter(channelMetric -> !channelMetric.hasPendingCommands())
                                    .limit(reducedSize);
                            })
                    )
                    // Unregister channel (close with reconnection)
                    .flatMapCompletable(controllerChannel -> {
                        log.debug(
                            "[{}] Re-balancing channel '{}' for the target '{}'",
                            identifyConfiguration.id(),
                            controllerChannel.id(),
                            controllerChannel.targetId()
                        );
                        return unregister(controllerChannel).onErrorComplete();
                    })
                    .doOnComplete(() -> log.debug("[{}] Re-balancing process finished", identifyConfiguration.id()))
                    .doOnError(throwable -> log.warn("[{}] Re-balancing process failed", identifyConfiguration.id(), throwable))
                    .onErrorComplete()
                    // Need to await the current process to finish
                    .blockingAwait();
            } else {
                log.debug("[{}] Re-balancing process ignored: there is no other cluster member", identifyConfiguration.id());
            }
        } else {
            log.debug("[{}] Re-balancing process ignored: latest member added is too recent", identifyConfiguration.id());
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        String message = "[%s] Stopping controller cluster manager".formatted(identifyConfiguration.id());
        log.debug(message);

        // Stop all command listeners.
        subscriptionsListenersByChannel
            .values()
            .stream()
            .map(id -> channelManager.getChannelById(id).orElse(null))
            .filter(Objects::nonNull)
            .forEach(this::channelDisconnected);

        // Remove member listener
        if (memberListener != null) {
            clusterManager.removeMemberListener(memberListener);
        }

        // Shutting down rebalancing executor
        if (rebalancingExecutorService != null) {
            rebalancingExecutorService.shutdown();
        }

        // Stop channel manager
        channelManager.stop();

        // Stop listening the reply queue.
        if (clusteredReplyQueue != null && clusteredReplySubscriptionId != null) {
            clusteredReplyQueue.removeMessageListener(clusteredReplySubscriptionId);
        }

        // Finally, notify all pending Rx emitters with an error.
        resultEmittersByCommand.forEach((type, emitter) ->
            emitter.onError(new ControllerClusterShutdownException("Stopping controller cluster manager"))
        );
        resultEmittersByCommand.clear();
    }

    public Flowable<TargetChannelsMetric> channelsMetricsByTarget() {
        return channelManager.channelsMetricsByTarget();
    }

    public Flowable<ChannelMetric> channelsMetricsForTarget(final String targetId) {
        return channelManager.channelsMetricsForTarget(targetId);
    }

    public Maybe<ChannelMetric> channelMetric(final String id) {
        return channelManager.channelMetric(id);
    }

    /**
     * Indicates to the controller cluster that a <code>ControllerChannel</code> must be registered.
     * It means that this current controller instance will be able to accept command addressed to the specified channel from anywhere in the cluster and forward them to the appropriate target.
     *
     * @param controllerChannel the newly connected channel.
     */
    public Completable register(final ControllerChannel controllerChannel) {
        return channelManager.register(controllerChannel).andThen(Completable.fromRunnable(() -> channelConnected(controllerChannel)));
    }

    private void channelConnected(final ControllerChannel channel) {
        final String targetId = channel.targetId();
        final String channelId = channel.id();
        final String queueName = getTargetQueueName(targetId);
        final Queue<ClusteredCommand<?>> queue = clusterManager.queue(queueName);
        String subscriptionId = queue.addMessageListener(this::onClusterCommand);
        subscriptionsListenersByChannel.put(channelId, subscriptionId);
    }

    private String getTargetQueueName(final String targetId) {
        return this.identifyConfiguration.identifyName("cluster-command-" + targetId);
    }

    private void onClusterCommand(final Message<ClusteredCommand<?>> clusteredCommandMessage) {
        ClusteredCommand<?> clusteredCommand = clusteredCommandMessage.content();
        final Queue<ClusteredReply<?>> replyToQueue = clusterManager.queue(clusteredCommand.replyToQueue());

        channelManager
            .send(clusteredCommand.command(), clusteredCommand.targetId())
            .map(reply -> new ClusteredReply<>(clusteredCommand.command().getId(), reply))
            .onErrorReturn(throwable -> new ClusteredReply<>(clusteredCommand.command().getId(), new ControllerClusterException(throwable)))
            .doOnSuccess(replyToQueue::add)
            .doOnError(throwable ->
                log.warn(
                    "[{}] Unable to write cluster reply for command '{}' for '{}' to clustered queue",
                    identifyConfiguration.id(),
                    clusteredCommand.command().getId(),
                    clusteredCommand.command().getType()
                )
            )
            .subscribe();
    }

    /**
     * Indicates to the controller cluster that a <code>ControllerChannel</code> must be unregistered.
     * It means that this current controller instance will stop listening commands addressed to this channel.
     *
     * @param controllerChannel the channel disconnected.
     */
    public Completable unregister(final ControllerChannel controllerChannel) {
        return channelManager.unregister(controllerChannel).andThen(Completable.fromRunnable(() -> channelDisconnected(controllerChannel)));
    }

    private void channelDisconnected(final ControllerChannel channel) {
        final String channelId = channel.id();
        final String targetId = channel.targetId();
        final String listenerSubscriptionId = subscriptionsListenersByChannel.remove(channelId);

        if (listenerSubscriptionId != null) {
            final String queueName = getTargetQueueName(targetId);
            final Queue<ClusteredCommand<?>> queue = clusterManager.queue(queueName);
            queue.removeMessageListener(listenerSubscriptionId);
        }
    }

    public Single<Reply<?>> sendCommand(final Command<?> command, final String targetId) {
        final ClusteredCommand<?> clusteredCommand = new ClusteredCommand<>(command, targetId, replyQueueName);

        return channelManager
            .channelsMetricsForTarget(targetId)
            .isEmpty()
            .flatMap(isEmpty -> {
                if (Boolean.TRUE.equals(isEmpty)) {
                    String errorMsg =
                        "[%s] No channel available for the target [%s] to send command [%s, %s]".formatted(
                                this.identifyConfiguration.id(),
                                targetId,
                                command.getType(),
                                command.getId()
                            );
                    return Single.error(new ControllerClusterNoChannelException(errorMsg));
                } else {
                    return Single.<Reply<?>>create(emitter -> sendClusteredCommand(clusteredCommand, emitter));
                }
            })
            .timeout(
                command.getReplyTimeoutMs(),
                TimeUnit.MILLISECONDS,
                Single.error(() -> {
                    String errorMsg =
                        "[%s] No reply received in time from cluster manager for command [%s, %s]".formatted(
                                this.identifyConfiguration.id(),
                                command.getType(),
                                command.getId()
                            );
                    log.warn(errorMsg);
                    return new ControllerClusterTimeoutException(errorMsg);
                })
            )
            // Cleanup result emitters list if cancelled by the upstream or an error occurred.
            .doFinally(() -> resultEmittersByCommand.remove(command.getId()));
    }

    private void sendClusteredCommand(final ClusteredCommand<?> clusteredCommand, final SingleEmitter<Reply<?>> emitter) {
        String targetId = clusteredCommand.targetId();
        final String queueName = getTargetQueueName(targetId);

        log.debug(
            "[{}] Trying to send a command [{} ({})] to the target '{}' through the cluster.",
            this.identifyConfiguration.id(),
            clusteredCommand.command().getId(),
            clusteredCommand.command().getType(),
            targetId
        );

        try {
            // Save the Rx emitter for later reuse (ie: when a reply will be sent in the reply queue).
            resultEmittersByCommand.put(clusteredCommand.command().getId(), emitter);

            // Send the command to queue dedicated to the installation.
            final Queue<ClusteredCommand<?>> queue = clusterManager.queue(queueName);
            queue.add(clusteredCommand);
        } catch (Exception e) {
            log.error("[{}] Failed to send command to the installation '{}'.", this.identifyConfiguration.id(), targetId, e);
            emitter.onError(e);
        }
    }
}
