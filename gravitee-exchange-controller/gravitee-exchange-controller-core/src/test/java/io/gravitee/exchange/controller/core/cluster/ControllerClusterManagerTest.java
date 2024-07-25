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

import static org.assertj.core.api.Assertions.assertThat;

import io.gravitee.exchange.api.configuration.IdentifyConfiguration;
import io.gravitee.exchange.controller.core.channel.ChannelManager;
import io.gravitee.exchange.controller.core.channel.SampleChannel;
import io.gravitee.exchange.controller.core.channel.primary.ChannelEvent;
import io.gravitee.node.api.cache.CacheManager;
import io.gravitee.node.api.cluster.Member;
import io.gravitee.node.api.cluster.MemberListener;
import io.gravitee.node.api.cluster.messaging.Queue;
import io.gravitee.node.plugin.cache.standalone.StandaloneCacheManager;
import io.gravitee.node.plugin.cluster.standalone.StandaloneMember;
import io.reactivex.rxjava3.core.Completable;
import io.vertx.core.Vertx;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
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
class ControllerClusterManagerTest {

    private CacheManager cacheManager;
    private MultiMemberStandaloneClusterManager clusterManager;
    private MockEnvironment environment;
    private IdentifyConfiguration identifyConfiguration;
    private ControllerClusterManager cut;
    private MemberListener memberListener;

    @BeforeEach
    public void beforeEach(Vertx vertx) throws Exception {
        environment =
            new MockEnvironment()
                .withProperty("exchange.controller.auto-rebalancing.enabled", "true")
                .withProperty("exchange.controller.auto-rebalancing.delay", "1000")
                .withProperty("exchange.controller.auto-rebalancing.unit", "MILLISECONDS");
        identifyConfiguration = new IdentifyConfiguration(environment);
        cacheManager = new StandaloneCacheManager();
        cacheManager.start();
        clusterManager = new MultiMemberStandaloneClusterManager(vertx);
        clusterManager.start();
        cut = new ControllerClusterManager(identifyConfiguration, clusterManager, cacheManager);
        cut.start();
    }

    @AfterEach
    public void afterEach() {
        if (memberListener != null) {
            clusterManager.removeMemberListener(memberListener);
        }
    }

