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
package io.gravitee.exchange.controller.core.management.channels;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.gravitee.exchange.api.batch.BatchStatus;
import io.gravitee.exchange.api.configuration.IdentifyConfiguration;
import io.gravitee.exchange.api.controller.ExchangeController;
import io.gravitee.exchange.api.controller.metrics.BatchMetric;
import io.gravitee.exchange.api.controller.metrics.ChannelMetric;
import io.gravitee.exchange.controller.core.management.AbstractMetricEndpointTest;
import io.gravitee.exchange.controller.core.management.batch.ControllerBatchMetricsEndpoint;
import io.gravitee.exchange.controller.core.management.channel.ControllerChannelMetricsEndpoint;
import io.gravitee.exchange.controller.core.management.error.ManagementError;
import io.reactivex.rxjava3.core.Maybe;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.Json;
import io.vertx.junit5.VertxTestContext;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.mock.env.MockEnvironment;

/**
 * @author Guillaume LAMIRAND (guillaume.lamirand at graviteesource.com)
 * @author GraviteeSource Team
 */
class ControllerChannelMetricsEndpointTest extends AbstractMetricEndpointTest {

    @Mock
    private ExchangeController exchangeController;

    private ControllerChannelMetricsEndpoint cut;

    @BeforeEach
    public void beforeEach() {
        cut = new ControllerChannelMetricsEndpoint(new IdentifyConfiguration(new MockEnvironment()), exchangeController);
        mainRouter.route(HttpMethod.valueOf(cut.method().name()), cut.path()).handler(cut::handle);
    }

    @Test
    void should_return_500_on_error(Vertx vertx, VertxTestContext context) {
        when(exchangeController.channelMetric(any())).thenReturn(Maybe.error(new RuntimeException()));

        HttpClient httpClient = vertx.createHttpClient();
        httpClient
            .request(HttpMethod.GET, serverPort, "localhost", "/exchange/channels/error")
            .flatMap(HttpClientRequest::send)
            .flatMap(httpClientResponse -> {
                assertThat(httpClientResponse.statusCode()).isEqualTo(500);
                return httpClientResponse.body();
            })
            .map(buffer -> {
                ManagementError managementError = Json.decodeValue(buffer, ManagementError.class);
                assertThat(managementError.code()).isEqualTo(500);
                assertThat(managementError.message()).isEqualTo("Unable to retrieve channel metrics for the given id [error]");
                return true;
            })
            .onFailure(context::failNow)
            .andThen(context.succeedingThenComplete());
    }

    @Test
    void should_return_404_when_id_not_found(Vertx vertx, VertxTestContext context) {
        when(exchangeController.channelMetric("unknown")).thenReturn(Maybe.empty());

        HttpClient httpClient = vertx.createHttpClient();
        httpClient
            .request(HttpMethod.GET, serverPort, "localhost", "/exchange/channels/unknown")
            .flatMap(HttpClientRequest::send)
            .flatMap(httpClientResponse -> {
                assertThat(httpClientResponse.statusCode()).isEqualTo(404);
                return httpClientResponse.body();
            })
            .map(buffer -> {
                ManagementError managementError = Json.decodeValue(buffer, ManagementError.class);
                assertThat(managementError.code()).isEqualTo(404);
                assertThat(managementError.message()).isEqualTo("No channel found for the given id [unknown]");
                return true;
            })
            .onFailure(context::failNow)
            .andThen(context.succeedingThenComplete());
    }

    @Test
    void should_return_batch_metric(Vertx vertx, VertxTestContext context) {
        ChannelMetric channelMetric = ChannelMetric
            .builder()
            .id("id1")
            .targetId("target1")
            .active(true)
            .pendingCommands(false)
            .primary(true)
            .build();

        when(exchangeController.channelMetric(channelMetric.id())).thenReturn(Maybe.just(channelMetric));

        HttpClient httpClient = vertx.createHttpClient();
        httpClient
            .request(HttpMethod.GET, serverPort, "localhost", "/exchange/channels/%s".formatted(channelMetric.id()))
            .flatMap(HttpClientRequest::send)
            .flatMap(httpClientResponse -> {
                assertThat(httpClientResponse.statusCode()).isEqualTo(200);
                return httpClientResponse.body();
            })
            .map(buffer -> {
                ChannelMetric returnChannelMetric = Json.decodeValue(buffer, ChannelMetric.class);
                assertThat(returnChannelMetric.id()).isEqualTo(channelMetric.id());
                assertThat(returnChannelMetric.targetId()).isEqualTo(channelMetric.targetId());
                assertThat(returnChannelMetric.active()).isEqualTo(channelMetric.active());
                assertThat(returnChannelMetric.pendingCommands()).isEqualTo(channelMetric.pendingCommands());
                assertThat(returnChannelMetric.primary()).isEqualTo(channelMetric.primary());
                return true;
            })
            .onFailure(context::failNow)
            .andThen(context.succeedingThenComplete());
    }
}
