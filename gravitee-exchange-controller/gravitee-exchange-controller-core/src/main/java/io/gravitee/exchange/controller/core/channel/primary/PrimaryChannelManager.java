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
import io.gravitee.node.api.cache.Cache;
import io.gravitee.node.api.cache.CacheConfiguration;
import io.gravitee.node.api.cache.CacheManager;
import io.gravitee.node.api.cluster.ClusterManager;
import io.gravitee.node.api.cluster.messaging.Topic;
import io.reactivex.rxjava3.core.Maybe;
import java.util.Iterator;
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

    public static final String PRIMARY_CHANNEL_ELECTED_EVENTS_TOPIC = "controller-primary-channel-elected-events";
    public static final String PRIMARY_CHANNEL_EVICTED_EVENTS_TOPIC = "controller-primary-channel-evicted-events";
    public static final String PRIMARY_CHANNEL_CACHE = "controller-primary-channel";
    public static final String PRIMARY_CHANNEL_CANDIDATE_CACHE = "controller-primary-channel-candidate";
    private final IdentifyConfiguration identifyConfiguration;
    private final ClusterManager clusterManager;
    private final CacheManager cacheManager;
    private PrimaryChannelCandidateRegistry primaryChannelCandidateRegistry;
    private Cache<String, String> primaryChannelRegistry;
    private Topic<PrimaryChannelElectedEvent> primaryChannelElectedEventTopic;
    private Topic<PrimaryChannelEvictedEvent> primaryChannelEvictedEventTopic;

    @Override
    protected void doStart() throws Exception {
        log.debug("[{}] Starting primary channel manager", this.identifyConfiguration.id());
        super.doStart();
        CacheConfiguration cacheConfiguration = CacheConfiguration.builder().distributed(true).build();
        if (primaryChannelCandidateRegistry == null) {
            primaryChannelCandidateRegistry =
                new PrimaryChannelCandidateRegistry(
                    cacheManager.getOrCreateCache(identifyConfiguration.identifyName(PRIMARY_CHANNEL_CANDIDATE_CACHE), cacheConfiguration)
                );
        }
        primaryChannelRegistry =
            cacheManager.getOrCreateCache(identifyConfiguration.identifyName(PRIMARY_CHANNEL_CACHE), cacheConfiguration);
        primaryChannelElectedEventTopic = clusterManager.topic(identifyConfiguration.identifyName(PRIMARY_CHANNEL_ELECTED_EVENTS_TOPIC));
        primaryChannelEvictedEventTopic = clusterManager.topic(identifyConfiguration.identifyName(PRIMARY_CHANNEL_EVICTED_EVENTS_TOPIC));
    }

    @Override
    protected void doStop() throws Exception {
        log.debug("[{}] Stopping primary channel manager", this.identifyConfiguration.id());
        super.doStop();
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
