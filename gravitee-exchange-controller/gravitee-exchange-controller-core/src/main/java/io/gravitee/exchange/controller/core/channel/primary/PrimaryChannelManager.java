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
import io.gravitee.node.api.cache.Cache;
import io.gravitee.node.api.cache.CacheConfiguration;
import io.gravitee.node.api.cache.CacheManager;
import io.gravitee.node.api.cluster.ClusterManager;
import io.gravitee.node.api.cluster.messaging.Topic;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Guillaume LAMIRAND (guillaume.lamirand at graviteesource.com)
 * @author GraviteeSource Team
 */
@RequiredArgsConstructor
@Slf4j
public class PrimaryChannelManager extends AbstractService<PrimaryChannelManager> {

    public static final String PRIMARY_CHANNEL_EVENTS_TOPIC = "controller-primary-channel-events";
    public static final String PRIMARY_CHANNEL_EVENTS_ELECTED_TOPIC = "controller-primary-channel-elected-events";
    public static final String PRIMARY_CHANNEL_CACHE = "controller-primary-channel";
    public static final String PRIMARY_CHANNEL_CANDIDATE_CACHE = "controller-primary-channel-candidate";
    private final IdentifyConfiguration identifyConfiguration;
    private final ClusterManager clusterManager;
    private final CacheManager cacheManager;
    private PrimaryChannelCandidateStore primaryChannelCandidateStore;
    private Cache<String, String> primaryChannelCache;
    private String subscriptionListenerId;
    private Topic<ChannelEvent> primaryChannelEventTopic;
    private Topic<PrimaryChannelElectedEvent> primaryChannelElectedEventTopic;
    private CacheConfiguration cacheConfiguration;

    @Override
    protected void doStart() throws Exception {
        log.debug("[{}] Starting primary channel manager", this.identifyConfiguration.id());
        super.doStart();
        cacheConfiguration = CacheConfiguration.builder().distributed(true).build();
        if (primaryChannelCandidateStore == null) {
            primaryChannelCandidateStore =
                new PrimaryChannelCandidateStore(
                    cacheManager.getOrCreateCache(identifyConfiguration.identifyName(PRIMARY_CHANNEL_CANDIDATE_CACHE), cacheConfiguration)
                );
        }
        primaryChannelCache = cacheManager.getOrCreateCache(identifyConfiguration.identifyName(PRIMARY_CHANNEL_CACHE), cacheConfiguration);
        primaryChannelEventTopic = clusterManager.topic(identifyConfiguration.identifyName(PRIMARY_CHANNEL_EVENTS_TOPIC));
        primaryChannelElectedEventTopic = clusterManager.topic(identifyConfiguration.identifyName(PRIMARY_CHANNEL_EVENTS_ELECTED_TOPIC));
        subscriptionListenerId =
            primaryChannelEventTopic.addMessageListener(message -> {
                ChannelEvent channelEvent = message.content();
                log.debug(
                    "[{}] New PrimaryChannelEvent received for channel '{}' on target '{}'",
                    this.identifyConfiguration.id(),
                    channelEvent.channelId(),
                    channelEvent.targetId()
                );
                if (clusterManager.self().primary()) {
                    log.debug(
                        "[{}] Handling PrimaryChannelEvent for channel '{}' on target '{}'",
                        this.identifyConfiguration.id(),
                        channelEvent.channelId(),
                        channelEvent.targetId()
                    );
                    handleChannelEvent(channelEvent);
                }
            });
    }

    @Override
    protected void doStop() throws Exception {
        log.debug("[{}] Stopping primary channel manager", this.identifyConfiguration.id());
        if (primaryChannelEventTopic != null && subscriptionListenerId != null) {
            primaryChannelEventTopic.removeMessageListener(subscriptionListenerId);
        }
        super.doStop();
    }

    public Flowable<Map.Entry<String, Set<String>>> candidatesChannel() {
        return primaryChannelCandidateStore.rxEntries();
    }

    public Maybe<Set<String>> candidatesChannel(final String targetId) {
        return primaryChannelCandidateStore.rxGet(targetId);
    }

    public Maybe<String> primaryChannelBy(final String targetId) {
        return primaryChannelCache.rxGet(targetId);
    }

    public void sendChannelEvent(final ControllerChannel controllerChannel, final boolean alive) {
        primaryChannelEventTopic.publish(
            ChannelEvent.builder().channelId(controllerChannel.id()).targetId(controllerChannel.targetId()).alive(alive).build()
        );
    }

    private void handleChannelEvent(final ChannelEvent channelEvent) {
        String targetId = channelEvent.targetId();
        String channelId = channelEvent.channelId();
        if (channelEvent.alive()) {
            primaryChannelCandidateStore.put(targetId, channelId);
        } else {
            primaryChannelCandidateStore.remove(targetId, channelId);
        }
        electPrimaryChannel(targetId);
    }

    private void electPrimaryChannel(final String targetId) {
        String previousPrimaryChannelId = primaryChannelCache.get(targetId);
        Set<String> channelIds = primaryChannelCandidateStore.get(targetId);

        if (null == channelIds || channelIds.isEmpty()) {
            log.warn(
                "[{}] Unable to elect a primary channel because there is no channel for target id '{}'",
                this.identifyConfiguration.id(),
                targetId
            );
            primaryChannelCache.evict(targetId);
            return;
        }
        if (!channelIds.contains(previousPrimaryChannelId)) {
            String newPrimaryChannelId = getRandomChannel(channelIds);
            if (newPrimaryChannelId != null) {
                primaryChannelCache.put(targetId, newPrimaryChannelId);
                primaryChannelElectedEventTopic.publish(
                    PrimaryChannelElectedEvent.builder().targetId(targetId).channelId(newPrimaryChannelId).build()
                );
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
