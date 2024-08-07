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

import io.gravitee.exchange.api.configuration.IdentifyConfiguration;
import io.gravitee.exchange.api.controller.ExchangeController;
import io.gravitee.exchange.api.controller.metrics.ChannelMetric;
import io.gravitee.exchange.controller.core.management.AbstractMetricEndpointTest;
import io.gravitee.exchange.controller.core.management.channel.ControllerTargetIdChannelsMetricsEndpoint;
import io.gravitee.exchange.controller.core.management.error.ManagementError;
import io.reactivex.rxjava3.core.Flowable;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.mock.env.MockEnvironment;

/**
 * @author Guillaume LAMIRAND (guillaume.lamirand at graviteesource.com)
 * @author GraviteeSource Team
 */
class ControllerTargetChannelsMetricEndpointTest extends AbstractMetricEndpointTest {

    @Mock
    private ExchangeController exchangeController;

    private ControllerTargetIdChannelsMetricsEndpoint cut;

    @BeforeEach
    public void beforeEach() {
        cut = new ControllerTargetIdChannelsMetricsEndpoint(new IdentifyConfiguration(new MockEnvironment()), exchangeController);
        mainRouter.route(HttpMethod.valueOf(cut.method().name()), cut.path()).handler(cut::handle);
    }

    @Test
    void should_return_500_on_error(Vertx vertx, VertxTestContext context) {
        when(exchangeController.channelsMetricsForTarget("error")).thenReturn(Flowable.error(new RuntimeException()));

        HttpClient httpClient = vertx.createHttpClient();
        httpClient
            .request(HttpMethod.GET, serverPort, "localhost", "/exchange/targets/error/channels")
            .flatMap(HttpClientRequest::send)
            .flatMap(httpClientResponse -> {
                assertThat(httpClientResponse.statusCode()).isEqualTo(500);
                return httpClientResponse.body();
            })
            .map(buffer -> {
                ManagementError managementError = Json.decodeValue(buffer, ManagementError.class);
                assertThat(managementError.code()).isEqualTo(500);
                assertThat(managementError.message()).isEqualTo("Unable to retrieve channels metrics for the given target id [error]");
                return true;
            })
            .onFailure(context::failNow)
            .andThen(context.succeedingThenComplete());
    }

    @Test
    void should_return_empty_array_when_no_channels_found(Vertx vertx, VertxTestContext context) {
        when(exchangeController.channelsMetricsForTarget("unknown")).thenReturn(Flowable.empty());

        HttpClient httpClient = vertx.createHttpClient();
        httpClient
            .request(HttpMethod.GET, serverPort, "localhost", "/exchange/targets/unknown/channels")
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
    void should_return_channels_metric(Vertx vertx, VertxTestContext context) {
        ChannelMetric channelMetric = ChannelMetric.builder().id("id").targetId("target").active(true).primary(true).build();
        when(exchangeController.channelsMetricsForTarget(channelMetric.targetId())).thenReturn(Flowable.just(channelMetric));

        HttpClient httpClient = vertx.createHttpClient();
        httpClient
            .request(HttpMethod.GET, serverPort, "localhost", "/exchange/targets/%s/channels".formatted(channelMetric.targetId()))
            .flatMap(HttpClientRequest::send)
            .flatMap(httpClientResponse -> {
                assertThat(httpClientResponse.statusCode()).isEqualTo(200);
                return httpClientResponse.body();
            })
            .map(buffer -> {
                JsonArray jsonArray = (JsonArray) Json.decodeValue(buffer);
                assertThat(jsonArray).hasSize(1);
                ChannelMetric channelMetricReturned = jsonArray.getJsonObject(0).mapTo(ChannelMetric.class);
                assertThat(channelMetricReturned.id()).isEqualTo(channelMetric.id());
                assertThat(channelMetricReturned.targetId()).isEqualTo(channelMetric.targetId());
                assertThat(channelMetricReturned.active()).isEqualTo(channelMetric.active());
                assertThat(channelMetricReturned.primary()).isEqualTo(channelMetric.primary());
                return true;
            })
            .onFailure(context::failNow)
            .andThen(context.succeedingThenComplete());
    }
}
