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
package io.gravitee.exchange.connector.embedded;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.gravitee.exchange.api.command.Command;
import io.gravitee.exchange.api.command.CommandHandler;
import io.gravitee.exchange.api.command.Reply;
import io.gravitee.exchange.api.connector.ConnectorChannel;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.plugins.RxJavaPlugins;
import io.reactivex.rxjava3.schedulers.TestScheduler;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * @author Guillaume LAMIRAND (guillaume.lamirand at graviteesource.com)
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class EmbeddedExchangeConnectorTest {

    @Mock
    private ConnectorChannel connectorChannel;

    private EmbeddedExchangeConnector cut;

    @BeforeEach
    public void beforeEach() {
        cut = EmbeddedExchangeConnector.builder().connectorChannel(connectorChannel).build();
    }

    @Nested
    class Primary {

        @Test
        void should_be_primary_by_default() {
            EmbeddedExchangeConnector exchangeConnector = new EmbeddedExchangeConnector();
            assertThat(exchangeConnector.isPrimary()).isTrue();
        }

        @Test
        void should_be_primary_using_builder() {
            EmbeddedExchangeConnector exchangeConnector = EmbeddedExchangeConnector.builder().build();
            assertThat(exchangeConnector.isPrimary()).isTrue();
        }

        @ParameterizedTest
        @ValueSource(booleans = { true, false })
        void should_set_primary_using_builder(final boolean primary) {
            EmbeddedExchangeConnector exchangeConnector = EmbeddedExchangeConnector.builder().build();
            exchangeConnector.setPrimary(primary);
            assertThat(exchangeConnector.isPrimary()).isEqualTo(primary);
        }
    }

    @Nested
    class DelegateChannel {

        @Test
        void should_delegate_initialize_to_channel() {
            when(connectorChannel.initialize()).thenReturn(Completable.complete());
            cut.initialize().test().assertComplete();
            verify(connectorChannel).initialize();
        }

        @Test
        void should_delegate_close_to_channel() {
            when(connectorChannel.close()).thenReturn(Completable.complete());
            cut.close().test().assertComplete();
            verify(connectorChannel).close();
        }

        @Test
        void should_delegate_target_id_to_channel() {
            when(connectorChannel.targetId()).thenReturn("targetId");
            assertThat(cut.targetId()).isEqualTo("targetId");
            verify(connectorChannel).targetId();
        }

        @Test
        void should_delegate_add_command_handlers_to_channel() {
            List<CommandHandler<? extends Command<?>, ? extends Reply<?>>> commandHandlers = List.of();
            cut.addCommandHandlers(commandHandlers);
            verify(connectorChannel).addCommandHandlers(commandHandlers);
        }
    }

    @Nested
    class Commands {

        @Mock
        private Command<?> command;

        @Mock
        private Reply<?> reply;

        private TestScheduler testScheduler;

        @BeforeEach
        public void beforeEach() {
            testScheduler = new TestScheduler();
            // set calls to Schedulers.computation() to use our test scheduler
            RxJavaPlugins.setComputationSchedulerHandler(ignore -> testScheduler);
        }

        @AfterEach
        public void after() {
            // reset it
            RxJavaPlugins.setComputationSchedulerHandler(null);
        }

        @Test
        void should_send_commands_to_channel() {
            when(connectorChannel.send(any())).thenReturn(Single.just(reply));
            EmbeddedExchangeConnector cut = EmbeddedExchangeConnector.builder().connectorChannel(connectorChannel).build();
            cut.sendCommand(command).test().assertValue(reply);

            verify(connectorChannel).send(command);
        }
    }
}
