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
package io.gravitee.exchange.controller.core.management.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.gravitee.exchange.api.batch.BatchStatus;
import io.gravitee.exchange.api.configuration.IdentifyConfiguration;
import io.gravitee.exchange.api.controller.ExchangeController;
import io.gravitee.exchange.api.controller.metrics.BatchMetric;
import io.gravitee.exchange.api.controller.metrics.TargetBatchsMetric;
import io.gravitee.exchange.controller.core.management.AbstractMetricEndpointTest;
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
class ControllerBatchsMetricsEndpointTest extends AbstractMetricEndpointTest {

    @Mock
    private ExchangeController exchangeController;

    private ControllerBatchsMetricsEndpoint cut;

    @BeforeEach
    public void beforeEach() {
        cut = new ControllerBatchsMetricsEndpoint(new IdentifyConfiguration(new MockEnvironment()), exchangeController);
        mainRouter.route(HttpMethod.valueOf(cut.method().name()), cut.path()).handler(cut::handle);
    }

    @Test
    void should_return_500_on_error(Vertx vertx, VertxTestContext context) {
        when(exchangeController.batchsMetricsByTarget()).thenReturn(Flowable.error(new RuntimeException()));

        HttpClient httpClient = vertx.createHttpClient();
        httpClient
            .request(HttpMethod.GET, serverPort, "localhost", "/exchange/batchs")
            .flatMap(HttpClientRequest::send)
            .flatMap(httpClientResponse -> {
                assertThat(httpClientResponse.statusCode()).isEqualTo(500);
                return httpClientResponse.body();
            })
            .map(buffer -> {
                ManagementError managementError = Json.decodeValue(buffer, ManagementError.class);
                assertThat(managementError.code()).isEqualTo(500);
                assertThat(managementError.message()).isEqualTo("Unable to retrieve batchs metrics");
                return true;
            })
            .onFailure(context::failNow)
            .andThen(context.succeedingThenComplete());
    }

