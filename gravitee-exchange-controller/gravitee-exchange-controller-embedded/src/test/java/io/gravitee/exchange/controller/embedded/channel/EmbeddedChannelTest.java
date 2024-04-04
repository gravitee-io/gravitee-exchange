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
package io.gravitee.exchange.controller.embedded.channel;

import static org.assertj.core.api.Assertions.assertThat;

import io.gravitee.exchange.api.channel.exception.ChannelInactiveException;
import io.gravitee.exchange.api.channel.exception.ChannelNoReplyException;
import io.gravitee.exchange.api.command.Command;
import io.gravitee.exchange.api.command.CommandAdapter;
import io.gravitee.exchange.api.command.CommandHandler;
import io.gravitee.exchange.api.command.Reply;
import io.gravitee.exchange.api.command.ReplyAdapter;
import io.gravitee.exchange.api.command.primary.PrimaryCommand;
import io.gravitee.exchange.api.command.primary.PrimaryCommandPayload;
import io.gravitee.exchange.api.command.primary.PrimaryReply;
import io.gravitee.exchange.api.command.primary.PrimaryReplyPayload;
import io.reactivex.rxjava3.core.Single;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * @author Guillaume LAMIRAND (guillaume.lamirand at graviteesource.com)
 * @author GraviteeSource Team
 */
@ExtendWith(VertxExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class EmbeddedChannelTest {

    private EmbeddedChannel cut;

    @BeforeEach
    public void beforeEach() {
        cut =
            EmbeddedChannel
                .builder()
                .commandHandlers(new ArrayList<>())
                .commandAdapters(new ArrayList<>())
                .replyAdapters(new ArrayList<>())
                .targetId("targetId")
                .build();
    }

    @Test
    void should_have_random_id() {
        assertThat(cut.id()).isNotNull();
    }

    @Test
    void should_have_target_id() {
        assertThat(cut.targetId()).isEqualTo("targetId");
    }

    @Test
    void should_not_be_active() {
        assertThat(cut.isActive()).isFalse();
    }

    @Test
    void should_be_active_after_initialization() {
        assertThat(cut.isActive()).isFalse();
        cut.initialize().test().assertComplete();
        assertThat(cut.isActive()).isTrue();
    }

    @Test
    void should_be_inactive_after_closing() {
        cut.initialize().test().assertComplete();
        assertThat(cut.isActive()).isTrue();

        cut.close().test().assertComplete();
        assertThat(cut.isActive()).isFalse();
    }

    @Test
    void should_enforce_active_status() {
        assertThat(cut.isActive()).isFalse();
        cut.enforceActiveStatus(true);
        assertThat(cut.isActive()).isTrue();
    }

    @Test
    void should_send_command_to_internal_handlers(VertxTestContext vertxTestContext) throws InterruptedException {
        Checkpoint checkpoint = vertxTestContext.checkpoint();
        cut.addCommandHandlers(
            List.of(
                new CommandHandler<>() {
                    @Override
                    public String supportType() {
                        return PrimaryCommand.COMMAND_TYPE;
                    }

                    @Override
                    public Single<Reply<?>> handle(final Command<?> command) {
                        checkpoint.flag();
                        return Single.just(new PrimaryReply(command.getId(), new PrimaryReplyPayload()));
                    }
                }
            )
        );

        cut.initialize().test().assertComplete();

        PrimaryCommand command = new PrimaryCommand(new PrimaryCommandPayload(true));
        cut.send(command).test().assertValue(reply -> reply.getCommandId().equals(command.getId())).assertComplete();

        assertThat(vertxTestContext.awaitCompletion(10, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void should_decorate_command_and_reply_to_internal_handlers(VertxTestContext vertxTestContext) throws InterruptedException {
        Checkpoint checkpoint = vertxTestContext.checkpoint(3);
        cut =
            EmbeddedChannel
                .builder()
                .commandHandlers(
                    List.of(
                        new CommandHandler<>() {
                            @Override
                            public String supportType() {
                                return PrimaryCommand.COMMAND_TYPE;
                            }

                            @Override
                            public Single<Reply<?>> handle(final Command<?> command) {
                                checkpoint.flag();
                                return Single.just(new PrimaryReply(command.getId(), new PrimaryReplyPayload()));
                            }
                        }
                    )
                )
                .commandAdapters(
                    List.of(
                        new CommandAdapter<>() {
                            @Override
                            public String supportType() {
                                return PrimaryCommand.COMMAND_TYPE;
                            }

                            @Override
                            public Single<Command<?>> adapt(final String targetId, final Command<?> command) {
                                checkpoint.flag();
                                return Single.just(command);
                            }
                        }
                    )
                )
                .replyAdapters(
                    List.of(
                        new ReplyAdapter<>() {
                            @Override
                            public String supportType() {
                                return PrimaryCommand.COMMAND_TYPE;
                            }

                            @Override
                            public Single<Reply<?>> adapt(final String targetId, final Reply<?> reply) {
                                checkpoint.flag();
                                return Single.just(reply);
                            }
                        }
                    )
                )
                .targetId("targetId")
                .build();

        cut.initialize().test().assertComplete();

        PrimaryCommand command = new PrimaryCommand(new PrimaryCommandPayload(true));
        cut.send(command).test().assertValue(reply -> reply.getCommandId().equals(command.getId())).assertComplete();

        assertThat(vertxTestContext.awaitCompletion(10, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void should_return_error_when_sending_command_without_handlers() {
        cut.initialize().test().assertComplete();

        cut.send(new PrimaryCommand(new PrimaryCommandPayload(true))).test().assertNoValues().assertError(ChannelNoReplyException.class);
    }

    @Test
    void should_return_error_when_sending_command_to_inactive_channel() {
        cut.send(new PrimaryCommand(new PrimaryCommandPayload(true))).test().assertError(ChannelInactiveException.class);
    }
}