    @Test
    void should_not_rebalance_with_only_member(VertxTestContext vertxTestContext) throws InterruptedException {
        Checkpoint checkpoint = vertxTestContext.checkpoint();
        clusterManager.addMemberListener(memberListener(checkpoint));

        SampleChannel sampleChannel = new SampleChannel("channelId", "targetId", true);
        sampleChannel.setClose(Completable.fromRunnable(checkpoint::flag));
        SampleChannel sampleChannel2 = new SampleChannel("channelId2", "targetId", true);
        sampleChannel2.setClose(Completable.fromRunnable(checkpoint::flag));
        cut
            .register(sampleChannel)
            .andThen(cut.register(sampleChannel2))
            .andThen(Completable.fromRunnable(() -> clusterManager.addMember(new StandaloneMember())))
            .test()
            .awaitDone(10, TimeUnit.SECONDS);

        assertThat(vertxTestContext.awaitCompletion(10, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void should_rebalance_channels_when_new_member_join(VertxTestContext vertxTestContext) throws InterruptedException {
        Checkpoint checkpoint = vertxTestContext.checkpoint(2); //2 for new member
        memberListener = memberListener(checkpoint);
        clusterManager.addMemberListener(memberListener);
        clusterManager.addMember(new StandaloneMember());
        SampleChannel sampleChannel = new SampleChannel("channelId", "targetId");
        sampleChannel.setClose(Completable.fromRunnable(checkpoint::flag));
        SampleChannel sampleChannel2 = new SampleChannel("channelId2", "targetId");
        sampleChannel2.setClose(Completable.fromRunnable(checkpoint::flag));
        cut
            .register(sampleChannel)
            .andThen(cut.register(sampleChannel2))
            .andThen(Completable.fromRunnable(() -> clusterManager.addMember(new StandaloneMember())))
            .test()
            .awaitDone(10, TimeUnit.SECONDS);
        assertThat(vertxTestContext.awaitCompletion(10, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void should_rebalance_channels_when_new_member_join_and_only_1_channels_is_active(VertxTestContext vertxTestContext)
        throws InterruptedException {
        Checkpoint checkpoint = vertxTestContext.checkpoint(2); //2 for new member
        memberListener = memberListener(checkpoint);
        clusterManager.addMemberListener(memberListener);
        clusterManager.addMember(new StandaloneMember());
        SampleChannel sampleChannel = new SampleChannel("channelId", "targetId");
        sampleChannel.setClose(Completable.fromRunnable(checkpoint::flag));
        SampleChannel sampleChannel2 = new SampleChannel("channelId2", "targetId", false);
        sampleChannel2.setClose(Completable.fromRunnable(checkpoint::flag));
        cut
            .register(sampleChannel)
            .andThen(cut.register(sampleChannel2))
            .andThen(Completable.fromRunnable(() -> clusterManager.addMember(new StandaloneMember())))
            .test()
            .awaitDone(10, TimeUnit.SECONDS);
        assertThat(vertxTestContext.awaitCompletion(10, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void should_rebalance_channel_without_pending_commands_when_new_member_join(VertxTestContext vertxTestContext)
        throws InterruptedException {
        Checkpoint checkpoint = vertxTestContext.checkpoint(2); //2 for new member
        memberListener = memberListener(checkpoint);
        clusterManager.addMemberListener(memberListener);
        clusterManager.addMember(new StandaloneMember());
        SampleChannel sampleChannel = new SampleChannel("channelId", "targetId", true, true);
        SampleChannel sampleChannel2 = new SampleChannel("channelId2", "targetId", false, false);
        sampleChannel2.setClose(Completable.fromRunnable(checkpoint::flag));
        cut
            .register(sampleChannel)
            .andThen(cut.register(sampleChannel2))
            .andThen(Completable.fromRunnable(() -> clusterManager.addMember(new StandaloneMember())))
            .test()
            .awaitDone(10, TimeUnit.SECONDS);
        assertThat(vertxTestContext.awaitCompletion(10, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void should_rebalance_channels_only_once_when_multiple_member_join_before_delay(VertxTestContext vertxTestContext)
        throws InterruptedException {
        Checkpoint checkpoint = vertxTestContext.checkpoint(5); // 5 for new members
        memberListener = memberListener(checkpoint);
        clusterManager.addMemberListener(memberListener);
        clusterManager.addMember(new StandaloneMember());
        SampleChannel sampleChannel = new SampleChannel("channelId", "targetId", true);
        sampleChannel.setClose(Completable.fromRunnable(checkpoint::flag));
        SampleChannel sampleChannel2 = new SampleChannel("channelId2", "targetId", true);
        sampleChannel2.setClose(Completable.fromRunnable(checkpoint::flag));
        cut
            .register(sampleChannel)
            .andThen(cut.register(sampleChannel2))
            .andThen(Completable.fromRunnable(() -> clusterManager.addMember(new StandaloneMember())))
            .andThen(Completable.fromRunnable(() -> clusterManager.addMember(new StandaloneMember())))
            .andThen(Completable.fromRunnable(() -> clusterManager.addMember(new StandaloneMember())))
            .andThen(Completable.fromRunnable(() -> clusterManager.addMember(new StandaloneMember())))
            .test()
            .awaitDone(10, TimeUnit.SECONDS);
        assertThat(vertxTestContext.awaitCompletion(10, TimeUnit.SECONDS)).isTrue();
    }

    private static MemberListener memberListener(final Checkpoint checkpoint) {
        return new MemberListener() {
            @Override
            public void onMemberAdded(final Member member) {
                checkpoint.flag();
            }
        };
    }
}
