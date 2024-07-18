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
package io.gravitee.exchange.controller.core.management;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.gravitee.exchange.api.batch.BatchStatus;
import io.gravitee.exchange.api.configuration.IdentifyConfiguration;
import io.gravitee.exchange.api.controller.ExchangeController;
import io.gravitee.exchange.api.controller.metrics.BatchMetric;
import io.gravitee.exchange.api.controller.metrics.ChannelMetric;
import io.gravitee.exchange.controller.core.management.error.ManagementError;
import io.reactivex.rxjava3.core.Flowable;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.Json;
import io.vertx.junit5.VertxTestContext;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.mock.env.MockEnvironment;

/**
 * @author Guillaume LAMIRAND (guillaume.lamirand at graviteesource.com)
 * @author GraviteeSource Team
 */
class ControllerTargetIdMetricEndpointTest extends AbstractMetricEndpointTest {

    @Mock
    private ExchangeController exchangeController;

    private ControllerTargetIdMetricsEndpoint cut;

    @BeforeEach
    public void beforeEach() {
        cut = new ControllerTargetIdMetricsEndpoint(new IdentifyConfiguration(new MockEnvironment()), exchangeController);
        mainRouter.route(HttpMethod.valueOf(cut.method().name()), cut.path()).handler(cut::handle);
    }

    @Test
    void should_return_500_on_error(Vertx vertx, VertxTestContext context) {
        when(exchangeController.channelsMetricsForTarget(any())).thenReturn(Flowable.error(new RuntimeException()));

        HttpClient httpClient = vertx.createHttpClient();
        httpClient
            .request(HttpMethod.GET, serverPort, "localhost", "/exchange/targets/error")
            .flatMap(HttpClientRequest::send)
            .flatMap(httpClientResponse -> {
                assertThat(httpClientResponse.statusCode()).isEqualTo(500);
                return httpClientResponse.body();
            })
            .map(buffer -> {
                ManagementError managementError = Json.decodeValue(buffer, ManagementError.class);
                assertThat(managementError.code()).isEqualTo(500);
                assertThat(managementError.message()).isEqualTo("Unable to retrieve target metrics for the given target [error]");
                return true;
            })
            .onFailure(context::failNow)
            .andThen(context.succeedingThenComplete());
    }

    @Test
    void should_return_404_when_id_not_found(Vertx vertx, VertxTestContext context) {
        when(exchangeController.channelsMetricsForTarget("unknown")).thenReturn(Flowable.empty());
        when(exchangeController.batchsMetricsForTarget("unknown")).thenReturn(Flowable.empty());

        HttpClient httpClient = vertx.createHttpClient();
        httpClient
            .request(HttpMethod.GET, serverPort, "localhost", "/exchange/targets/unknown")
            .flatMap(HttpClientRequest::send)
            .flatMap(httpClientResponse -> {
                assertThat(httpClientResponse.statusCode()).isEqualTo(404);
                return httpClientResponse.body();
            })
            .map(buffer -> {
                ManagementError managementError = Json.decodeValue(buffer, ManagementError.class);
                assertThat(managementError.code()).isEqualTo(404);
                assertThat(managementError.message()).isEqualTo("No metrics found for the given target [unknown]");
                return true;
            })
            .onFailure(context::failNow)
            .andThen(context.succeedingThenComplete());
    }

    @Test
    void should_return_target_metric_for_specific_target(Vertx vertx, VertxTestContext context) {
        ChannelMetric channelMetric = ChannelMetric.builder().id("id").active(true).pendingCommands(false).primary(true).build();
        when(exchangeController.channelsMetricsForTarget("target")).thenReturn(Flowable.just(channelMetric));
        BatchMetric batchMetric = BatchMetric
            .builder()
            .id("id1")
            .key("key1")
            .status(BatchStatus.SUCCEEDED)
            .errorDetails("errorDetails")
            .retry(1)
            .maxRetry(2)
            .lastRetryAt(Instant.now())
            .build();
        when(exchangeController.batchsMetricsForTarget("target")).thenReturn(Flowable.just(batchMetric));
        HttpClient httpClient = vertx.createHttpClient();
        httpClient
            .request(HttpMethod.GET, serverPort, "localhost", "/exchange/targets/%s".formatted("target"))
            .flatMap(HttpClientRequest::send)
            .flatMap(httpClientResponse -> {
                assertThat(httpClientResponse.statusCode()).isEqualTo(200);
                return httpClientResponse.body();
            })
            .map(buffer -> {
                Result result = Json.decodeValue(buffer, Result.class);
                assertThat(result.channels()).containsOnly(channelMetric);
                assertThat(result.batchs()).containsOnly(batchMetric);
                return true;
            })
            .onFailure(context::failNow)
            .andThen(context.succeedingThenComplete());
    }

    @Test
    void should_return_target_metric_for_specific_target_with_batch(Vertx vertx, VertxTestContext context) {
        ChannelMetric channelMetric = ChannelMetric.builder().id("id").active(true).pendingCommands(false).primary(true).build();
        when(exchangeController.channelsMetricsForTarget("target")).thenReturn(Flowable.just(channelMetric));
        when(exchangeController.batchsMetricsForTarget(any())).thenReturn(Flowable.empty());
        HttpClient httpClient = vertx.createHttpClient();
        httpClient
            .request(HttpMethod.GET, serverPort, "localhost", "/exchange/targets/%s".formatted("target"))
            .flatMap(HttpClientRequest::send)
            .flatMap(httpClientResponse -> {
                assertThat(httpClientResponse.statusCode()).isEqualTo(200);
                return httpClientResponse.body();
            })
            .map(buffer -> {
                Result result = Json.decodeValue(buffer, Result.class);
                assertThat(result.channels()).containsOnly(channelMetric);
                assertThat(result.batchs()).isEmpty();
                return true;
            })
            .onFailure(context::failNow)
            .andThen(context.succeedingThenComplete());
    }

    public record Result(List<ChannelMetric> channels, List<BatchMetric> batchs) {}
}
