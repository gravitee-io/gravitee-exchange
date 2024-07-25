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
package io.gravitee.exchange.controller.core.channel.primary;

import io.gravitee.common.service.AbstractService;
import io.gravitee.exchange.api.configuration.IdentifyConfiguration;
import io.gravitee.exchange.api.controller.ControllerChannel;
import io.gravitee.exchange.controller.core.channel.ChannelManager;
import io.gravitee.node.api.cache.Cache;
import io.gravitee.node.api.cache.CacheConfiguration;
import io.gravitee.node.api.cache.CacheManager;
import io.gravitee.node.api.cluster.ClusterManager;
import io.gravitee.node.api.cluster.Member;
import io.gravitee.node.api.cluster.MemberListener;
import io.gravitee.node.api.cluster.messaging.Queue;
import io.gravitee.node.api.cluster.messaging.Topic;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.MaybeEmitter;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Guillaume LAMIRAND (guillaume.lamirand at graviteesource.com)
 * @author GraviteeSource Team
 */
@RequiredArgsConstructor
@Slf4j
public class PrimaryChannelManager extends AbstractService<PrimaryChannelManager> {

    public static final String PRIMARY_CHANNEL_ELECTED_EVENTS_TOPIC = "controller-primary-channel-elected-events";
    public static final String PRIMARY_CHANNEL_EVICTED_EVENTS_TOPIC = "controller-primary-channel-evicted-events";
    public static final String PRIMARY_CHANNEL_CACHE = "controller-primary-channel";
    public static final String PRIMARY_CHANNEL_CANDIDATE_CACHE = "controller-primary-channel-candidate";
    public static final String PRIMARY_CHANNEL_CANDIDATE_HEALTHCHECK_EVENTS_TOPIC =
        "controller-primary-channel-candidate-healthcheck-events";
    private static final int PRIMARY_CHANNEL_CANDIDATE_HEALTH_CHECK_DELAY = 1_000;
    private static final TimeUnit PRIMARY_CHANNEL_CANDIDATE_HEALTH_CHECK_DELAY_UNIT = TimeUnit.MILLISECONDS;
    private final IdentifyConfiguration identifyConfiguration;
    private final ClusterManager clusterManager;
    private final CacheManager cacheManager;
    private final ChannelManager channelManager;
    private final Map<String, MaybeEmitter<String>> candidateHealthCheckResponseEmitters = new ConcurrentHashMap<>();
    private PrimaryChannelCandidateRegistry primaryChannelCandidateRegistry;
    private Cache<String, String> primaryChannelRegistry;
    private Topic<PrimaryChannelElectedEvent> primaryChannelElectedEventTopic;
    private Topic<PrimaryChannelEvictedEvent> primaryChannelEvictedEventTopic;
    private Topic<PrimaryChannelCandidateHealthcheckEvent> primaryChannelHealthcheckEventTopic;
    private String primaryChannelHealthcheckSubscriptionId;
    private MemberListener primaryChannelHealthCheckListener;
    private Queue<PrimaryChannelCandidateHealthcheckEvent.Response> primaryChannelCandidatesHealthcheckResponseQueue;
    private String primaryChannelCandidatesHealthcheckResponseEventListenerId;
    private String primaryChannelCandidatesHealthcheckResponseQueueName;

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        log.debug("[{}] Starting primary channel manager", this.identifyConfiguration.id());
        CacheConfiguration cacheConfiguration = CacheConfiguration.builder().distributed(true).build();
        primaryChannelCandidateRegistry =
            new PrimaryChannelCandidateRegistry(
                cacheManager.getOrCreateCache(identifyConfiguration.identifyName(PRIMARY_CHANNEL_CANDIDATE_CACHE), cacheConfiguration)
            );
        primaryChannelRegistry =
            cacheManager.getOrCreateCache(identifyConfiguration.identifyName(PRIMARY_CHANNEL_CACHE), cacheConfiguration);
        primaryChannelElectedEventTopic = clusterManager.topic(identifyConfiguration.identifyName(PRIMARY_CHANNEL_ELECTED_EVENTS_TOPIC));
        primaryChannelEvictedEventTopic = clusterManager.topic(identifyConfiguration.identifyName(PRIMARY_CHANNEL_EVICTED_EVENTS_TOPIC));

