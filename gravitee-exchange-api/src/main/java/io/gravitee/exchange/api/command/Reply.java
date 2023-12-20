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

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.gravitee.exchange.api.command.goodbye.GoodByeCommand;
import io.gravitee.exchange.api.command.goodbye.GoodByeReply;
import io.gravitee.exchange.api.command.healtcheck.HealthCheckCommand;
import io.gravitee.exchange.api.command.healtcheck.HealthCheckReply;
import io.gravitee.exchange.api.command.hello.HelloCommand;
import io.gravitee.exchange.api.command.hello.HelloReply;
import io.gravitee.exchange.api.command.noreply.NoReply;
import io.gravitee.exchange.api.command.primary.PrimaryCommand;
import io.gravitee.exchange.api.command.primary.PrimaryReply;
import io.gravitee.exchange.api.command.unknown.UnknownCommand;
import io.gravitee.exchange.api.command.unknown.UnknownReply;
import io.gravitee.exchange.api.websocket.protocol.legacy.IgnoredReply;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * @author Guillaume LAMIRAND (guillaume.lamirand at graviteesource.com)
 * @author GraviteeSource Team
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type", defaultImpl = UnknownReply.class)
@JsonSubTypes(
    {
        @JsonSubTypes.Type(value = HelloReply.class, name = HelloCommand.COMMAND_TYPE),
        @JsonSubTypes.Type(value = GoodByeReply.class, name = GoodByeCommand.COMMAND_TYPE),
        @JsonSubTypes.Type(value = HealthCheckReply.class, name = HealthCheckCommand.COMMAND_TYPE),
        @JsonSubTypes.Type(value = PrimaryReply.class, name = PrimaryCommand.COMMAND_TYPE),
        @JsonSubTypes.Type(value = NoReply.class, name = NoReply.COMMAND_TYPE),
        @JsonSubTypes.Type(value = IgnoredReply.class, name = IgnoredReply.COMMAND_TYPE),
        @JsonSubTypes.Type(value = UnknownReply.class, name = UnknownCommand.COMMAND_TYPE),
    }
)
@Getter
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@JsonPropertyOrder({ "commandId", "type", "commandStatus", "errorDetails", "payload" })
public abstract class Reply<P extends Payload> extends Exchange<P> {

    /**
     * The command id the reply is related to.
     */
    protected String commandId;

    /**
     * The result status of the command.
     */
    protected CommandStatus commandStatus;

    /**
     * An optional message that can be used to give some details about the error.
     */
    protected String errorDetails;

    protected Reply(final String type) {
        super(type);
    }

    protected Reply(final String type, final String commandId, final CommandStatus commandStatus) {
        super(type);
        this.commandId = commandId;
        this.commandStatus = commandStatus;
    }

    public boolean stopOnErrorStatus() {
        return false;
    }
}
