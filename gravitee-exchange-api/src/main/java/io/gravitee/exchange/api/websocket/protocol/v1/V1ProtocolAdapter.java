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

import io.gravitee.exchange.api.command.goodbye.GoodByeCommand;
import io.gravitee.exchange.api.command.hello.HelloCommand;
import io.gravitee.exchange.api.websocket.command.ExchangeSerDe;
import io.gravitee.exchange.api.websocket.protocol.ProtocolAdapter;
import io.gravitee.exchange.api.websocket.protocol.ProtocolExchange;
import io.gravitee.exchange.api.websocket.protocol.ProtocolVersion;
import io.vertx.rxjava3.core.buffer.Buffer;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;

/**
 * @author Guillaume LAMIRAND (guillaume.lamirand at graviteesource.com)
 * @author GraviteeSource Team
 */
@RequiredArgsConstructor
public class V1ProtocolAdapter implements ProtocolAdapter {

    private static final String TYPE_PREFIX = "t:";
    private static final String EXCHANGE_TYPE_PREFIX = "et:";
    private static final String EXCHANGE_PREFIX = "e:";
    private static final String SEPARATOR = ";;";
    private final ExchangeSerDe exchangeSerDe;

    @Override
    public Buffer write(ProtocolExchange websocketExchange) {
        List<String> event = new ArrayList<>();
        if (websocketExchange.type() != null) {
            event.add(TYPE_PREFIX + websocketExchange.type().name());
        }
        if (websocketExchange.exchangeType() != null) {
            event.add(EXCHANGE_TYPE_PREFIX + websocketExchange.exchangeType());
        }
        if (websocketExchange.exchange() != null) {
            event.add(EXCHANGE_PREFIX + exchangeSerDe.serialize(ProtocolVersion.V1, websocketExchange.exchange()));
        }
        return Buffer.buffer(String.join(SEPARATOR, event));
    }

    @Override
    public ProtocolExchange read(Buffer buffer) {
        final String bufferStr = buffer.toString();
        final String[] lines = bufferStr.split(SEPARATOR);
        ProtocolExchange.Type type = ProtocolExchange.Type.UNKNOWN;
        String exchangeType = null;
        String exchange = null;
        for (String line : lines) {
            if (line.startsWith(TYPE_PREFIX)) {
                try {
                    type = ProtocolExchange.Type.valueOf(extractFrom(line, TYPE_PREFIX));
                } catch (Exception e) {
                    type = ProtocolExchange.Type.UNKNOWN;
                }
            } else if (line.startsWith(EXCHANGE_TYPE_PREFIX)) {
                exchangeType = extractFrom(line, EXCHANGE_TYPE_PREFIX);
            } else if (line.startsWith(EXCHANGE_PREFIX)) {
                exchange = extractFrom(line, EXCHANGE_PREFIX);
            }
        }

        ProtocolExchange.ProtocolExchangeBuilder exchangeObjectBuilder = ProtocolExchange.builder().type(type).exchangeType(exchangeType);

        if (exchange != null) {
            if (ProtocolExchange.Type.COMMAND == type) {
                exchangeObjectBuilder.exchange(exchangeSerDe.deserializeAsCommand(ProtocolVersion.V1, exchangeType, exchange));
            } else if (ProtocolExchange.Type.REPLY == type) {
                exchangeObjectBuilder.exchange(exchangeSerDe.deserializeAsReply(ProtocolVersion.V1, exchangeType, exchange));
            }
        }
        return exchangeObjectBuilder.build();
    }

    private String extractFrom(final String line, final String prefix) {
        return line.substring(prefix.length()).trim();
    }
}
