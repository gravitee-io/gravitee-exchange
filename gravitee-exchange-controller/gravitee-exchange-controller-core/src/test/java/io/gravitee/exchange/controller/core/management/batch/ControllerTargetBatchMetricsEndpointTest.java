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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.gravitee.exchange.api.batch.BatchStatus;
import io.gravitee.exchange.api.configuration.IdentifyConfiguration;
import io.gravitee.exchange.api.controller.ExchangeController;
import io.gravitee.exchange.api.controller.metrics.BatchMetric;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.mock.env.MockEnvironment;

/**
 * @author Guillaume LAMIRAND (guillaume.lamirand at graviteesource.com)
 * @author GraviteeSource Team
 */
class ControllerTargetBatchMetricsEndpointTest extends AbstractMetricEndpointTest {

    @Mock
    private ExchangeController exchangeController;

    private ControllerTargetIdBatchsMetricsEndpoint cut;

    @BeforeEach
    public void beforeEach() {
        cut = new ControllerTargetIdBatchsMetricsEndpoint(new IdentifyConfiguration(new MockEnvironment()), exchangeController);
        mainRouter.route(HttpMethod.valueOf(cut.method().name()), cut.path()).handler(cut::handle);
    }

    @Test
    void should_return_500_on_error(Vertx vertx, VertxTestContext context) {
        when(exchangeController.batchsMetricsForTarget(any())).thenReturn(Flowable.error(new RuntimeException()));

        HttpClient httpClient = vertx.createHttpClient();
        httpClient
            .request(HttpMethod.GET, serverPort, "localhost", "/exchange/targets/error/batchs")
            .flatMap(HttpClientRequest::send)
            .flatMap(httpClientResponse -> {
                assertThat(httpClientResponse.statusCode()).isEqualTo(500);
                return httpClientResponse.body();
            })
            .map(buffer -> {
                ManagementError managementError = Json.decodeValue(buffer, ManagementError.class);
                assertThat(managementError.code()).isEqualTo(500);
                assertThat(managementError.message()).isEqualTo("Unable to retrieve batchs metrics for the given target [error]");
                return true;
            })
            .onFailure(context::failNow)
            .andThen(context.succeedingThenComplete());
    }

    @Test
    void should_return_empty_array_when_no_batchs_found(Vertx vertx, VertxTestContext context) {
        when(exchangeController.batchsMetricsForTarget("unknown")).thenReturn(Flowable.empty());

        HttpClient httpClient = vertx.createHttpClient();
        httpClient
            .request(HttpMethod.GET, serverPort, "localhost", "/exchange/targets/unknown/batchs")
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
    void should_return_batch_metric_for_target(Vertx vertx, VertxTestContext context) {
        BatchMetric batch = BatchMetric
            .builder()
            .id("id")
            .key("key")
            .targetId("targetId")
            .status(BatchStatus.PENDING)
            .errorDetails("errorDetails")
            .retry(1)
            .maxRetry(2)
            .lastRetryAt(Instant.now())
            .build();
        when(exchangeController.batchsMetricsForTarget(batch.targetId())).thenReturn(Flowable.just(batch));

        HttpClient httpClient = vertx.createHttpClient();
        httpClient
            .request(HttpMethod.GET, serverPort, "localhost", "/exchange/targets/%s/batchs".formatted(batch.targetId()))
            .flatMap(HttpClientRequest::send)
            .flatMap(httpClientResponse -> {
                assertThat(httpClientResponse.statusCode()).isEqualTo(200);
                return httpClientResponse.body();
            })
            .map(buffer -> {
                JsonArray jsonArray = (JsonArray) Json.decodeValue(buffer);
                assertThat(jsonArray).hasSize(1);
                BatchMetric batchMetric = jsonArray.getJsonObject(0).mapTo(BatchMetric.class);
                assertThat(batchMetric.id()).isEqualTo(batch.id());
                assertThat(batchMetric.key()).isEqualTo(batch.key());
                assertThat(batchMetric.status()).isEqualTo(batch.status());
                assertThat(batchMetric.retry()).isEqualTo(batch.retry());
                assertThat(batchMetric.maxRetry()).isEqualTo(batch.maxRetry());
                assertThat(batchMetric.lastRetryAt()).isEqualTo(batch.lastRetryAt());
                return true;
            })
            .onFailure(context::failNow)
            .andThen(context.succeedingThenComplete());
    }
}
