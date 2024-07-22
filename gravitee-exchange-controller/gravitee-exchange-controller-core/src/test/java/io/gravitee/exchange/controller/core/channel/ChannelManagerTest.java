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

import static io.gravitee.exchange.controller.core.channel.ChannelManager.CHANNEL_EVENTS_TOPIC;
import static org.assertj.core.api.Assertions.assertThat;

import io.gravitee.exchange.api.command.Command;
import io.gravitee.exchange.api.command.CommandHandler;
import io.gravitee.exchange.api.command.Reply;
import io.gravitee.exchange.api.command.healtcheck.HealthCheckCommand;
import io.gravitee.exchange.api.command.healtcheck.HealthCheckReply;
import io.gravitee.exchange.api.command.healtcheck.HealthCheckReplyPayload;
import io.gravitee.exchange.api.command.hello.HelloCommand;
import io.gravitee.exchange.api.command.hello.HelloCommandPayload;
import io.gravitee.exchange.api.command.hello.HelloReply;
import io.gravitee.exchange.api.command.hello.HelloReplyPayload;
import io.gravitee.exchange.api.command.primary.PrimaryCommand;
import io.gravitee.exchange.api.command.primary.PrimaryReply;
import io.gravitee.exchange.api.command.primary.PrimaryReplyPayload;
import io.gravitee.exchange.api.configuration.IdentifyConfiguration;
import io.gravitee.exchange.api.controller.metrics.ChannelMetric;
import io.gravitee.exchange.api.controller.metrics.TargetChannelsMetric;
import io.gravitee.exchange.controller.core.channel.exception.NoChannelFoundException;
import io.gravitee.exchange.controller.core.channel.primary.ChannelEvent;
import io.gravitee.exchange.controller.core.channel.primary.PrimaryChannelElectedEvent;
import io.gravitee.exchange.controller.core.channel.primary.PrimaryChannelManager;
import io.gravitee.node.api.cache.CacheManager;
import io.gravitee.node.api.cluster.ClusterManager;
import io.gravitee.node.api.cluster.messaging.Topic;
import io.gravitee.node.plugin.cache.standalone.StandaloneCacheManager;
import io.gravitee.node.plugin.cluster.standalone.StandaloneClusterManager;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.plugins.RxJavaPlugins;
import io.reactivex.rxjava3.schedulers.TestScheduler;
import io.vertx.core.Vertx;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.assertj.core.groups.Tuple;
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
class ChannelManagerTest {

    private CacheManager cacheManager;
    private ClusterManager clusterManager;
    private MockEnvironment environment;
    private IdentifyConfiguration identifyConfiguration;
    private ChannelManager cut;
    private Topic<ChannelEvent> channelEventTopic;
    private Topic<PrimaryChannelElectedEvent> primaryChannelElectedEventTopic;

    @BeforeEach
    public void beforeEach(Vertx vertx) throws Exception {
        environment = new MockEnvironment();
        identifyConfiguration = new IdentifyConfiguration(environment);
        cacheManager = new StandaloneCacheManager();
        cacheManager.start();
        clusterManager = new StandaloneClusterManager(vertx);
        clusterManager.start();
        cut = new ChannelManager(identifyConfiguration, clusterManager, cacheManager);
        cut.start();
        channelEventTopic = clusterManager.topic(identifyConfiguration.identifyName(CHANNEL_EVENTS_TOPIC));
        primaryChannelElectedEventTopic =
            clusterManager.topic(identifyConfiguration.identifyName(PrimaryChannelManager.PRIMARY_CHANNEL_ELECTED_EVENTS_TOPIC));
    }

    @Test
    void should_register_new_channel(VertxTestContext vertxTestContext) throws InterruptedException {
        SampleChannel sampleChannel = new SampleChannel("channelId", "targetId");
        Checkpoint checkpoint = vertxTestContext.checkpoint();
        channelEventTopic.addMessageListener(message -> {
            if (message.content().active()) {
                checkpoint.flag();
            }
        });
        cut.register(sampleChannel).test().awaitDone(10, TimeUnit.SECONDS);
        assertThat(vertxTestContext.awaitCompletion(10, TimeUnit.SECONDS)).isTrue();

        assertThat(cut.getChannelById("channelId")).isNotNull();
    }

