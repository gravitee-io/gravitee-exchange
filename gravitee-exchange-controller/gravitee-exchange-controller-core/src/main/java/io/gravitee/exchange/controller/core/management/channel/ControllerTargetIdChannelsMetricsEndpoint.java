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
package io.gravitee.exchange.controller.core.management.channel;

import static io.gravitee.exchange.controller.core.management.EndpointHelper.write;

import io.gravitee.common.http.HttpMethod;
import io.gravitee.exchange.api.configuration.IdentifyConfiguration;
import io.gravitee.exchange.api.controller.ExchangeController;
import io.gravitee.exchange.controller.core.management.EndpointHelper;
import io.gravitee.exchange.controller.core.management.error.ManagementError;
import io.gravitee.node.management.http.endpoint.ManagementEndpoint;
import io.vertx.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Slf4j
public class ControllerTargetIdChannelsMetricsEndpoint implements ManagementEndpoint {

    private final IdentifyConfiguration identifyConfiguration;
    private final ExchangeController exchangeController;

    @Override
    public HttpMethod method() {
        return HttpMethod.GET;
    }

    @Override
    public String path() {
        return EndpointHelper.path(identifyConfiguration, "/targets/:targetId/channels");
    }

    @Override
    public void handle(final RoutingContext ctx) {
        var targetId = ctx.pathParam("targetId");
        exchangeController
            .channelsMetricsForTarget(targetId)
            .toList()
            .doOnSuccess(channelMetrics -> write(ctx, channelMetrics))
            .doOnError(throwable -> {
                log.error(
                    "[{}] Unable to retrieve channels metrics for the given target id '{}'",
                    identifyConfiguration.id(),
                    targetId,
                    throwable
                );
                write(
                    ctx,
                    ManagementError
                        .builder()
                        .code(500)
                        .message("Unable to retrieve channels metrics for the given target id [%s]".formatted(targetId))
                        .build()
                );
            })
            .onErrorComplete()
            .subscribe();
    }
}
