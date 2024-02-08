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
package io.gravitee.exchange.connector.core.command.healtcheck;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.gravitee.exchange.api.command.CommandStatus;
import io.gravitee.exchange.api.command.healtcheck.HealthCheckCommand;
import io.gravitee.exchange.api.command.healtcheck.HealthCheckCommandPayload;
import io.gravitee.exchange.api.connector.ExchangeConnector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * @author Guillaume LAMIRAND (guillaume.lamirand at graviteesource.com)
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class HealthCheckCommandHandlerTest {

    @Mock
    private ExchangeConnector exchangeConnector;

    private HealthCheckCommandHandler cut;

    @BeforeEach
    public void beforeEach() {
        cut = new HealthCheckCommandHandler(exchangeConnector);
    }

    @Test
    void should_handle_good_bye_command() {
        assertThat(cut.supportType()).isEqualTo(HealthCheckCommand.COMMAND_TYPE);
    }

    @Test
    void should_answer_with_healthy_payload() {
        when(exchangeConnector.targetId()).thenReturn("targetId");
        HealthCheckCommand healthCheckCommand = new HealthCheckCommand(HealthCheckCommandPayload.builder().build());
        cut
            .handle(healthCheckCommand)
            .test()
            .assertComplete()
            .assertValue(goodByeReply -> {
                assertThat(goodByeReply.getCommandId()).isEqualTo(healthCheckCommand.getId());
                assertThat(goodByeReply.getCommandStatus()).isEqualTo(CommandStatus.SUCCEEDED);
                assertThat(goodByeReply.getPayload().healthy()).isTrue();
                return true;
            });
    }
}
