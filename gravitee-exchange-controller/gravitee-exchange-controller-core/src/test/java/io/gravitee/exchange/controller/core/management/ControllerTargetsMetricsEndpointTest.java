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
import static org.mockito.Mockito.when;

import io.gravitee.exchange.api.batch.BatchStatus;
import io.gravitee.exchange.api.configuration.IdentifyConfiguration;
import io.gravitee.exchange.api.controller.ExchangeController;
import io.gravitee.exchange.api.controller.metrics.BatchMetric;
import io.gravitee.exchange.api.controller.metrics.ChannelMetric;
import io.gravitee.exchange.api.controller.metrics.TargetBatchsMetric;
import io.gravitee.exchange.api.controller.metrics.TargetChannelsMetric;
import io.gravitee.exchange.controller.core.management.error.ManagementError;
import io.reactivex.rxjava3.core.Flowable;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
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
class ControllerTargetsMetricsEndpointTest extends AbstractMetricEndpointTest {

    @Mock
    private ExchangeController exchangeController;

    private ControllerTargetsMetricsEndpoint cut;

    @BeforeEach
    public void beforeEach() {
        cut = new ControllerTargetsMetricsEndpoint(new IdentifyConfiguration(new MockEnvironment()), exchangeController);
        mainRouter.route(HttpMethod.valueOf(cut.method().name()), cut.path()).handler(cut::handle);
    }

    @Test
    void should_return_500_when_error_occurred_retrieving_targets_channels_metrics(Vertx vertx, VertxTestContext context) {
        when(exchangeController.channelsMetricsByTarget()).thenReturn(Flowable.error(new RuntimeException()));
        when(exchangeController.batchsMetricsByTarget()).thenReturn(Flowable.empty());

        HttpClient httpClient = vertx.createHttpClient();
        httpClient
            .request(HttpMethod.GET, serverPort, "localhost", "/exchange/targets")
            .flatMap(HttpClientRequest::send)
            .flatMap(httpClientResponse -> {
                assertThat(httpClientResponse.statusCode()).isEqualTo(500);
                return httpClientResponse.body();
            })
            .map(buffer -> {
                ManagementError managementError = Json.decodeValue(buffer, ManagementError.class);
                assertThat(managementError.code()).isEqualTo(500);
                assertThat(managementError.message()).isEqualTo("Unable to retrieve targets metrics");
                return true;
            })
            .onFailure(context::failNow)
            .andThen(context.succeedingThenComplete());
    }

    @Test
    void should_return_500_when_error_occurred_retrieving_targets_batchs_metrics(Vertx vertx, VertxTestContext context) {
        ChannelMetric channelMetric = ChannelMetric.builder().id("id").active(true).primary(true).build();
        TargetChannelsMetric targetChannelsMetric = TargetChannelsMetric.builder().id("target").channels(List.of(channelMetric)).build();
        when(exchangeController.channelsMetricsByTarget()).thenReturn(Flowable.just(targetChannelsMetric));
        when(exchangeController.batchsMetricsByTarget()).thenReturn(Flowable.error(new RuntimeException()));

        HttpClient httpClient = vertx.createHttpClient();
        httpClient
            .request(HttpMethod.GET, serverPort, "localhost", "/exchange/targets")
            .flatMap(HttpClientRequest::send)
            .flatMap(httpClientResponse -> {
                assertThat(httpClientResponse.statusCode()).isEqualTo(500);
                return httpClientResponse.body();
            })
            .map(buffer -> {
                ManagementError managementError = Json.decodeValue(buffer, ManagementError.class);
                assertThat(managementError.code()).isEqualTo(500);
                assertThat(managementError.message()).isEqualTo("Unable to retrieve targets metrics");
                return true;
            })
            .onFailure(context::failNow)
            .andThen(context.succeedingThenComplete());
    }

    @Test
    void should_return_empty_array_when_no_metrics(Vertx vertx, VertxTestContext context) {
        when(exchangeController.channelsMetricsByTarget()).thenReturn(Flowable.empty());
        when(exchangeController.batchsMetricsByTarget()).thenReturn(Flowable.empty());

        HttpClient httpClient = vertx.createHttpClient();
        httpClient
            .request(HttpMethod.GET, serverPort, "localhost", "/exchange/targets")
            .flatMap(HttpClientRequest::send)
            .flatMap(httpClientResponse -> {
                assertThat(httpClientResponse.statusCode()).isEqualTo(200);
                return httpClientResponse.body();
            })
            .map(buffer -> {
                assertThat(buffer).hasToString("[ ]");
                return true;
            })
            .onFailure(context::failNow)
            .andThen(context.succeedingThenComplete());
    }