    @Test
    void should_return_empty_array_when_no_batchs_found(Vertx vertx, VertxTestContext context) {
        when(exchangeController.batchsMetricsByTarget()).thenReturn(Flowable.empty());

        HttpClient httpClient = vertx.createHttpClient();
        httpClient
            .request(HttpMethod.GET, serverPort, "localhost", "/exchange/batchs")
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
    void should_return_batchs_metric(Vertx vertx, VertxTestContext context) {
        BatchMetric batchMetric1 = BatchMetric
            .builder()
            .id("id1")
            .key("key1")
            .targetId("target")
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
            .targetId("target2")
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
            .request(HttpMethod.GET, serverPort, "localhost", "/exchange/batchs")
            .flatMap(HttpClientRequest::send)
            .flatMap(httpClientResponse -> {
                assertThat(httpClientResponse.statusCode()).isEqualTo(200);
                return httpClientResponse.body();
            })
            .map(buffer -> {
                JsonArray jsonArray = (JsonArray) Json.decodeValue(buffer);
                assertThat(jsonArray).hasSize(2);
                BatchMetric result1 = jsonArray.getJsonObject(0).mapTo(BatchMetric.class);
                assertThat(result1.id()).isEqualTo(result1.id());
                assertThat(result1.key()).isEqualTo(result1.key());
                assertThat(result1.targetId()).isEqualTo("target");
                assertThat(result1.retry()).isEqualTo(result1.retry());
                assertThat(result1.maxRetry()).isEqualTo(result1.maxRetry());
                assertThat(result1.lastRetryAt()).isEqualTo(result1.lastRetryAt());
                assertThat(result1.errorDetails()).isEqualTo(result1.errorDetails());
                BatchMetric result2 = jsonArray.getJsonObject(1).mapTo(BatchMetric.class);
                assertThat(result2.id()).isEqualTo(result2.id());
                assertThat(result2.key()).isEqualTo(result2.key());
                assertThat(result2.targetId()).isEqualTo("target2");
                assertThat(result2.retry()).isEqualTo(result2.retry());
                assertThat(result2.maxRetry()).isEqualTo(result2.maxRetry());
                assertThat(result2.lastRetryAt()).isEqualTo(result2.lastRetryAt());
                assertThat(result2.errorDetails()).isEqualTo(result2.errorDetails());
                return true;
            })
            .onFailure(context::failNow)
            .andThen(context.succeedingThenComplete());
    }

    @Test
    void should_return_filtered_batchs_metric_with_status_filtering(Vertx vertx, VertxTestContext context) {
        BatchMetric batchSucceeded = BatchMetric
            .builder()
            .id("id-succeeded")
            .key("key-succeeded")
            .targetId("target")
            .status(BatchStatus.SUCCEEDED)
            .errorDetails("errorDetails-succeeded")
            .retry(1)
            .maxRetry(2)
            .lastRetryAt(Instant.now())
            .build();
        BatchMetric batchPending = BatchMetric
            .builder()
            .id("id-pending")
            .key("key-pending")
            .targetId("target2")
            .status(BatchStatus.PENDING)
            .errorDetails("errorDetails-pending")
            .retry(5)
            .maxRetry(10)
            .lastRetryAt(Instant.now())
            .build();
        when(exchangeController.batchsMetricsByTarget())
            .thenReturn(
                Flowable.just(
                    TargetBatchsMetric.builder().id("target").batchs(List.of(batchSucceeded)).build(),
                    TargetBatchsMetric.builder().id("target2").batchs(List.of(batchPending)).build()
                )
            );

        HttpClient httpClient = vertx.createHttpClient();
        httpClient
            .request(HttpMethod.GET, serverPort, "localhost", "/exchange/batchs?status=PENDING")
            .flatMap(HttpClientRequest::send)
            .flatMap(httpClientResponse -> {
                assertThat(httpClientResponse.statusCode()).isEqualTo(200);
                return httpClientResponse.body();
            })
            .map(buffer -> {
                JsonArray jsonArray = (JsonArray) Json.decodeValue(buffer);
                assertThat(jsonArray).hasSize(1);
                BatchMetric batchMetric = jsonArray.getJsonObject(0).mapTo(BatchMetric.class);
                assertThat(batchMetric.id()).isEqualTo(batchPending.id());
                assertThat(batchMetric.key()).isEqualTo(batchPending.key());
                assertThat(batchMetric.targetId()).isEqualTo(batchPending.targetId());
                assertThat(batchMetric.status()).isEqualTo(batchPending.status());
                assertThat(batchMetric.retry()).isEqualTo(batchPending.retry());
                assertThat(batchMetric.maxRetry()).isEqualTo(batchPending.maxRetry());
                assertThat(batchMetric.lastRetryAt()).isEqualTo(batchPending.lastRetryAt());
                return true;
            })
            .onFailure(context::failNow)
            .andThen(context.succeedingThenComplete());
    }

    @Test
    void should_return_filtered_batchs_metric_with_target_filtering(Vertx vertx, VertxTestContext context) {
        BatchMetric batchSucceeded = BatchMetric
            .builder()
            .id("id-succeeded")
            .key("key-succeeded")
            .targetId("targetId-succeeded")
            .status(BatchStatus.SUCCEEDED)
            .errorDetails("errorDetails-succeeded")
            .retry(1)
            .maxRetry(2)
            .lastRetryAt(Instant.now())
            .build();
        BatchMetric batchPending = BatchMetric
            .builder()
            .id("id-pending")
            .key("key-pending")
            .targetId("targetId-pending")
            .status(BatchStatus.PENDING)
            .errorDetails("errorDetails-pending")
            .retry(5)
            .maxRetry(10)
            .lastRetryAt(Instant.now())
            .build();
        when(exchangeController.batchsMetricsByTarget())
            .thenReturn(
                Flowable.just(
                    TargetBatchsMetric.builder().id("targetId-succeeded").batchs(List.of(batchSucceeded)).build(),
                    TargetBatchsMetric.builder().id("targetId-pending").batchs(List.of(batchPending)).build()
                )
            );

        HttpClient httpClient = vertx.createHttpClient();
        httpClient
            .request(HttpMethod.GET, serverPort, "localhost", "/exchange/batchs?targetId=targetId-pending")
            .flatMap(HttpClientRequest::send)
            .flatMap(httpClientResponse -> {
                assertThat(httpClientResponse.statusCode()).isEqualTo(200);
                return httpClientResponse.body();
            })
            .map(buffer -> {
                JsonArray jsonArray = (JsonArray) Json.decodeValue(buffer);
                assertThat(jsonArray).hasSize(1);
                BatchMetric batchMetric = jsonArray.getJsonObject(0).mapTo(BatchMetric.class);
                assertThat(batchMetric.id()).isEqualTo(batchPending.id());
                assertThat(batchMetric.key()).isEqualTo(batchPending.key());
                assertThat(batchMetric.targetId()).isEqualTo(batchPending.targetId());
                assertThat(batchMetric.status()).isEqualTo(batchPending.status());
                assertThat(batchMetric.retry()).isEqualTo(batchPending.retry());
                assertThat(batchMetric.maxRetry()).isEqualTo(batchPending.maxRetry());
                assertThat(batchMetric.lastRetryAt()).isEqualTo(batchPending.lastRetryAt());
                return true;
            })
            .onFailure(context::failNow)
            .andThen(context.succeedingThenComplete());
    }
}
