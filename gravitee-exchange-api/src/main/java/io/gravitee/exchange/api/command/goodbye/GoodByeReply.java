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
package io.gravitee.exchange.api.command.goodbye;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.gravitee.exchange.api.command.CommandStatus;
import io.gravitee.exchange.api.command.Reply;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * @author Guillaume LAMIRAND (guillaume.lamirand at graviteesource.com)
 * @author GraviteeSource Team
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class GoodByeReply extends Reply<GoodByeReplyPayload> {

    public GoodByeReply() {
        super(GoodByeCommand.COMMAND_TYPE);
    }

    public GoodByeReply(String commandId, GoodByeReplyPayload payload) {
        super(GoodByeCommand.COMMAND_TYPE, commandId, CommandStatus.SUCCEEDED);
        this.payload = payload;
    }

    public GoodByeReply(String commandId, String errorDetails) {
        super(GoodByeCommand.COMMAND_TYPE, commandId, CommandStatus.ERROR);
        this.errorDetails = errorDetails;
    }
}
