package io.gravitee.exchange.connector.websocket.client;/*
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.groups.Tuple.tuple;

import io.gravitee.exchange.api.configuration.IdentifyConfiguration;
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
class WebSocketClientConfigurationTest {

    private MockEnvironment environment;

    @BeforeEach
    void beforeEach() {
        environment = new MockEnvironment();
    }

    @Nested
    class DefaultPrefix {

        protected WebSocketClientConfiguration cut;
        protected String prefix;

        @BeforeEach
        void beforeEach() {
            if (prefix == null) {
                IdentifyConfiguration identifyConfiguration = new IdentifyConfiguration(environment);
                prefix = identifyConfiguration.id();
                cut = new WebSocketClientConfiguration(identifyConfiguration);
            } else {
                cut = new WebSocketClientConfiguration(new IdentifyConfiguration(environment, prefix));
            }
        }

        @Test
        void should_return_headers() {
            environment
                .withProperty("%s.connector.ws.headers[0].name".formatted(prefix), "name")
                .withProperty("%s.connector.ws.headers[0].value".formatted(prefix), "value")
                .withProperty("%s.connector.ws.headers[1].name".formatted(prefix), "name1")
                .withProperty("%s.connector.ws.headers[1].value".formatted(prefix), "value1");
            assertThat(cut.headers()).containsOnly(entry("name", "value"), entry("name1", "value1"));
        }

        @Test
        void should_return_empty_headers_without_configuration() {
            assertThat(cut.headers()).isEmpty();
        }

        @Test
        void should_return_trust_all() {
            environment.withProperty("%s.connector.ws.ssl.trustAll".formatted(prefix), "true");
            assertThat(cut.trustAll()).isTrue();
        }

        @Test
        void should_return_default_trust_all_without_configuration() {
            assertThat(cut.trustAll()).isFalse();
        }

        @Test
        void should_return_verify_host() {
            environment.withProperty("%s.connector.ws.ssl.verifyHost".formatted(prefix), "false");
            assertThat(cut.verifyHost()).isFalse();
        }

        @Test
        void should_return_default_verify_host_without_configuration() {
            assertThat(cut.verifyHost()).isTrue();
        }

        @Test
        void should_return_key_store_type() {
            environment.withProperty("%s.connector.ws.ssl.keystore.type".formatted(prefix), "PEM");
            assertThat(cut.keyStoreType()).isEqualTo("PEM");
        }

        @Test
        void should_return_null_key_store_type_without_configuration() {
            assertThat(cut.keyStoreType()).isNull();
        }

        @Test
        void should_return_key_store_path() {
            environment.withProperty("%s.connector.ws.ssl.keystore.path".formatted(prefix), "/path");
            assertThat(cut.keyStorePath()).isEqualTo("/path");
        }

        @Test
        void should_return_null_key_store_path_without_configuration() {
            assertThat(cut.keyStorePath()).isNull();
        }

        @Test
        void should_return_key_store_password() {
            environment.withProperty("%s.connector.ws.ssl.keystore.password".formatted(prefix), "pwd");
            assertThat(cut.keyStorePassword()).isEqualTo("pwd");
        }

        @Test
        void should_return_null_key_store_password_without_configuration() {
            assertThat(cut.keyStorePassword()).isNull();
        }

        @Test
        void should_return_trust_store_type() {
            environment.withProperty("%s.connector.ws.ssl.truststore.type".formatted(prefix), "PEM");
            assertThat(cut.trustStoreType()).isEqualTo("PEM");
        }

        @Test
        void should_return_null_trust_store_type_without_configuration() {
            assertThat(cut.trustStoreType()).isNull();
        }

        @Test
        void should_return_trust_store_path() {
            environment.withProperty("%s.connector.ws.ssl.truststore.path".formatted(prefix), "/path");
            assertThat(cut.trustStorePath()).isEqualTo("/path");
        }

        @Test
        void should_return_null_trust_store_path_without_configuration() {
            assertThat(cut.trustStorePath()).isNull();
        }

        @Test
        void should_return_trust_store_password() {
            environment.withProperty("%s.connector.ws.ssl.truststore.password".formatted(prefix), "pwd");
            assertThat(cut.trustStorePassword()).isEqualTo("pwd");
        }

        @Test
        void should_return_null_trust_store_password_without_configuration() {
            assertThat(cut.trustStorePassword()).isNull();
        }

        @Test
        void should_return_max_web_socket_frame_size() {
            environment.withProperty("%s.connector.ws.maxWebSocketFrameSize".formatted(prefix), "123456");
            assertThat(cut.maxWebSocketFrameSize()).isEqualTo(123456);
        }

        @Test
        void should_return_default_max_web_socket_frame_size_without_configuration() {
            assertThat(cut.maxWebSocketFrameSize()).isEqualTo(65536);
        }

        @Test
        void should_return_max_web_socket_message_size() {
            environment.withProperty("%s.connector.ws.maxWebSocketMessageSize".formatted(prefix), "123456");
            assertThat(cut.maxWebSocketMessageSize()).isEqualTo(123456);
        }

        @Test
        void should_return_default_max_web_socket_message_size_without_configuration() {
            assertThat(cut.maxWebSocketMessageSize()).isEqualTo(13107200);
        }

        @Test
        void should_return_endpoints() {
            environment
                .withProperty("%s.connector.ws.endpoints[0]".formatted(prefix), "http://endpoint:1234")
                .withProperty("%s.connector.ws.endpoints[1]".formatted(prefix), "http://endpoint2:5678");
            assertThat(cut.endpoints()).extracting("host", "port").contains(tuple("endpoint", 1234), tuple("endpoint2", 5678));
        }

        @Test
        void should_return_empty_endpoints_without_configuration() {
            assertThat(cut.endpoints()).isEmpty();
        }

        @Test
        void should_return_http_client_no_proxy_configuration() {
            assertThat(cut.isProxyConfigured()).isFalse();
        }

        @Test
        void should_return_http_client_proxy_configuration() {
            environment
                .withProperty("%s.connector.ws.proxy.enabled".formatted(prefix), "true")
                .withProperty("%s.connector.ws.proxy.type".formatted(prefix), "HTTP")
                .withProperty("%s.connector.ws.proxy.host".formatted(prefix), "proxy.tld")
                .withProperty("%s.connector.ws.proxy.port".formatted(prefix), "123")
                .withProperty("%s.connector.ws.proxy.username".formatted(prefix), "bob")
                .withProperty("%s.connector.ws.proxy.password".formatted(prefix), "bob_pwd");
            assertThat(cut.isProxyConfigured()).isTrue();
            assertThat(cut.proxyType()).isEqualTo("HTTP");
            assertThat(cut.proxyHost()).isEqualTo("proxy.tld");
            assertThat(cut.proxyPort()).isEqualTo(123);
            assertThat(cut.proxyUsername()).isEqualTo("bob");
            assertThat(cut.proxyPassword()).isEqualTo("bob_pwd");
        }
    }

    @Nested
    class CustomPrefix extends DefaultPrefix {

        @BeforeEach
        void beforeEach() {
            prefix = "custom";
            super.beforeEach();
        }
    }
}
