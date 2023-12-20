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
package io.gravitee.exchange.api.command;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.gravitee.common.utils.UUID;
import io.gravitee.exchange.api.command.goodbye.GoodByeCommand;
import io.gravitee.exchange.api.command.healtcheck.HealthCheckCommand;
import io.gravitee.exchange.api.command.hello.HelloCommand;
import io.gravitee.exchange.api.command.primary.PrimaryCommand;
import io.gravitee.exchange.api.command.unknown.UnknownCommand;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * @author Guillaume LAMIRAND (guillaume.lamirand at graviteesource.com)
 * @author GraviteeSource Team
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "type",
    defaultImpl = UnknownCommand.class
)
@JsonSubTypes(
    {
        @JsonSubTypes.Type(value = HelloCommand.class, name = HelloCommand.COMMAND_TYPE),
        @JsonSubTypes.Type(value = GoodByeCommand.class, name = GoodByeCommand.COMMAND_TYPE),
        @JsonSubTypes.Type(value = HealthCheckCommand.class, name = HealthCheckCommand.COMMAND_TYPE),
        @JsonSubTypes.Type(value = PrimaryCommand.class, name = PrimaryCommand.COMMAND_TYPE),
        @JsonSubTypes.Type(value = UnknownCommand.class, name = UnknownCommand.COMMAND_TYPE),
    }
)
@NoArgsConstructor
@Getter
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@JsonPropertyOrder({ "id", "type", "payload" })
public abstract class Command<P extends Payload> extends Exchange<P> {

    public static final int COMMAND_REPLY_TIMEOUT_MS = 60_000;

    /**
     * The command id.
     */
    protected String id;

    @Setter
    @JsonIgnore
    protected int replyTimeoutMs = COMMAND_REPLY_TIMEOUT_MS;

    protected Command(String type) {
        super(type);
        this.id = UUID.random().toString();
    }
}
