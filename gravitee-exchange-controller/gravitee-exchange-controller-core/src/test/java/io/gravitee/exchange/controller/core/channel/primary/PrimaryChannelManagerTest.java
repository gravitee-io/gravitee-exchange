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
import static io.gravitee.exchange.controller.core.channel.primary.PrimaryChannelManager.PRIMARY_CHANNEL_EVENTS_ELECTED_TOPIC;
import static io.gravitee.exchange.controller.core.channel.primary.PrimaryChannelManager.PRIMARY_CHANNEL_EVENTS_EVICTED_TOPIC;
import static io.gravitee.exchange.controller.core.channel.primary.PrimaryChannelManager.PRIMARY_CHANNEL_EVENTS_TOPIC;
import static org.assertj.core.api.Assertions.assertThat;

import io.gravitee.exchange.api.command.Command;
import io.gravitee.exchange.api.command.CommandAdapter;
import io.gravitee.exchange.api.command.CommandHandler;
import io.gravitee.exchange.api.command.Reply;
import io.gravitee.exchange.api.command.ReplyAdapter;
import io.gravitee.exchange.api.configuration.IdentifyConfiguration;
import io.gravitee.exchange.api.controller.ControllerChannel;
import io.gravitee.exchange.controller.core.channel.SampleChannel;
import io.gravitee.node.api.cache.Cache;
import io.gravitee.node.api.cache.CacheConfiguration;
import io.gravitee.node.api.cache.CacheManager;
import io.gravitee.node.api.cluster.ClusterManager;
import io.gravitee.node.api.cluster.messaging.Topic;
import io.gravitee.node.plugin.cache.standalone.StandaloneCacheManager;
import io.gravitee.node.plugin.cluster.standalone.StandaloneClusterManager;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.Vertx;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.mock.env.MockEnvironment;

