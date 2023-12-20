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
package io.gravitee.exchange.connector.websocket.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * @author Guillaume LAMIRAND (guillaume.lamirand at graviteesource.com)
 * @author GraviteeSource Team
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class WebSocketEndpointTest {

    private static Stream<Arguments> urls() {
        return Stream.of(
            // URL / host / port / root path
            Arguments.of("http://localhost:8062", "localhost", 8062),
            Arguments.of("http://localhost:8062/", "localhost", 8062),
            Arguments.of("https://localhost:8063", "localhost", 8063),
            Arguments.of("https://localhost:8063/", "localhost", 8063),
            Arguments.of("https://localhost:8064/root", "localhost", 8064),
            Arguments.of("https://localhost:8064/root/", "localhost", 8064)
        );
    }

    @ParameterizedTest
    @MethodSource("urls")
    void should_create_default_websocket_endpoint(String baseUrl, String host, int port) {
        WebSocketEndpoint endpoint = new WebSocketEndpoint(baseUrl, -1);
        assertThat(endpoint.getHost()).isEqualTo(host);
        assertThat(endpoint.getPort()).isEqualTo(port);
        assertThat(endpoint.getMaxRetryCount()).isEqualTo(5);
        assertThat(endpoint.getRetryCount()).isZero();
    }

    @ParameterizedTest
    @MethodSource("urls")
    void should_resolve_path(String baseUrl) {
        WebSocketEndpoint endpoint = new WebSocketEndpoint(baseUrl, -1);
        assertThat(endpoint.resolvePath("/path")).isEqualTo("/path");
    }

    @Test
    void should_create_websocket_endpoint_with_max_retry() {
        WebSocketEndpoint endpoint = new WebSocketEndpoint("http://localhost:8062", 10);
        assertThat(endpoint.getMaxRetryCount()).isEqualTo(10);
        assertThat(endpoint.getRetryCount()).isZero();
    }

    @Test
    void should_increment_retry_counter() {
        WebSocketEndpoint endpoint = new WebSocketEndpoint("http://localhost:8062", -1);
        assertThat(endpoint.getRetryCount()).isZero();
        assertThat(endpoint.getMaxRetryCount()).isEqualTo(5);
        endpoint.incrementRetryCount();
        assertThat(endpoint.getRetryCount()).isEqualTo(1);
    }

    @Test
    void should_resent__retry_counter() {
        WebSocketEndpoint endpoint = new WebSocketEndpoint("http://localhost:8062", -1);
        endpoint.incrementRetryCount();
        assertThat(endpoint.getRetryCount()).isEqualTo(1);
        endpoint.resetRetryCount();
        assertThat(endpoint.getRetryCount()).isZero();
    }

    @Test
    void should_be_removable_when_retry_is_higher_than_max() {
        WebSocketEndpoint endpoint = new WebSocketEndpoint("http://localhost:8062", 1);
        endpoint.incrementRetryCount();
        assertThat(endpoint.isRemovable()).isFalse();
        endpoint.incrementRetryCount();
        assertThat(endpoint.isRemovable()).isTrue();
    }
}
