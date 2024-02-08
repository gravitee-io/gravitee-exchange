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
package io.gravitee.exchange.api.websocket.protocol.legacy;

import io.gravitee.exchange.api.command.Command;
import io.gravitee.exchange.api.command.CommandAdapter;
import io.gravitee.exchange.api.command.Reply;
import io.gravitee.exchange.api.command.ReplyAdapter;
import io.gravitee.exchange.api.command.primary.PrimaryCommand;
import io.gravitee.exchange.api.command.primary.PrimaryCommandPayload;
import io.gravitee.exchange.api.websocket.command.ExchangeSerDe;
import io.gravitee.exchange.api.websocket.protocol.ProtocolAdapter;
import io.gravitee.exchange.api.websocket.protocol.ProtocolExchange;
import io.gravitee.exchange.api.websocket.protocol.ProtocolVersion;
import io.gravitee.exchange.api.websocket.protocol.legacy.goodbye.LegacyGoodByeReplyAdapter;
import io.gravitee.exchange.api.websocket.protocol.legacy.goodbye.LegacyGoodyeCommandAdapter;
import io.gravitee.exchange.api.websocket.protocol.legacy.healthcheck.LegacyHealthCheckCommandAdapter;
import io.gravitee.exchange.api.websocket.protocol.legacy.hello.LegacyHelloCommandAdapter;
import io.gravitee.exchange.api.websocket.protocol.legacy.hello.LegacyHelloReplyAdapter;
import io.gravitee.exchange.api.websocket.protocol.legacy.ignored.LegacyNoReplyAdapter;
import io.gravitee.exchange.api.websocket.protocol.legacy.primary.LegacyPrimaryCommandAdapter;
import io.vertx.rxjava3.core.buffer.Buffer;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;

/**
 * @author Guillaume LAMIRAND (guillaume.lamirand at graviteesource.com)
 * @author GraviteeSource Team
 */

@RequiredArgsConstructor
public class LegacyProtocolAdapter implements ProtocolAdapter {

    private static final String COMMAND_PREFIX = "command:";
    private static final String REPLY_PREFIX = "reply:";
    private static final String PRIMARY_MESSAGE = "primary: true";
    private static final String REPLICA_MESSAGE = "replica: true";
    private final ExchangeSerDe exchangeSerDe;

    @Override
    public List<CommandAdapter<? extends Command<?>, ? extends Command<?>, ? extends Reply<?>>> commandAdapters() {
        return List.of(
            new LegacyHelloCommandAdapter(),
            new LegacyGoodyeCommandAdapter(),
            new LegacyHealthCheckCommandAdapter(),
            new LegacyPrimaryCommandAdapter()
        );
    }

    @Override
    public List<ReplyAdapter<? extends Reply<?>, ? extends Reply<?>>> replyAdapters() {
        return List.of(new LegacyHelloReplyAdapter(), new LegacyGoodByeReplyAdapter(), new LegacyNoReplyAdapter());
    }

    @Override
    public Buffer write(final ProtocolExchange websocketExchange) {
        if (websocketExchange.type() == ProtocolExchange.Type.COMMAND) {
            if (Objects.equals(websocketExchange.exchangeType(), PrimaryCommand.COMMAND_TYPE)) {
                PrimaryCommand primaryCommand = (PrimaryCommand) websocketExchange.exchange();
                if (primaryCommand.getPayload().primary()) {
                    return Buffer.buffer(PRIMARY_MESSAGE);
                } else {
                    return Buffer.buffer(REPLICA_MESSAGE);
                }
            } else {
                return Buffer.buffer(COMMAND_PREFIX + exchangeSerDe.serialize(ProtocolVersion.LEGACY, websocketExchange.exchange()));
            }
        } else if (websocketExchange.type() == ProtocolExchange.Type.REPLY) {
            return Buffer.buffer(REPLY_PREFIX + exchangeSerDe.serialize(ProtocolVersion.LEGACY, websocketExchange.exchange()));
        }
        return Buffer.buffer();
    }

    @Override
    public ProtocolExchange read(final Buffer buffer) {
        String incoming = buffer.toString();
        if (incoming.startsWith(PRIMARY_MESSAGE)) {
            return ProtocolExchange
                .builder()
                .type(ProtocolExchange.Type.COMMAND)
                .exchangeType(PrimaryCommand.COMMAND_TYPE)
                .exchange(new PrimaryCommand(new PrimaryCommandPayload(true)))
                .build();
        } else if (incoming.startsWith(REPLICA_MESSAGE)) {
            return ProtocolExchange
                .builder()
                .type(ProtocolExchange.Type.COMMAND)
                .exchangeType(PrimaryCommand.COMMAND_TYPE)
                .exchange(new PrimaryCommand(new PrimaryCommandPayload(false)))
                .build();
        } else if (incoming.startsWith(COMMAND_PREFIX)) {
            String exchange = incoming.replace(COMMAND_PREFIX, "");
            return ProtocolExchange
                .builder()
                .type(ProtocolExchange.Type.COMMAND)
                .exchange(exchangeSerDe.deserializeAsCommand(ProtocolVersion.LEGACY, null, exchange))
                .build();
        } else if (incoming.startsWith(REPLY_PREFIX)) {
            String exchange = incoming.replace(REPLY_PREFIX, "");
            return ProtocolExchange
                .builder()
                .type(ProtocolExchange.Type.REPLY)
                .exchange(exchangeSerDe.deserializeAsReply(ProtocolVersion.LEGACY, null, exchange))
                .build();
        }
        return ProtocolExchange.builder().type(ProtocolExchange.Type.UNKNOWN).build();
    }
}
