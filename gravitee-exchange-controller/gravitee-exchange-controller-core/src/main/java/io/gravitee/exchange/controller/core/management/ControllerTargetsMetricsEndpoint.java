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

import static io.gravitee.exchange.controller.core.management.EndpointHelper.write;

import io.gravitee.common.http.HttpMethod;
import io.gravitee.exchange.api.configuration.IdentifyConfiguration;
import io.gravitee.exchange.api.controller.ExchangeController;
import io.gravitee.exchange.api.controller.metrics.BatchMetric;
import io.gravitee.exchange.api.controller.metrics.ChannelMetric;
import io.gravitee.exchange.controller.core.management.error.ManagementError;
import io.gravitee.node.management.http.endpoint.ManagementEndpoint;
import io.vertx.ext.web.RoutingContext;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Slf4j
public class ControllerTargetsMetricsEndpoint implements ManagementEndpoint {

    private final IdentifyConfiguration identifyConfiguration;
    private final ExchangeController exchangeController;

    @Override
    public HttpMethod method() {
        return HttpMethod.GET;
    }

    @Override
    public String path() {
        return EndpointHelper.path(identifyConfiguration, "/targets");
    }

    @Override
    public void handle(final RoutingContext ctx) {
        exchangeController
            .channelsMetricsByTarget()
            .map(targetMetric -> new Result(targetMetric.id(), targetMetric.channels()))
            .zipWith(
                exchangeController.batchsMetricsByTarget().cache(),
                (result, targetBatchsMetrics) -> {
                    if (result.id.equals(targetBatchsMetrics.id())) {
                        result.batchs.addAll(targetBatchsMetrics.batchs());
                    }
                    return result;
                }
            )
            .toList()
            .doOnSuccess(results -> write(ctx, results))
            .doOnError(throwable -> {
                log.error("[{}] Unable to retrieve targets metrics", identifyConfiguration.id(), throwable);
                write(ctx, ManagementError.builder().code(500).message("Unable to retrieve targets metrics").build());
            })
            .onErrorComplete()
            .subscribe();
    }

    public record Result(String id, List<ChannelMetric> channels, List<BatchMetric> batchs) {
        public Result(String id, List<ChannelMetric> channels) {
            this(id, channels, new ArrayList<>());
        }
    }
}