        /*
         * Handle primary candidate health mechanism
         * When a cluster member is leaving, we must ensure that all known candidates are still connected.
         *
         * The mechanism use :
         *   - a topic where channel candidate healthcheck event will be sent and received by all controllers on the cluster
         *   - each controller depending on its locally connected channel will respond to a dynamic response queue
         *   - the node at the original of the healthCheck event will publish a ChannelEvent message accordingly
         *   - if no response is received before the defined delay, the channel is discarded from the candidate list
         */
        primaryChannelHealthcheckEventTopic =
            clusterManager.topic(identifyConfiguration.identifyName(PRIMARY_CHANNEL_CANDIDATE_HEALTHCHECK_EVENTS_TOPIC));
        primaryChannelHealthcheckSubscriptionId =
            primaryChannelHealthcheckEventTopic.addMessageListener(message ->
                handlePrimaryChannelCandidateHealthcheckEvent(message.content())
            );
        primaryChannelCandidatesHealthcheckResponseQueueName =
            this.identifyConfiguration.identifyName("controller-primary-channel-candidates-healthcheck-response-" + UUID.randomUUID());
        primaryChannelCandidatesHealthcheckResponseQueue = clusterManager.queue(primaryChannelCandidatesHealthcheckResponseQueueName);
        primaryChannelCandidatesHealthcheckResponseEventListenerId =
            primaryChannelCandidatesHealthcheckResponseQueue.addMessageListener(message ->
                handlePrimaryChannelCandidateHealthcheckResponse(message.content())
            );
        primaryChannelHealthCheckListener =
            new MemberListener() {
                @Override
                public void onMemberRemoved(final Member member) {
                    sendPrimaryChannelCandidatesHealthCheck();
                }
            };
        clusterManager.addMemberListener(primaryChannelHealthCheckListener);
    }

    private void handlePrimaryChannelCandidateHealthcheckEvent(final PrimaryChannelCandidateHealthcheckEvent event) {
        if (Instant.now().isBefore(event.eventTime().plus(PRIMARY_CHANNEL_CANDIDATE_HEALTH_CHECK_DELAY, ChronoUnit.MILLIS))) {
            log.debug(
                "[{}] Receiving primary channel candidate healthcheck event for channel '{}'",
                this.identifyConfiguration.id(),
                event.channelId()
            );
            Optional<ControllerChannel> channelOptional = channelManager.getChannelById(event.channelId());
            if (channelOptional.isPresent()) {
                ControllerChannel controllerChannel = channelOptional.get();
                log.debug(
                    "[{}] Responding to primary channel candidate event for channel '{}' on target '{}'",
                    this.identifyConfiguration.id(),
                    controllerChannel.id(),
                    controllerChannel.targetId()
                );
                PrimaryChannelCandidateHealthcheckEvent.Response responseEvent = PrimaryChannelCandidateHealthcheckEvent.Response
                    .builder()
                    .requestEventTime(event.eventTime())
                    .channelId(controllerChannel.id())
                    .targetId(controllerChannel.targetId())
                    .active(controllerChannel.isActive())
                    .build();
                clusterManager.queue(event.responseQueue()).add(responseEvent);
            } else {
                log.debug(
                    "[{}] Ignoring primary channel candidate healthcheck because channel '{}' is not connected to this controller",
                    this.identifyConfiguration.id(),
                    event.channelId()
                );
            }
        } else {
            log.debug(
                "[{}] Ignoring expired primary channel candidate healthcheck for channel '{}'",
                this.identifyConfiguration.id(),
                event.channelId()
            );
        }
    }

    private void handlePrimaryChannelCandidateHealthcheckResponse(final PrimaryChannelCandidateHealthcheckEvent.Response responseEvent) {
        log.debug(
            "[{}] Healthcheck response received for primary channel candidate '{}' for target '{}'",
            this.identifyConfiguration.id(),
            responseEvent.channelId(),
            responseEvent.targetId()
        );

        MaybeEmitter<String> maybeEmitter = candidateHealthCheckResponseEmitters.remove(responseEvent.channelId());
        if (maybeEmitter != null) {
            channelManager.publishChannelEvent(responseEvent.channelId(), responseEvent.targetId(), responseEvent.active(), false);
            maybeEmitter.onSuccess(responseEvent.channelId());
        } else {
            log.debug(
                "[{}] Ignoring expired healthcheck response for primary channel candidate '{}' for target '{}'",
                this.identifyConfiguration.id(),
                responseEvent.channelId(),
                responseEvent.targetId()
            );
        }
    }

    private void sendPrimaryChannelCandidatesHealthCheck() {
        if (clusterManager.self().primary()) {
            log.debug("[{}] Checking primary channel candidates health", this.identifyConfiguration.id());
            primaryChannelCandidateRegistry
                .rxEntrySet()
                .flatMapCompletable(entry -> {
                    String targetId = entry.getKey();
                    return Flowable
                        .fromIterable(entry.getValue())
                        .filter(channelId -> !candidateHealthCheckResponseEmitters.containsKey(channelId))
                        .flatMapCompletable(channelId ->
                            Completable
                                .mergeArray(
                                    Maybe
                                        .<String>create(emitter -> candidateHealthCheckResponseEmitters.put(channelId, emitter))
                                        .timeout(
                                            PRIMARY_CHANNEL_CANDIDATE_HEALTH_CHECK_DELAY,
                                            PRIMARY_CHANNEL_CANDIDATE_HEALTH_CHECK_DELAY_UNIT,
                                            Maybe.fromRunnable(() -> {
                                                log.warn(
                                                    "[{}] No healthcheck response received for primary channel candidate '{}' for target '{}'",
                                                    this.identifyConfiguration.id(),
                                                    channelId,
                                                    targetId
                                                );
                                                channelManager.publishChannelEvent(channelId, targetId, false, true);
                                            })
                                        )
                                        .ignoreElement(),
                                    Completable
                                        .fromRunnable(() ->
                                            log.debug(
                                                "[{}] Sending primary channel candidate healthcheck for channel '{}' on target '{}'",
                                                this.identifyConfiguration.id(),
                                                channelId,
                                                targetId
                                            )
                                        )
                                        .andThen(
                                            primaryChannelHealthcheckEventTopic.rxPublish(
                                                PrimaryChannelCandidateHealthcheckEvent
                                                    .builder()
                                                    .eventTime(Instant.now())
                                                    .channelId(channelId)
                                                    .responseQueue(primaryChannelCandidatesHealthcheckResponseQueueName)
                                                    .build()
                                            )
                                        )
                                )
                                .onErrorResumeNext(throwable -> {
                                    log.warn(
                                        "[{}] Unable to check primary channel candidate health '{}' for target '{}'",
                                        this.identifyConfiguration.id(),
                                        entry.getValue(),
                                        targetId,
                                        throwable
                                    );
                                    return Completable.complete();
                                })
                                .doFinally(() -> candidateHealthCheckResponseEmitters.remove(channelId))
                        );
                })
                .onErrorComplete()
                .blockingAwait();
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        log.debug("[{}] Stopping primary channel manager", this.identifyConfiguration.id());

        // Remove listeners for Primary channel candidates health check
        if (primaryChannelHealthCheckListener != null) {
            clusterManager.removeMemberListener(primaryChannelHealthCheckListener);
        }
        if (
            primaryChannelCandidatesHealthcheckResponseQueue != null && primaryChannelCandidatesHealthcheckResponseEventListenerId != null
        ) {
            primaryChannelCandidatesHealthcheckResponseQueue.removeMessageListener(
                primaryChannelCandidatesHealthcheckResponseEventListenerId
            );
        }
        if (primaryChannelHealthcheckEventTopic != null && primaryChannelHealthcheckSubscriptionId != null) {
            primaryChannelHealthcheckEventTopic.removeMessageListener(primaryChannelHealthcheckSubscriptionId);
        }
    }

    public boolean isPrimaryChannelFor(final String channelId, final String targetId) {
        String primaryChannelId = primaryChannelRegistry.get(targetId);
        return channelId.equals(primaryChannelId);
    }

    public void handleChannelCandidate(final ChannelEvent channelEvent) {
        String targetId = channelEvent.targetId();
        String channelId = channelEvent.channelId();
        if (channelEvent.active()) {
            log.debug(
                "[{}] Adding channel '{}' as new primary candidate for target '{}'",
                this.identifyConfiguration.id(),
                channelId,
                targetId
            );
            primaryChannelCandidateRegistry.put(targetId, channelId);
        } else {
            log.debug(
                "[{}] Channel '{}' is inactive, removing it from primary candidates for target '{}'",
                this.identifyConfiguration.id(),
                channelId,
                targetId
            );
            primaryChannelCandidateRegistry.remove(targetId, channelId);
        }
        electPrimaryChannel(targetId);
    }

    private void electPrimaryChannel(final String targetId) {
        String previousPrimaryChannelId = primaryChannelRegistry.get(targetId);
        Set<String> channelIds = primaryChannelCandidateRegistry.get(targetId);

        if (null == channelIds || channelIds.isEmpty()) {
            log.warn(
                "[{}] Unable to elect a primary channel because there is no channel for target '{}'",
                this.identifyConfiguration.id(),
                targetId
            );
            primaryChannelRegistry.evict(targetId);
            primaryChannelEvictedEventTopic.publish(PrimaryChannelEvictedEvent.builder().targetId(targetId).build());
            return;
        }
        if (!channelIds.contains(previousPrimaryChannelId)) {
            log.debug(
                "[{}] Previous elected primary channel '{}' is no more a primary candidate for target '{}'",
                this.identifyConfiguration.id(),
                previousPrimaryChannelId,
                targetId
            );
            String newPrimaryChannelId = getRandomChannel(channelIds);
            if (newPrimaryChannelId != null) {
                log.debug(
                    "[{}] Channel '{}' has been elected as a primary for target '{}'",
                    this.identifyConfiguration.id(),
                    newPrimaryChannelId,
                    targetId
                );
                primaryChannelRegistry.put(targetId, newPrimaryChannelId);
                primaryChannelElectedEventTopic.publish(
                    PrimaryChannelElectedEvent.builder().targetId(targetId).channelId(newPrimaryChannelId).build()
                );
            } else {
                log.debug("[{}] Unable to elect primary channel for target '{}'", this.identifyConfiguration.id(), targetId);
            }
        }
    }

    private String getRandomChannel(Set<String> channelIds) {
        int randomIndex = ThreadLocalRandom.current().nextInt(channelIds.size());
        int i = 0;
        for (String channelId : channelIds) {
            if (i == randomIndex) {
                return channelId;
            }
            i++;
        }
        // Send first one in case random loop didn't find any match
        Iterator<String> iterator = channelIds.iterator();
        if (iterator.hasNext()) {
            return channelIds.iterator().next();
        }
        // Shouldn't happen in any case
        return null;
    }
}
