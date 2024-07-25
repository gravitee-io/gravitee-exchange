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

import static io.gravitee.exchange.controller.core.channel.primary.PrimaryChannelManager.PRIMARY_CHANNEL_CACHE;
import static io.gravitee.exchange.controller.core.channel.primary.PrimaryChannelManager.PRIMARY_CHANNEL_CANDIDATE_CACHE;
import static io.gravitee.exchange.controller.core.channel.primary.PrimaryChannelManager.PRIMARY_CHANNEL_CANDIDATE_HEALTHCHECK_EVENTS_TOPIC;
import static io.gravitee.exchange.controller.core.channel.primary.PrimaryChannelManager.PRIMARY_CHANNEL_ELECTED_EVENTS_TOPIC;
import static io.gravitee.exchange.controller.core.channel.primary.PrimaryChannelManager.PRIMARY_CHANNEL_EVICTED_EVENTS_TOPIC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.gravitee.exchange.api.configuration.IdentifyConfiguration;
import io.gravitee.exchange.controller.core.channel.ChannelManager;
import io.gravitee.exchange.controller.core.cluster.MultiMemberStandaloneClusterManager;
import io.gravitee.node.api.cache.Cache;
import io.gravitee.node.api.cache.CacheConfiguration;
import io.gravitee.node.api.cache.CacheManager;
import io.gravitee.node.api.cluster.Member;
import io.gravitee.node.api.cluster.MemberListener;
import io.gravitee.node.api.cluster.messaging.Topic;
import io.gravitee.node.plugin.cache.standalone.StandaloneCacheManager;
import io.gravitee.node.plugin.cluster.standalone.StandaloneMember;
import io.vertx.core.Vertx;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;

