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
package io.gravitee.exchange.controller.websocket.auth;

import static org.assertj.core.api.Assertions.assertThat;

import io.gravitee.exchange.api.controller.ControllerCommandContext;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

/**
 * @author Guillaume LAMIRAND (guillaume.lamirand at graviteesource.com)
 * @author GraviteeSource Team
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class DefaultWebSocketControllerAuthenticationTest {

    @Test
    void should_return_a_valid_context__by_default() {
        DefaultWebSocketControllerAuthentication cut = new DefaultWebSocketControllerAuthentication();
        ControllerCommandContext controllerContext = cut.authenticate(null);
        assertThat(controllerContext).isInstanceOf(DefaultCommandContext.class);
        assertThat(controllerContext.isValid()).isTrue();
    }
}