/**
 * @author Guillaume LAMIRAND (guillaume.lamirand at graviteesource.com)
 * @author GraviteeSource Team
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
@ExtendWith(VertxExtension.class)
class PrimaryChannelManagerTest {

    private CacheManager cacheManager;
    private ClusterManager clusterManager;
    private MockEnvironment environment;
    private IdentifyConfiguration identifyConfiguration;
    private PrimaryChannelManager cut;
    private Cache<String, String> primaryChannelCache;
    private Topic<PrimaryChannelElectedEvent> primaryChannelElectedEventTopic;
    private Topic<PrimaryChannelEvictedEvent> primaryChannelEvictedEventTopic;
    private Topic<ChannelEvent> primaryChannelEventTopic;

    @BeforeEach
    public void beforeEach(Vertx vertx) throws Exception {
        environment = new MockEnvironment();
        identifyConfiguration = new IdentifyConfiguration(environment);
        cacheManager = new StandaloneCacheManager();
        cacheManager.start();
        clusterManager = new StandaloneClusterManager(vertx);
        clusterManager.start();
        cut = new PrimaryChannelManager(identifyConfiguration, clusterManager, cacheManager);
        cut.start();

        primaryChannelCache =
            cacheManager.getOrCreateCache(
                identifyConfiguration.identifyName(PRIMARY_CHANNEL_CACHE),
                CacheConfiguration.builder().distributed(true).build()
            );
        primaryChannelEventTopic = clusterManager.topic(identifyConfiguration.identifyName(PRIMARY_CHANNEL_EVENTS_TOPIC));
        primaryChannelElectedEventTopic = clusterManager.topic(identifyConfiguration.identifyName(PRIMARY_CHANNEL_EVENTS_ELECTED_TOPIC));
        primaryChannelEvictedEventTopic = clusterManager.topic(identifyConfiguration.identifyName(PRIMARY_CHANNEL_EVENTS_EVICTED_TOPIC));
    }

    @AfterEach
    public void afterEach() {
        primaryChannelCache.clear();
    }

    @Test
    void should_elect_new_primary_channel(VertxTestContext vertxTestContext) throws InterruptedException {
        Checkpoint checkpoint = vertxTestContext.checkpoint();
        primaryChannelElectedEventTopic.addMessageListener(message -> checkpoint.flag());
        cut.sendChannelEvent(new SampleChannel("channelId", "targetId"), true);
        assertThat(vertxTestContext.awaitCompletion(1, TimeUnit.SECONDS)).isTrue();
        assertThat(primaryChannelCache.containsKey("targetId")).isTrue();
        // should contain channel for the target
        cut.primaryChannelBy("targetId").test().awaitDone(1, TimeUnit.SECONDS).assertValue("channelId");
        // should have  primary channel for the target
        cut
            .candidatesChannel("targetId")
            .test()
            .awaitDone(1, TimeUnit.SECONDS)
            .assertValue(candidates -> {
                assertThat(candidates).containsOnly("channelId");
                return true;
            });
    }

    @Test
    void should_elect_then_evict_primary_channel(VertxTestContext vertxTestContext) throws InterruptedException {
        Checkpoint checkpoint = vertxTestContext.checkpoint(2);
        primaryChannelElectedEventTopic.addMessageListener(message -> {
            checkpoint.flag();
            cut.sendChannelEvent(new SampleChannel("channelId", "targetId"), false);
        });
        primaryChannelEvictedEventTopic.addMessageListener(message -> checkpoint.flag());
        cut.sendChannelEvent(new SampleChannel("channelId", "targetId"), true);

        assertThat(vertxTestContext.awaitCompletion(1, TimeUnit.SECONDS)).isTrue();
        // shouldn't contain any channel for the target
        assertThat(primaryChannelCache.containsKey("targetId")).isFalse();
        // shouldn't have any primary channel for the target
        cut.primaryChannelBy("targetId").test().awaitDone(1, TimeUnit.SECONDS).assertNoValues();
        cut.candidatesChannel("targetId").test().awaitDone(1, TimeUnit.SECONDS).assertNoValues();
    }

    @Test
    void should_reelect_after_evicted_primary_channel(VertxTestContext vertxTestContext) throws InterruptedException {
        AtomicInteger channelEventsCount = new AtomicInteger(0);
        Checkpoint checkpoint = vertxTestContext.checkpoint(2);
        primaryChannelElectedEventTopic.addMessageListener(message -> {
            checkpoint.flag();
            if (channelEventsCount.get() == 1) {
                // Make sure to election of primary is done before sending new channel event
                cut.sendChannelEvent(new SampleChannel("channelId2", "targetId"), true);
            }
        });

        primaryChannelEventTopic.addMessageListener(message -> {
            int count = channelEventsCount.incrementAndGet();
            // Make sure to send the following event after the first two have been properly send
            if (count == 2) {
                cut.sendChannelEvent(new SampleChannel("channelId", "targetId"), false);
            }
        });

        cut.sendChannelEvent(new SampleChannel("channelId", "targetId"), true);
        assertThat(vertxTestContext.awaitCompletion(10, TimeUnit.SECONDS)).isTrue();
        assertThat(primaryChannelCache.containsKey("targetId")).isTrue();
        cut.primaryChannelBy("targetId").test().awaitDone(10, TimeUnit.SECONDS).assertValue("channelId2");
        cut
            .candidatesChannel("targetId")
            .test()
            .awaitDone(1, TimeUnit.SECONDS)
            .assertValue(candidates -> {
                assertThat(candidates).containsOnly("channelId2");
                return true;
            });
    }

    @Test
    void should_not_listen_any_more_channel_event_after_stop(VertxTestContext vertxTestContext) throws Exception {
        Checkpoint checkpoint = vertxTestContext.checkpoint();
        primaryChannelElectedEventTopic.addMessageListener(message -> checkpoint.flag());
        primaryChannelEventTopic.addMessageListener(message -> checkpoint.flag());
        cut.stop();
        cut.sendChannelEvent(new SampleChannel("channelId", "targetId"), true);
        assertThat(vertxTestContext.awaitCompletion(10, TimeUnit.SECONDS)).isTrue();
        assertThat(primaryChannelCache.containsKey("targetId")).isFalse();
        cut.primaryChannelBy("targetId").test().awaitDone(10, TimeUnit.SECONDS).assertNoValues();
        cut.candidatesChannel("targetId").test().awaitDone(1, TimeUnit.SECONDS).assertNoValues();
    }
}
