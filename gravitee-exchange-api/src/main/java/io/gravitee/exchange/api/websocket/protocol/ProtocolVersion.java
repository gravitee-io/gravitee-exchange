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
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Function;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/**
 * @author Guillaume LAMIRAND (guillaume.lamirand at graviteesource.com)
 * @author GraviteeSource Team
 */
@RequiredArgsConstructor
@Getter
@Accessors(fluent = true)
public enum ProtocolVersion {
    LEGACY("legacy", LegacyProtocolAdapter::new),
    V1("v1", V1ProtocolAdapter::new);

    private final String version;
    private final Function<ExchangeSerDe, ProtocolAdapter> adapterFactory;

    public static ProtocolVersion parse(final String version) {
        if (version == null) {
            return LEGACY;
        }
        return Arrays
            .stream(ProtocolVersion.values())
            .filter(protocolVersion -> Objects.equals(protocolVersion.version, version))
            .findFirst()
            .orElse(ProtocolVersion.LEGACY);
    }
}
