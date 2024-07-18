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

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.TestSocketUtils;

/**
 * @author Guillaume LAMIRAND (guillaume.lamirand at graviteesource.com)
 * @author GraviteeSource Team
 */
@ExtendWith({ VertxExtension.class, MockitoExtension.class })
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public abstract class AbstractMetricEndpointTest {

    protected static int serverPort;
    protected static HttpServer httpServer;
    protected static Router mainRouter;

    @BeforeAll
    public static void startServer(Vertx vertx, VertxTestContext context) {
        final HttpServerOptions httpServerOptions = new HttpServerOptions();
        serverPort = TestSocketUtils.findAvailableTcpPort();
        httpServerOptions.setPort(serverPort);
        mainRouter = Router.router(vertx);
        httpServer = vertx.createHttpServer(httpServerOptions);
        httpServer.requestHandler(mainRouter).listen(serverPort).andThen(context.succeedingThenComplete());
    }

    @AfterAll
    public static void stopServer(VertxTestContext context) {
        httpServer.close().andThen(context.succeedingThenComplete());
    }

    @AfterEach
    public void afterEach() {
        mainRouter.getRoutes().forEach(Route::remove);
    }
}
