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
package io.gravitee.exchange.controller.websocket;

import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.ext.web.RoutingContext;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FailureHandler implements io.vertx.core.Handler<RoutingContext> {

    @Override
    public void handle(final RoutingContext ctx) {
        log.error("Caught failure", ctx.failure());
        ctx.response().putHeader("content-type", "application/json").setStatusCode(500).end().subscribe();
    }
}
