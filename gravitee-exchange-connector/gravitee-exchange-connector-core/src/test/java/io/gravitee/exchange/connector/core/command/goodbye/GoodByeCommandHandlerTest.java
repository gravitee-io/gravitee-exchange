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
package io.gravitee.exchange.connector.core.command.goodbye;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.gravitee.exchange.api.command.CommandStatus;
import io.gravitee.exchange.api.command.goodbye.GoodByeCommand;
import io.gravitee.exchange.api.command.goodbye.GoodByeCommandPayload;
import io.gravitee.exchange.api.connector.ExchangeConnector;
import io.reactivex.rxjava3.core.Completable;
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
class GoodByeCommandHandlerTest {

    @Mock
    private ExchangeConnector exchangeConnector;

    private GoodByeCommandHandler cut;

    @BeforeEach
    public void beforeEach() {
        cut = new GoodByeCommandHandler(exchangeConnector);
    }

    @Test
    void should_handle_good_bye_command() {
        assertThat(cut.supportType()).isEqualTo(GoodByeCommand.COMMAND_TYPE);
    }

    @Test
    void should_initialize_connector_when_reconnect_is_true() {
        when(exchangeConnector.initialize()).thenReturn(Completable.complete());
        GoodByeCommand goodByeCommand = new GoodByeCommand(GoodByeCommandPayload.builder().targetId("targetId").reconnect(true).build());
        cut
            .handle(goodByeCommand)
            .test()
            .assertComplete()
            .assertValue(goodByeReply -> {
                assertThat(goodByeReply.getCommandId()).isEqualTo(goodByeCommand.getId());
                assertThat(goodByeReply.getCommandStatus()).isEqualTo(CommandStatus.SUCCEEDED);
                assertThat(goodByeReply.getPayload().getTargetId()).isEqualTo(goodByeCommand.getPayload().getTargetId());
                return true;
            });

        verify(exchangeConnector, never()).close();
        verify(exchangeConnector).initialize();
    }

    @Test
    void should_not_initialize_connector_when_reconnect_is_false() {
        when(exchangeConnector.close()).thenReturn(Completable.complete());
        GoodByeCommand goodByeCommand = new GoodByeCommand(GoodByeCommandPayload.builder().targetId("targetId").reconnect(false).build());
        cut
            .handle(goodByeCommand)
            .test()
            .assertComplete()
            .assertValue(goodByeReply -> {
                assertThat(goodByeReply.getCommandId()).isEqualTo(goodByeCommand.getId());
                assertThat(goodByeReply.getCommandStatus()).isEqualTo(CommandStatus.SUCCEEDED);
                assertThat(goodByeReply.getPayload().getTargetId()).isEqualTo(goodByeCommand.getPayload().getTargetId());
                return true;
            });

        verify(exchangeConnector).close();
        verify(exchangeConnector, never()).initialize();
    }
}
