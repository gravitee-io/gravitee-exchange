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

import static io.gravitee.exchange.api.configuration.IdentifyConfiguration.DEFAULT_EXCHANGE_ID;

import io.gravitee.common.http.HttpStatusCode;
import io.gravitee.common.http.MediaType;
import io.gravitee.exchange.api.configuration.IdentifyConfiguration;
import io.gravitee.exchange.controller.core.management.error.ManagementError;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.Json;
import io.vertx.ext.web.RoutingContext;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * @author Guillaume LAMIRAND (guillaume.lamirand at graviteesource.com)
 * @author GraviteeSource Team
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class EndpointHelper {

    public static String path(final IdentifyConfiguration identifyConfiguration, final String path) {
        StringBuilder builder = new StringBuilder("/exchange");
        String id = identifyConfiguration.id();
        if (!DEFAULT_EXCHANGE_ID.equals(id)) {
            builder.append("/%s".formatted(id));
        }
        return builder.append(path).toString();
    }

    public static <T> void write(final RoutingContext ctx, final T payload) {
        ctx
            .response()
            .setStatusCode(HttpStatusCode.OK_200)
            .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
            .end(Json.encodePrettily(payload));
    }

    public static void write(final RoutingContext ctx, final ManagementError exchangeManagementError) {
        ctx
            .response()
            .setStatusCode(exchangeManagementError.code())
            .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
            .end(Json.encodePrettily(exchangeManagementError));
    }
}