    @Test
    void should_return_empty_array_when_no_metrics_even_with_orphan_batch(Vertx vertx, VertxTestContext context) {
        when(exchangeController.channelsMetricsByTarget()).thenReturn(Flowable.empty());
        BatchMetric orphanBatch = BatchMetric
            .builder()
            .id("id1")
            .key("key1")
            .status(BatchStatus.SUCCEEDED)
            .errorDetails("errorDetails")
            .retry(1)
            .maxRetry(2)
            .lastRetryAt(Instant.now())
            .build();
        when(exchangeController.batchsMetricsByTarget())
            .thenReturn(Flowable.just(TargetBatchsMetric.builder().id("orphan").batchs(List.of(orphanBatch)).build()));

        HttpClient httpClient = vertx.createHttpClient();
        httpClient
            .request(HttpMethod.GET, serverPort, "localhost", "/exchange/targets")
            .flatMap(HttpClientRequest::send)
            .flatMap(httpClientResponse -> {
                assertThat(httpClientResponse.statusCode()).isEqualTo(200);
                return httpClientResponse.body();
            })
            .map(buffer -> {
                assertThat(buffer).hasToString("[ ]");
                return true;
            })
            .onFailure(context::failNow)
            .andThen(context.succeedingThenComplete());
    }

    @Test
    void should_return_target_metrics(Vertx vertx, VertxTestContext context) {
        ChannelMetric channelMetric1 = ChannelMetric.builder().id("id").active(true).primary(true).build();
        TargetChannelsMetric targetChannelsMetric1 = TargetChannelsMetric.builder().id("target").channels(List.of(channelMetric1)).build();
        ChannelMetric channelMetric2 = ChannelMetric.builder().id("id2").active(true).primary(true).build();
        TargetChannelsMetric targetChannelsMetric2 = TargetChannelsMetric.builder().id("target2").channels(List.of(channelMetric2)).build();
        when(exchangeController.channelsMetricsByTarget()).thenReturn(Flowable.just(targetChannelsMetric1, targetChannelsMetric2));
        BatchMetric batchMetric1 = BatchMetric
            .builder()
            .id("id1")
            .key("key1")
            .status(BatchStatus.SUCCEEDED)
            .errorDetails("errorDetails")
            .retry(1)
            .maxRetry(2)
            .lastRetryAt(Instant.now())
            .build();
        BatchMetric batchMetric2 = BatchMetric
            .builder()
            .id("id2")
            .key("key2")
            .status(BatchStatus.PENDING)
            .errorDetails("errorDetails-pending")
            .retry(5)
            .maxRetry(10)
            .lastRetryAt(Instant.now())
            .build();
        when(exchangeController.batchsMetricsByTarget())
            .thenReturn(
                Flowable.just(
                    TargetBatchsMetric.builder().id("target").batchs(List.of(batchMetric1)).build(),
                    TargetBatchsMetric.builder().id("target2").batchs(List.of(batchMetric2)).build()
                )
            );

        HttpClient httpClient = vertx.createHttpClient();
        httpClient
            .request(HttpMethod.GET, serverPort, "localhost", "/exchange/targets")
            .flatMap(HttpClientRequest::send)
            .flatMap(httpClientResponse -> {
                assertThat(httpClientResponse.statusCode()).isEqualTo(200);
                return httpClientResponse.body();
            })
            .map(buffer -> {
                JsonArray jsonArray = (JsonArray) Json.decodeValue(buffer);
                assertThat(jsonArray).hasSize(2);
                Result result1 = jsonArray.getJsonObject(0).mapTo(Result.class);
                assertThat(result1.id()).isEqualTo("target");
                assertThat(result1.channels()).containsOnly(channelMetric1);
                assertThat(result1.batchs()).containsOnly(batchMetric1);
                Result result2 = jsonArray.getJsonObject(1).mapTo(Result.class);
                assertThat(result2.id()).isEqualTo("target2");
                assertThat(result2.channels()).containsOnly(channelMetric2);
                assertThat(result2.batchs()).containsOnly(batchMetric2);
                return true;
            })
            .onFailure(context::failNow)
            .andThen(context.succeedingThenComplete());
    }

    public record Result(String id, List<ChannelMetric> channels, List<BatchMetric> batchs) {}
}
