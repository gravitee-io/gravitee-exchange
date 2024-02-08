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
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.gravitee.exchange.api.command.unknown.UnknownReply;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * @author Guillaume LAMIRAND (guillaume.lamirand at graviteesource.com)
 * @author GraviteeSource Team
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type", defaultImpl = UnknownReply.class)
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
