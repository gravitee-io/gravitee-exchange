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
package io.gravitee.exchange.api.websocket.command;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.exchange.api.command.Command;
import io.gravitee.exchange.api.command.Reply;
import io.gravitee.exchange.api.command.goodbye.GoodByeCommand;
import io.gravitee.exchange.api.command.goodbye.GoodByeCommandPayload;
import io.gravitee.exchange.api.command.goodbye.GoodByeReply;
import io.gravitee.exchange.api.command.goodbye.GoodByeReplyPayload;
import io.gravitee.exchange.api.command.healtcheck.HealthCheckCommand;
import io.gravitee.exchange.api.command.healtcheck.HealthCheckCommandPayload;
import io.gravitee.exchange.api.command.healtcheck.HealthCheckReply;
import io.gravitee.exchange.api.command.healtcheck.HealthCheckReplyPayload;
import io.gravitee.exchange.api.command.hello.HelloCommand;
import io.gravitee.exchange.api.command.hello.HelloCommandPayload;
import io.gravitee.exchange.api.command.hello.HelloReply;
import io.gravitee.exchange.api.command.hello.HelloReplyPayload;
import io.gravitee.exchange.api.command.noreply.NoReply;
import io.gravitee.exchange.api.command.primary.PrimaryCommand;
import io.gravitee.exchange.api.command.primary.PrimaryCommandPayload;
import io.gravitee.exchange.api.command.primary.PrimaryReply;
import io.gravitee.exchange.api.command.primary.PrimaryReplyPayload;
import io.gravitee.exchange.api.command.unknown.UnknownCommand;
import io.gravitee.exchange.api.command.unknown.UnknownReply;
import io.gravitee.exchange.api.websocket.protocol.ProtocolVersion;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * @author Guillaume LAMIRAND (guillaume.lamirand at graviteesource.com)
 * @author GraviteeSource Team
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class DefaultExchangeSerDeTest {

    private DefaultExchangeSerDe cut;

    @BeforeEach
    public void beforeEach() {
        cut = new DefaultExchangeSerDe(new ObjectMapper());
    }

    @Nested
    class Commands {

        @Test
        void should_deserialize_unknown_command() {
            Command<?> command = cut.deserializeAsCommand(ProtocolVersion.V1, "wrong", "{\"type\" : \"wrong\"}");
            assertThat(command).isInstanceOf(UnknownCommand.class);
        }

        private static Stream<Arguments> knownCommands() {
            return Stream.of(
                Arguments.of(
                    HelloCommand.COMMAND_TYPE,
                    "{\"id\":\"%id%\",\"type\":\"HELLO\",\"payload\":{\"targetId\":\"targetId\"}}",
                    new HelloCommand(HelloCommandPayload.builder().targetId("targetId").build())
                ),
                Arguments.of(
                    GoodByeCommand.COMMAND_TYPE,
                    "{\"id\":\"%id%\",\"type\":\"GOOD_BYE\",\"payload\":{\"targetId\":\"targetId\",\"reconnect\":true}}",
                    new GoodByeCommand(GoodByeCommandPayload.builder().targetId("targetId").reconnect(true).build())
                ),
                Arguments.of(
                    GoodByeCommand.COMMAND_TYPE,
                    "{\"id\":\"%id%\",\"type\":\"GOOD_BYE\",\"payload\":{\"targetId\":\"targetId\",\"reconnect\":false}}",
                    new GoodByeCommand(GoodByeCommandPayload.builder().targetId("targetId").reconnect(false).build())
                ),
                Arguments.of(
                    HealthCheckCommand.COMMAND_TYPE,
                    "{\"id\":\"%id%\",\"type\":\"HEALTH_CHECK\",\"payload\":{}}",
                    new HealthCheckCommand(new HealthCheckCommandPayload())
                ),
                Arguments.of(
                    PrimaryCommand.COMMAND_TYPE,
                    "{\"id\":\"%id%\",\"type\":\"PRIMARY\",\"payload\":{\"primary\":true}}",
                    new PrimaryCommand(new PrimaryCommandPayload(true))
                ),
                Arguments.of(
                    PrimaryCommand.COMMAND_TYPE,
                    "{\"id\":\"%id%\",\"type\":\"PRIMARY\",\"payload\":{\"primary\":false}}",
                    new PrimaryCommand(new PrimaryCommandPayload(false))
                ),
                Arguments.of(UnknownCommand.COMMAND_TYPE, "{\"id\":\"%id%\",\"type\":\"UNKNOWN\",\"payload\":{}}", new UnknownCommand())
            );
        }

        @ParameterizedTest
        @MethodSource("knownCommands")
        void should_deserialize_known_command(final String commandType, final String json, final Command<?> command) {
            Command<?> deserializeCommand = cut.deserializeAsCommand(
                ProtocolVersion.V1,
                commandType,
                json.replaceAll("%id%", command.getId())
            );
            assertThat(deserializeCommand).isEqualTo(command);
        }

        @Test
        void should_serialize_unknown_command() {
            UnknownCommand command = new UnknownCommand();
            String json = cut.serialize(ProtocolVersion.V1, command);
            assertThat(json).isEqualTo("{\"id\":\"%id%\",\"type\":\"UNKNOWN\",\"payload\":{}}".replaceAll("%id%", command.getId()));
        }

        @ParameterizedTest
        @MethodSource("knownCommands")
        void should_serialize_known_command(final String commandType, final String json, final Command<?> command) {
            String serializeCommand = cut.serialize(ProtocolVersion.V1, command);
            assertThat(serializeCommand).isEqualTo(json.replaceAll("%id%", command.getId()));
        }
    }

    @Nested
    class Replies {

        @Test
        void should_deserialize_unknown_reply() {
            Reply<?> reply = cut.deserializeAsReply(ProtocolVersion.V1, "wrong", "{\"type\" : \"wrong\"}");
            assertThat(reply).isInstanceOf(UnknownReply.class);
        }

        private static Stream<Arguments> knownReplies() {
            return Stream.of(
                Arguments.of(
                    HelloCommand.COMMAND_TYPE,
                    "{\"commandId\":\"commandId\",\"type\":\"HELLO\",\"commandStatus\":\"SUCCEEDED\",\"payload\":{\"targetId\":\"targetId\"}}",
                    new HelloReply("commandId", HelloReplyPayload.builder().targetId("targetId").build())
                ),
                Arguments.of(
                    GoodByeCommand.COMMAND_TYPE,
                    "{\"commandId\":\"commandId\",\"type\":\"GOOD_BYE\",\"commandStatus\":\"SUCCEEDED\",\"payload\":{\"targetId\":\"targetId\"}}",
                    new GoodByeReply("commandId", GoodByeReplyPayload.builder().targetId("targetId").build())
                ),
                Arguments.of(
                    HealthCheckCommand.COMMAND_TYPE,
                    "{\"commandId\":\"commandId\",\"type\":\"HEALTH_CHECK\",\"commandStatus\":\"SUCCEEDED\",\"payload\":{\"healthy\":true,\"detail\":null}}",
                    new HealthCheckReply("commandId", new HealthCheckReplyPayload(true, null))
                ),
                Arguments.of(
                    PrimaryCommand.COMMAND_TYPE,
                    "{\"commandId\":\"commandId\",\"type\":\"PRIMARY\",\"commandStatus\":\"SUCCEEDED\",\"payload\":{}}",
                    new PrimaryReply("commandId", new PrimaryReplyPayload())
                ),
                Arguments.of(
                    UnknownCommand.COMMAND_TYPE,
                    "{\"commandId\":\"commandId\",\"type\":\"UNKNOWN\",\"commandStatus\":\"ERROR\",\"errorDetails\":\"error\",\"payload\":{}}",
                    new UnknownReply("commandId", "error")
                ),
                Arguments.of(
                    NoReply.COMMAND_TYPE,
                    "{\"commandId\":\"commandId\",\"type\":\"NO_REPLY\",\"commandStatus\":\"ERROR\",\"errorDetails\":\"error\",\"payload\":{}}",
                    new NoReply("commandId", "error")
                )
            );
        }

        @ParameterizedTest
        @MethodSource("knownReplies")
        void should_deserialize_known_reply(final String commandType, final String json, final Reply<?> reply) {
            Reply<?> deserializeReply = cut.deserializeAsReply(ProtocolVersion.V1, commandType, json);
            assertThat(deserializeReply).isEqualTo(reply);
        }

        @Test
        void should_serialize_unknown_reply() {
            UnknownReply unknownReply = new UnknownReply("commandId", "error");
            String json = cut.serialize(ProtocolVersion.V1, unknownReply);
            assertThat(json)
                .isEqualTo(
                    "{\"commandId\":\"commandId\",\"type\":\"UNKNOWN\",\"commandStatus\":\"ERROR\",\"errorDetails\":\"error\",\"payload\":{}}"
                );
        }

        @ParameterizedTest
        @MethodSource("knownReplies")
        void should_serialize_known_reply(final String commandType, final String json, final Reply<?> reply) {
            String serializeReply = cut.serialize(ProtocolVersion.V1, reply);
            assertThat(serializeReply).isEqualTo(json);
        }
    }
}
