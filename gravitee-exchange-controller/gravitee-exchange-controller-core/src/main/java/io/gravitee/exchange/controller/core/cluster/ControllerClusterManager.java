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
import io.gravitee.exchange.api.command.Command;
import io.gravitee.exchange.api.command.Reply;
import io.gravitee.exchange.api.configuration.IdentifyConfiguration;
import io.gravitee.exchange.api.controller.ControllerChannel;
import io.gravitee.exchange.api.controller.metrics.ChannelMetric;
import io.gravitee.exchange.api.controller.metrics.TargetMetric;
import io.gravitee.exchange.controller.core.channel.ChannelManager;
import io.gravitee.exchange.controller.core.cluster.command.ClusteredCommand;
import io.gravitee.exchange.controller.core.cluster.command.ClusteredReply;
import io.gravitee.exchange.controller.core.cluster.exception.ControllerClusterException;
import io.gravitee.exchange.controller.core.cluster.exception.ControllerClusterShutdownException;
import io.gravitee.exchange.controller.core.cluster.exception.ControllerClusterTimeoutException;
import io.gravitee.node.api.cache.CacheManager;
import io.gravitee.node.api.cluster.ClusterManager;
import io.gravitee.node.api.cluster.messaging.Message;
import io.gravitee.node.api.cluster.messaging.Queue;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.core.SingleEmitter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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

    private final IdentifyConfiguration identifyConfiguration;
    private final ClusterManager clusterManager;
    private final ChannelManager channelManager;
    private final Map<String, SingleEmitter<Reply<?>>> resultEmittersByCommand = new ConcurrentHashMap<>();
    private final Map<String, String> subscriptionsListenersByChannel = new ConcurrentHashMap<>();
    private final String replyQueueName;

    private Queue<ClusteredReply<?>> clusteredReplyQueue;
    private String clusteredReplySubscriptionId;

    public ControllerClusterManager(
        final IdentifyConfiguration identifyConfiguration,
        final ClusterManager clusterManager,
        final CacheManager cacheManager
    ) {
        this.identifyConfiguration = identifyConfiguration;
        this.clusterManager = clusterManager;
        this.channelManager = new ChannelManager(identifyConfiguration, clusterManager, cacheManager);
        this.replyQueueName = identifyConfiguration.identifyName("controller-cluster-replies-" + UUID.randomUUID());
    }

    @Override
    protected void doStart() throws Exception {
        log.debug("[{}] Starting controller cluster manager", identifyConfiguration.id());
        super.doStart();
        channelManager.start();

        // Create a queue to receive replies on and start to listen it.
        clusteredReplyQueue = clusterManager.queue(replyQueueName);
        clusteredReplySubscriptionId = clusteredReplyQueue.addMessageListener(this::handleClusteredReply);
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

    @Override
    protected void doStop() throws Exception {
        log.debug("[{}] Stopping controller cluster manager", identifyConfiguration.id());
        super.doStop();

        // Stop all command listeners.
        final List<ControllerChannel> channels = subscriptionsListenersByChannel
            .values()
            .stream()
            .map(channelManager::getChannelById)
            .filter(Objects::nonNull)
            .toList();

        channels.forEach(this::channelDisconnected);

        // Stop channel manager
        channelManager.stop();

        // Stop listening the reply queue.
        if (clusteredReplyQueue != null && clusteredReplySubscriptionId != null) {
            clusteredReplyQueue.removeMessageListener(clusteredReplySubscriptionId);
        }

        // Finally, notify all pending Rx emitters with an error.
        resultEmittersByCommand.forEach((type, emitter) -> emitter.onError(new ControllerClusterShutdownException()));
        resultEmittersByCommand.clear();
    }

    public Flowable<TargetMetric> targetsMetric() {
        return channelManager.targetsMetric();
    }

    public Flowable<ChannelMetric> channelsMetric(final String targetId) {
        return channelManager.channelsMetric(targetId);
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

        return Single
            .<Reply<?>>create(emitter -> sendClusteredCommand(clusteredCommand, emitter))
            .timeout(
                command.getReplyTimeoutMs(),
                TimeUnit.MILLISECONDS,
                Single.error(() -> {
                    log.warn(
                        "[{}] No reply received in time from cluster manager for command [{}, {}]",
                        this.identifyConfiguration.id(),
                        command.getType(),
                        command.getId()
                    );
                    return new ControllerClusterTimeoutException();
                })
            )
            // Cleanup result emitters list if cancelled by the upstream or an error occurred.
            .doFinally(() -> resultEmittersByCommand.remove(command.getId()));
    }

    private void sendClusteredCommand(final ClusteredCommand<?> clusteredCommand, final SingleEmitter<Reply<?>> emitter) {
        String targetId = clusteredCommand.targetId();
        final String queueName = getTargetQueueName(targetId);

        log.debug(
            "[{}] Trying to send a command [{} ({})] to the target [{}] through the cluster.",
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
            log.error("[{}] Failed to send command to the installation [{}].", this.identifyConfiguration.id(), targetId, e);
            emitter.onError(e);
        }
    }
}
