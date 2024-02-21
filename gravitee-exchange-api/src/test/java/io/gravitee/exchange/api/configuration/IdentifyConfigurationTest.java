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
package io.gravitee.exchange.api.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/**
 * @author Guillaume LAMIRAND (guillaume.lamirand at graviteesource.com)
 * @author GraviteeSource Team
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class IdentifyConfigurationTest {

    private MockEnvironment environment;
    private IdentifyConfiguration cut;

    @BeforeEach
    public void beforeEach() {
        environment = new MockEnvironment();
    }

    @Nested
    class DefaultPrefix {

        @BeforeEach
        public void beforeEach() {
            cut = new IdentifyConfiguration(environment);
        }

        @Test
        void should_be_default() {
            assertThat(cut.environment()).isEqualTo(environment);
            assertThat(cut.id()).isEqualTo("exchange");
        }

        @Test
        void should_contain_property() {
            environment.withProperty("exchange.key", "value");
            assertThat(cut.containsProperty("key")).isTrue();
        }

        @Test
        void should_get_property() {
            environment.withProperty("exchange.key", "value");
            assertThat(cut.getProperty("key")).isEqualTo("value");
        }

        @Test
        void should_not_get_property_with_wrong_prefix() {
            environment.withProperty("wrong.key", "value");
            assertThat(cut.getProperty("key")).isNull();
        }

        @Test
        void should_get_custom_property() {
            environment.withProperty("exchange.key", "123");
            assertThat(cut.getProperty("key", Integer.class, 0)).isEqualTo(123);
        }

        @Test
        void should_get_default_value_for_custom_property() {
            assertThat(cut.getProperty("key", Integer.class, 0)).isZero();
        }

        @Test
        void should_return_identify_property() {
            assertThat(cut.identifyProperty("key")).isEqualTo("exchange.key");
        }

        @Test
        void should_return_identify_name() {
            assertThat(cut.identifyName("name")).isEqualTo("exchange-name");
        }
    }

    @Nested
    class CustomPrefix {

        @BeforeEach
        public void beforeEach() {
            cut = new IdentifyConfiguration(environment, "custom");
        }

        @Test
        void should_be_default() {
            assertThat(cut.environment()).isEqualTo(environment);
            assertThat(cut.id()).isEqualTo("custom");
        }

        @Test
        void should_contain_property() {
            environment.withProperty("custom.key", "value");
            assertThat(cut.containsProperty("key")).isTrue();
        }

        @Test
        void should_get_property() {
            environment.withProperty("custom.key", "value");
            assertThat(cut.getProperty("key")).isEqualTo("value");
        }

        @Test
        void should_not_get_property_with_wrong_prefix() {
            environment.withProperty("wrong.key", "value");
            assertThat(cut.getProperty("key")).isNull();
        }

        @Test
        void should_get_custom_property() {
            environment.withProperty("custom.key", "123");
            assertThat(cut.getProperty("key", Integer.class, 0)).isEqualTo(123);
        }

        @Test
        void should_get_default_value_for_custom_property() {
            assertThat(cut.getProperty("key", Integer.class, 0)).isZero();
        }

        @Test
        void should_return_identify_property() {
            assertThat(cut.identifyProperty("key")).isEqualTo("custom.key");
        }

        @Test
        void should_return_identify_name() {
            assertThat(cut.identifyName("name")).isEqualTo("custom-name");
        }
    }
}