/**
 * @author Guillaume LAMIRAND (guillaume.lamirand at graviteesource.com)
 * @author GraviteeSource Team
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
@ExtendWith({ VertxExtension.class, MockitoExtension.class })
class PrimaryChannelManagerTest {

    @Mock
    private ChannelManager mockChannelManager;

    private CacheManager cacheManager;
    private MultiMemberStandaloneClusterManager clusterManager;
    private MockEnvironment environment;
    private IdentifyConfiguration identifyConfiguration;
    private PrimaryChannelManager cut;
    private Cache<String, String> primaryChannelCache;
    private Cache<String, Set<String>> primaryChannelCandidateCache;
    private Topic<PrimaryChannelElectedEvent> primaryChannelElectedEventTopic;
    private Topic<PrimaryChannelEvictedEvent> primaryChannelEvictedEventTopic;
    private Topic<PrimaryChannelCandidateHealthcheckEvent> primaryChannelHealthcheckEventTopic;
    private MemberListener memberListener;

    @BeforeEach
    public void beforeEach(Vertx vertx) throws Exception {
        environment = new MockEnvironment();
        identifyConfiguration = new IdentifyConfiguration(environment);
        cacheManager = new StandaloneCacheManager();
        cacheManager.start();
        clusterManager = new MultiMemberStandaloneClusterManager(vertx);
        clusterManager.start();
        cut = new PrimaryChannelManager(identifyConfiguration, clusterManager, cacheManager, mockChannelManager);
        cut.start();

        CacheConfiguration cacheConfiguration = CacheConfiguration.builder().distributed(true).build();
        primaryChannelCache = cacheManager.getOrCreateCache(identifyConfiguration.identifyName(PRIMARY_CHANNEL_CACHE), cacheConfiguration);
        primaryChannelCandidateCache =
            cacheManager.getOrCreateCache(identifyConfiguration.identifyName(PRIMARY_CHANNEL_CANDIDATE_CACHE), cacheConfiguration);
        primaryChannelElectedEventTopic = clusterManager.topic(identifyConfiguration.identifyName(PRIMARY_CHANNEL_ELECTED_EVENTS_TOPIC));
        primaryChannelEvictedEventTopic = clusterManager.topic(identifyConfiguration.identifyName(PRIMARY_CHANNEL_EVICTED_EVENTS_TOPIC));
        primaryChannelHealthcheckEventTopic =
            clusterManager.topic(identifyConfiguration.identifyName(PRIMARY_CHANNEL_CANDIDATE_HEALTHCHECK_EVENTS_TOPIC));
    }

    @AfterEach
    public void afterEach() {
        primaryChannelCache.clear();
        if (memberListener != null) {
            clusterManager.removeMemberListener(memberListener);
        }
    }

    @Test
    void should_elect_new_primary_channel(VertxTestContext vertxTestContext) throws InterruptedException {
        Checkpoint checkpoint = vertxTestContext.checkpoint();
        primaryChannelElectedEventTopic.addMessageListener(message -> checkpoint.flag());
        cut.handleChannelCandidate(ChannelEvent.builder().channelId("channelId").targetId("targetId").active(true).build());
        assertThat(vertxTestContext.awaitCompletion(1, TimeUnit.SECONDS)).isTrue();
        assertThat(primaryChannelCache.containsKey("targetId")).isTrue();
        assertThat(primaryChannelCache.get("targetId")).isEqualTo("channelId");
        assertThat(primaryChannelCandidateCache.containsKey("targetId")).isTrue();
        assertThat(primaryChannelCandidateCache.get("targetId")).containsOnly("channelId");
        assertThat(cut.isPrimaryChannelFor("channelId", "targetId")).isTrue();
    }

    @Test
    void should_elect_then_evict_primary_channel(VertxTestContext vertxTestContext) throws InterruptedException {
        Checkpoint checkpoint = vertxTestContext.checkpoint(2);
        primaryChannelElectedEventTopic.addMessageListener(message -> {
            checkpoint.flag();
            cut.handleChannelCandidate(ChannelEvent.builder().channelId("channelId").targetId("targetId").active(false).build());
        });
        primaryChannelEvictedEventTopic.addMessageListener(message -> checkpoint.flag());
        cut.handleChannelCandidate(ChannelEvent.builder().channelId("channelId").targetId("targetId").active(true).build());

        assertThat(vertxTestContext.awaitCompletion(1, TimeUnit.SECONDS)).isTrue();
        // shouldn't contain any channel for the target
        assertThat(primaryChannelCache.containsKey("targetId")).isFalse();
        // shouldn't have any primary channel for the target
        assertThat(primaryChannelCache.containsKey("targetId")).isFalse();
        assertThat(primaryChannelCandidateCache.containsKey("targetId")).isFalse();
        assertThat(cut.isPrimaryChannelFor("channelId", "targetId")).isFalse();
    }

    @Test
    void should_reelect_after_evicted_primary_channel() {
        AtomicInteger channelElectedEventsCount = new AtomicInteger(0);
        primaryChannelElectedEventTopic.addMessageListener(message -> {
            if (channelElectedEventsCount.incrementAndGet() == 1) {
                // Make sure to election of primary is done before sending new channel event
                cut.handleChannelCandidate(ChannelEvent.builder().channelId("channelId2").targetId("targetId").active(true).build());
            }
        });
        cut.handleChannelCandidate(ChannelEvent.builder().channelId("channelId").targetId("targetId").active(true).build());
        await()
            .atMost(1, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                assertThat(primaryChannelCandidateCache.get("targetId")).hasSize(2);
            });

        cut.handleChannelCandidate(ChannelEvent.builder().channelId("channelId").targetId("targetId").active(false).build());
        await()
            .atMost(1, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                assertThat(primaryChannelCache.containsKey("targetId")).isTrue();
                assertThat(primaryChannelCache.get("targetId")).isEqualTo("channelId2");
                assertThat(primaryChannelCandidateCache.containsKey("targetId")).isTrue();
                assertThat(primaryChannelCandidateCache.get("targetId")).containsOnly("channelId2");
                assertThat(cut.isPrimaryChannelFor("channelId2", "targetId")).isTrue();
            });
    }

    @Test
    void should_send_channel_candidate_healthcheck_when_member_is_leaving(VertxTestContext vertxTestContext) throws InterruptedException {
        Checkpoint checkpoint = vertxTestContext.checkpoint(3); // 1 for leaving member; 2 for healtchcheck event        memberListener = memberListener(checkpoint);
        StandaloneMember member = new StandaloneMember();
        clusterManager.addMember(member);
        memberListener = memberListener(checkpoint);
        clusterManager.addMemberListener(memberListener);
        primaryChannelCandidateCache.put("targetId", Set.of("channelId", "channelId2"));
        primaryChannelHealthcheckEventTopic.addMessageListener(message -> {
            checkpoint.flag();
            PrimaryChannelCandidateHealthcheckEvent content = message.content();
            if (content.channelId().equals("channelId")) {
                clusterManager
                    .queue(content.responseQueue())
                    .add(
                        PrimaryChannelCandidateHealthcheckEvent.Response
                            .builder()
                            .requestEventTime(content.eventTime())
                            .channelId("channelId")
                            .targetId("targetId")
                            .active(true)
                            .build()
                    );
            }
        });
        clusterManager.removeMember(member);
        assertThat(vertxTestContext.awaitCompletion(10, TimeUnit.SECONDS)).isTrue();
        verify(mockChannelManager, times(1)).publishChannelEvent("channelId", "targetId", true, false);
        verify(mockChannelManager, times(1)).publishChannelEvent("channelId2", "targetId", false, true);
    }

    @Test
    void should_ignore_primary_candidate_healthcheck_response_when_timestamp_is_old(VertxTestContext vertxTestContext)
        throws InterruptedException {
        Checkpoint checkpoint = vertxTestContext.checkpoint(2); // 1 for leaving member; 1 for healtchcheck event        memberListener = memberListener(checkpoint);
        StandaloneMember member = new StandaloneMember();
        clusterManager.addMember(member);
        memberListener = memberListener(checkpoint);
        clusterManager.addMemberListener(memberListener);
        primaryChannelCandidateCache.put("targetId", Set.of("channelId"));
        primaryChannelHealthcheckEventTopic.addMessageListener(message -> {
            checkpoint.flag();
            PrimaryChannelCandidateHealthcheckEvent content = message.content();
            await().atLeast(1000, TimeUnit.MILLISECONDS).until(() -> true);
            clusterManager
                .queue(content.responseQueue())
                .add(
                    PrimaryChannelCandidateHealthcheckEvent.Response
                        .builder()
                        .requestEventTime(Instant.MIN)
                        .channelId("channelId")
                        .targetId("targetId")
                        .active(true)
                        .build()
                );
        });
        clusterManager.removeMember(member);
        assertThat(vertxTestContext.awaitCompletion(10, TimeUnit.SECONDS)).isTrue();
        verify(mockChannelManager, times(0)).publishChannelEvent("channelId", "targetId", true, false);
    }

    private static MemberListener memberListener(final Checkpoint checkpoint) {
        return new MemberListener() {
            @Override
            public void onMemberRemoved(final Member member) {
                checkpoint.flag();
            }
        };
    }
}