    @Test
    void should_receive_primary_command_after_register_new_primary_channel(VertxTestContext vertxTestContext) throws InterruptedException {
        Checkpoint checkpoint = vertxTestContext.checkpoint();
        SampleChannel sampleChannel = new SampleChannel("channelId", "targetId");
        sampleChannel.addCommandHandlers(
            List.of(
                new CommandHandler<>() {
                    @Override
                    public Single<Reply<?>> handle(final Command<?> command) {
                        return Single.fromCallable(() -> {
                            checkpoint.flag();
                            return new PrimaryReply(command.getId(), new PrimaryReplyPayload());
                        });
                    }

                    @Override
                    public String supportType() {
                        return PrimaryCommand.COMMAND_TYPE;
                    }
                }
            )
        );
        cut.register(sampleChannel).test().awaitDone(10, TimeUnit.SECONDS);
        assertThat(vertxTestContext.awaitCompletion(10, TimeUnit.SECONDS)).isTrue();

        assertThat(cut.getChannelById("channelId")).isNotNull();
    }

    @Test
    void should_receive_healthCheck_command(VertxTestContext vertxTestContext) {
        try {
            TestScheduler testScheduler = new TestScheduler();
            RxJavaPlugins.setComputationSchedulerHandler(s -> testScheduler);
            // Restart cut to apply test scheduler
            cut.stop();
            cut.start();

            Checkpoint checkpoint = vertxTestContext.checkpoint();
            SampleChannel sampleChannel = new SampleChannel("channelId", "targetId");
            sampleChannel.addCommandHandlers(
                List.of(
                    new CommandHandler<>() {
                        @Override
                        public Single<Reply<?>> handle(final Command<?> command) {
                            return Single.fromCallable(() -> {
                                checkpoint.flag();
                                return new HealthCheckReply(command.getId(), new HealthCheckReplyPayload(true, null));
                            });
                        }

                        @Override
                        public String supportType() {
                            return HealthCheckCommand.COMMAND_TYPE;
                        }
                    }
                )
            );
            cut.register(sampleChannel).test().awaitDone(10, TimeUnit.SECONDS);
            testScheduler.advanceTimeBy(30, TimeUnit.SECONDS);
            assertThat(vertxTestContext.awaitCompletion(10, TimeUnit.SECONDS)).isTrue();
            assertThat(cut.getChannelById("channelId")).isNotNull();
        } catch (Exception e) {
            RxJavaPlugins.reset();
        }
    }

    @Test
    void should_not_register_new_channel_if_initialization_failed() {
        SampleChannel sampleChannel = new SampleChannel("channelId", "targetId");
        sampleChannel.setInitialize(Completable.error(new RuntimeException()));
        cut.register(sampleChannel).test().awaitDone(10, TimeUnit.SECONDS);

        assertThat(cut.getChannelById("channelId")).isNull();
    }

    @Test
    void should_unregister_channel(VertxTestContext vertxTestContext) throws InterruptedException {
        SampleChannel sampleChannel = new SampleChannel("channelId", "targetId");
        Checkpoint checkpoint = vertxTestContext.checkpoint();
        channelEventTopic.addMessageListener(message -> {
            if (!message.content().active()) {
                checkpoint.flag();
            }
        });
        cut
            .register(sampleChannel)
            .andThen(
                Completable.fromRunnable(() -> {
                    assertThat(cut.getChannelById("channelId")).isNotNull();
                })
            )
            .andThen(cut.unregister(sampleChannel))
            .test()
            .awaitDone(10, TimeUnit.SECONDS);
        assertThat(vertxTestContext.awaitCompletion(10, TimeUnit.SECONDS)).isTrue();

        assertThat(cut.getChannelById("channelId")).isNull();
    }

    @Test
    void should_unregister_channel_even_if_close_failed() {
        SampleChannel sampleChannel = new SampleChannel("channelId", "targetId");
        sampleChannel.setClose(Completable.error(new RuntimeException()));
        cut
            .register(sampleChannel)
            .andThen(
                Completable.fromRunnable(() -> {
                    assertThat(cut.getChannelById("channelId")).isNotNull();
                })
            )
            .andThen(cut.unregister(sampleChannel))
            .test()
            .awaitDone(10, TimeUnit.SECONDS);

        assertThat(cut.getChannelById("channelId")).isNull();
    }

