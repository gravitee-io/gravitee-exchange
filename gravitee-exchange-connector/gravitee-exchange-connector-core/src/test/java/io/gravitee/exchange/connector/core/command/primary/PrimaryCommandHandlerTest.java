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
package io.gravitee.exchange.connector.core.command.primary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import io.gravitee.exchange.api.command.CommandStatus;
import io.gravitee.exchange.api.command.primary.PrimaryCommand;
import io.gravitee.exchange.api.command.primary.PrimaryCommandPayload;
import io.gravitee.exchange.api.connector.ExchangeConnector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * @author Guillaume LAMIRAND (guillaume.lamirand at graviteesource.com)
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class PrimaryCommandHandlerTest {

    @Mock
    private ExchangeConnector exchangeConnector;

    private PrimaryCommandHandler cut;

    @BeforeEach
    public void beforeEach() {
        cut = new PrimaryCommandHandler(exchangeConnector);
    }

    @Test
    void should_handle_good_bye_command() {
        assertThat(cut.handleType()).isEqualTo(PrimaryCommand.COMMAND_TYPE);
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void should_set_primary_on_connector(final boolean primary) {
        PrimaryCommand primaryCommand = new PrimaryCommand(new PrimaryCommandPayload(primary));
        cut
            .handle(primaryCommand)
            .test()
            .assertComplete()
            .assertValue(primaryReply -> {
                assertThat(primaryReply.getCommandId()).isEqualTo(primaryCommand.getId());
                assertThat(primaryReply.getCommandStatus()).isEqualTo(CommandStatus.SUCCEEDED);
                return true;
            });
        verify(exchangeConnector).setPrimary(primary);
    }
}
