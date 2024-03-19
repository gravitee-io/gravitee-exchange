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
package io.gravitee.exchange.connector.core;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.gravitee.exchange.api.connector.ExchangeConnector;
import io.reactivex.rxjava3.core.Completable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * @author Guillaume LAMIRAND (guillaume.lamirand at graviteesource.com)
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class DefaultExchangeConnectorManagerTest {

    private DefaultExchangeConnectorManager cut;

    @Mock
    private ExchangeConnector exchangeConnector;

    @BeforeEach
    public void beforeEach() {
        cut = new DefaultExchangeConnectorManager();
        lenient().when(exchangeConnector.targetId()).thenReturn("targetId");
        lenient().when(exchangeConnector.initialize()).thenReturn(Completable.complete());
        lenient().when(exchangeConnector.close()).thenReturn(Completable.complete());
    }

    @Nested
    class Get {

        @Test
        void should_not_return_any_connectors_with_unknown_id() {
            cut.register(exchangeConnector).andThen(cut.get("unknown")).test().assertNoValues().assertComplete();
        }

        @Test
        void should_return_registered_connector() {
            cut
                .register(exchangeConnector)
                .andThen(cut.get(exchangeConnector.targetId()))
                .test()
                .assertValue(exchangeConnector)
                .assertComplete();
        }
    }

    @Nested
    class Register {

        @Test
        void should_register() {
            cut
                .register(exchangeConnector)
                .andThen(cut.get(exchangeConnector.targetId()))
                .test()
                .assertValue(exchangeConnector)
                .assertComplete();
            verify(exchangeConnector).initialize();
            verify(exchangeConnector).addCommandHandlers(any());
        }

        @Test
        void should_unregister_if_connector_initialization_failed_during_registration() {
            when(exchangeConnector.initialize()).thenReturn(Completable.error(new RuntimeException()));
            cut.register(exchangeConnector).test().assertError(RuntimeException.class);
            cut.get(exchangeConnector.targetId()).test().assertNoValues().assertComplete();
            verify(exchangeConnector).initialize();
            verify(exchangeConnector).close();
        }
    }

    @Nested
    class Unregister {

        @Test
        void should_unregister() {
            cut
                .register(exchangeConnector)
                .andThen(cut.unregister(exchangeConnector))
                .andThen(cut.get(exchangeConnector.targetId()))
                .test()
                .assertNoValues()
                .assertComplete();
            verify(exchangeConnector).close();
        }

        @Test
        void should_unregister_even_if_connector_closing_failed() {
            when(exchangeConnector.close()).thenReturn(Completable.error(new RuntimeException()));

            cut.register(exchangeConnector).andThen(cut.unregister(exchangeConnector)).test().assertError(RuntimeException.class);
            cut.get(exchangeConnector.targetId()).test().assertNoValues().assertComplete();
            verify(exchangeConnector).close();
        }
    }
}
