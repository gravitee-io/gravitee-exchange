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
import lombok.SneakyThrows;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
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
            Arguments.of("http://management_api:8072", "management_api", 8072),
            Arguments.of("http://management-api.gravitee.io:8072", "management-api.gravitee.io", 8072),
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
    @SneakyThrows
    void should_create_default_websocket_endpoint(String baseUrl, String host, int port) {
        var endpoint = WebSocketEndpoint.newEndpoint(baseUrl);
        assertThat(endpoint)
            .isPresent()
            .get()
            .extracting(WebSocketEndpoint::getHost, WebSocketEndpoint::getPort)
            .containsExactly(host, port);
    }

    @ParameterizedTest
    @MethodSource("urls")
    @SneakyThrows
    void should_resolve_path(String baseUrl) {
        WebSocketEndpoint endpoint = WebSocketEndpoint.newEndpoint(baseUrl).orElseThrow();
        assertThat(endpoint.resolvePath("/path")).isEqualTo("/path");
    }
}
