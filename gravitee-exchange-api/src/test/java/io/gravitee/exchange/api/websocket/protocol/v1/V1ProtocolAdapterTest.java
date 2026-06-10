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
package io.gravitee.exchange.api.websocket.protocol.v1;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.exchange.api.command.Command;
import io.gravitee.exchange.api.command.goodbye.GoodByeCommand;
import io.gravitee.exchange.api.command.goodbye.GoodByeCommandPayload;
import io.gravitee.exchange.api.websocket.command.DefaultExchangeSerDe;
import io.gravitee.exchange.api.websocket.protocol.ProtocolExchange;
import io.vertx.core.buffer.Buffer;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class V1ProtocolAdapterTest {

    private final V1ProtocolAdapter adapter = new V1ProtocolAdapter(new DefaultExchangeSerDe(new ObjectMapper()));

    private static final String DOC_CONTENT_WITH_SEPARATOR = "case \"$opt\" in\n  d) DEBUG=1 ;;\n  k) DIRECTION=\"$OPTARG\" ;;\nesac";

    @Test
    void should_round_trip_command_payload_containing_separator() {
        assertCommandTargetIdRoundTrips(DOC_CONTENT_WITH_SEPARATOR);
    }

    @Test
    void should_round_trip_command_payload_with_separated_semicolons() {
        assertCommandTargetIdRoundTrips(DOC_CONTENT_WITH_SEPARATOR.replace(";;", "; ;"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "a;;b", "a;;b;;c", "a;;;;b", ";;leading", "trailing;;" })
    void should_round_trip_command_payload_for_separator_variants(final String content) {
        assertCommandTargetIdRoundTrips(content);
    }

    @Test
    void should_round_trip_payload_fragments_that_mimic_field_prefixes() {
        assertCommandTargetIdRoundTrips("a;;e:b;;t:c");
    }

    private void assertCommandTargetIdRoundTrips(final String targetId) {
        final GoodByeCommand command = new GoodByeCommand(GoodByeCommandPayload.builder().targetId(targetId).build());

        final Buffer frame = adapter.write(
            ProtocolExchange.builder().type(ProtocolExchange.Type.COMMAND).exchangeType(command.getType()).exchange(command).build()
        );
        final Command<?> decoded = adapter.read(frame).asCommand();

        assertThat(decoded).isInstanceOf(GoodByeCommand.class);
        assertThat(((GoodByeCommand) decoded).getPayload().getTargetId()).isEqualTo(targetId);
    }
}
