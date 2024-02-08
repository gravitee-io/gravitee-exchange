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

import io.gravitee.exchange.api.command.Command;
import io.gravitee.exchange.api.command.Exchange;
import io.gravitee.exchange.api.command.Reply;
import javax.annotation.Nullable;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * @author Guillaume LAMIRAND (guillaume.lamirand at graviteesource.com)
 * @author GraviteeSource Team
 */
@Builder
@Getter
@Accessors(fluent = true)
public class ProtocolExchange {

    @Builder.Default
    private final Type type = Type.UNKNOWN;

    @Nullable
    private final String exchangeType;

    private final Exchange<?> exchange;

    public <C extends Command<?>> C asCommand() {
        return (C) exchange;
    }

    public <R extends Reply<?>> R asReply() {
        return (R) exchange;
    }

    public enum Type {
        COMMAND,
        REPLY,
        UNKNOWN,
    }
}
