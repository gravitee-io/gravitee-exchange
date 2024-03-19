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

import io.gravitee.exchange.api.command.Command;
import io.gravitee.exchange.api.command.Exchange;
import io.gravitee.exchange.api.command.Reply;
import io.gravitee.exchange.api.websocket.command.exception.DeserializationException;
import io.gravitee.exchange.api.websocket.command.exception.SerializationException;
import io.gravitee.exchange.api.websocket.protocol.ProtocolVersion;

/**
 * @author Guillaume LAMIRAND (guillaume.lamirand at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface ExchangeSerDe {
    <C extends Command<?>> C deserializeAsCommand(final ProtocolVersion protocolVersion, final String exchangeType, final String exchange)
        throws DeserializationException;
    <R extends Reply<?>> R deserializeAsReply(final ProtocolVersion protocolVersion, final String exchangeType, final String exchange)
        throws DeserializationException;
    String serialize(final ProtocolVersion protocolVersion, final Exchange<?> exchange) throws SerializationException;
}
