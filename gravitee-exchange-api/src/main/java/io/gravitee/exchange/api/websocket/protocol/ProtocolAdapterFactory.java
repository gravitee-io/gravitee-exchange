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
package io.gravitee.exchange.api.websocket.protocol;

import io.gravitee.exchange.api.websocket.command.ExchangeSerDe;
import io.gravitee.exchange.api.websocket.protocol.legacy.LegacyProtocolAdapter;
import io.gravitee.exchange.api.websocket.protocol.v1.V1ProtocolAdapter;
import io.vertx.rxjava3.core.http.HttpServerRequest;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * @author Guillaume LAMIRAND (guillaume.lamirand at graviteesource.com)
 * @author GraviteeSource Team
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ProtocolAdapterFactory {

    public static final String REQUEST_HEADER = "X-Gravitee-Exchange-Protocol";

    public static ProtocolAdapter create(final HttpServerRequest httpServerRequest, final ExchangeSerDe exchangeSerDe) {
        String headerValue = httpServerRequest.getHeader(REQUEST_HEADER);
        ProtocolVersion protocolVersion = ProtocolVersion.parse(headerValue);
        return create(protocolVersion, exchangeSerDe);
    }

    public static ProtocolAdapter create(final ProtocolVersion protocolVersion, final ExchangeSerDe exchangeSerDe) {
        if (protocolVersion == ProtocolVersion.V1) {
            return new V1ProtocolAdapter(exchangeSerDe);
        }
        return new LegacyProtocolAdapter(exchangeSerDe);
    }
}