    @Test
    void should_return_metrics_for_target(VertxTestContext vertxTestContext) throws InterruptedException {
        Checkpoint checkpoint = vertxTestContext.checkpoint(6); // 3 register, 1 manuel event and 2 election
        channelEventTopic.addMessageListener(message -> checkpoint.flag());
        primaryChannelElectedEventTopic.addMessageListener(message -> checkpoint.flag());

        SampleChannel sampleChannel = new SampleChannel("channelId", "targetId");
        SampleChannel sampleChannel2 = new SampleChannel("channelId2", "targetId");
        SampleChannel sampleChannel3 = new SampleChannel("channelId3", "targetId2");
        cut
            .register(sampleChannel)
            .andThen(cut.register(sampleChannel2))
            .andThen(cut.register(sampleChannel3))
            .andThen(channelEventTopic.rxPublish(ChannelEvent.builder().channelId("channelId2").targetId("targetId").active(false).build()))
            .test()
            .awaitDone(10, TimeUnit.SECONDS);
        assertThat(vertxTestContext.awaitCompletion(10, TimeUnit.SECONDS)).isTrue();

        cut
            .channelsMetricsByTarget()
            .toList()
            .test()
            .awaitDone(10, TimeUnit.SECONDS)
            .assertValue(targetMetrics -> {
                for (TargetChannelsMetric targetChannelsMetric : targetMetrics) {
                    if (targetChannelsMetric.id().equals("targetId")) {
                        assertThat(targetChannelsMetric.channels())
                            .extracting(ChannelMetric::id, ChannelMetric::primary, ChannelMetric::active)
                            .containsOnly(Tuple.tuple("channelId", true, true), Tuple.tuple("channelId2", false, false));
                    } else if (targetChannelsMetric.id().equals("targetId2")) {
                        assertThat(targetChannelsMetric.channels())
                            .extracting(ChannelMetric::id, ChannelMetric::primary, ChannelMetric::active)
                            .containsOnly(Tuple.tuple("channelId3", true, true));
                    } else {
                        return false;
                    }
                }
                return true;
            });
    }

    @Test
    void should_return_metrics_for_channel(VertxTestContext vertxTestContext) throws InterruptedException {
        Checkpoint checkpoint = vertxTestContext.checkpoint(6); // 3 register, 1 manuel event and 2 election
        channelEventTopic.addMessageListener(message -> checkpoint.flag());
        primaryChannelElectedEventTopic.addMessageListener(message -> checkpoint.flag());

        SampleChannel sampleChannel = new SampleChannel("channelId", "targetId");
        SampleChannel sampleChannel2 = new SampleChannel("channelId2", "targetId");
        SampleChannel sampleChannel3 = new SampleChannel("channelId3", "targetId2");
        cut
            .register(sampleChannel)
            .andThen(cut.register(sampleChannel2))
            .andThen(cut.register(sampleChannel3))
            .andThen(channelEventTopic.rxPublish(ChannelEvent.builder().channelId("channelId2").targetId("targetId").active(false).build()))
            .test()
            .awaitDone(10, TimeUnit.SECONDS);
        assertThat(vertxTestContext.awaitCompletion(10, TimeUnit.SECONDS)).isTrue();

        cut
            .channelsMetricsForTarget("targetId")
            .toList()
            .test()
            .awaitDone(10, TimeUnit.SECONDS)
            .assertValue(channelMetrics -> {
                assertThat(channelMetrics)
                    .extracting(ChannelMetric::id, ChannelMetric::active, ChannelMetric::primary)
                    .containsOnly(Tuple.tuple("channelId", true, true), Tuple.tuple("channelId2", false, false));
                return true;
            });
    }

    @Test
    void should_send_command_to_channel(VertxTestContext vertxTestContext) throws InterruptedException {
        Checkpoint checkpoint = vertxTestContext.checkpoint(2);
        channelEventTopic.addMessageListener(message -> checkpoint.flag());

        SampleChannel sampleChannel = new SampleChannel("channelId", "targetId");
        sampleChannel.addCommandHandlers(
            List.of(
                new CommandHandler<>() {
                    @Override
                    public Single<Reply<?>> handle(final Command<?> command) {
                        return Single.fromCallable(() -> {
                            checkpoint.flag();
                            return new HelloReply(command.getId(), new HelloReplyPayload("targetId"));
                        });
                    }

                    @Override
                    public String supportType() {
                        return HelloCommand.COMMAND_TYPE;
                    }
                }
            )
        );
        SampleChannel sampleChannel2 = new SampleChannel("channelId2", "targetId", false);
        cut
            .register(sampleChannel)
            .andThen(cut.send(new HelloCommand(new HelloCommandPayload()), "targetId"))
            .test()
            .awaitDone(10, TimeUnit.SECONDS);
        assertThat(vertxTestContext.awaitCompletion(10, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void should_return_error_when_sending_command_without_channel() {
        cut.send(new HelloCommand(new HelloCommandPayload()), "targetId").test().assertError(NoChannelFoundException.class);
    }
}
