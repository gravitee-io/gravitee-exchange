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
package io.gravitee.exchange.api.websocket.protocol.legacy.goodbye;

import io.gravitee.exchange.api.command.CommandStatus;
import io.gravitee.exchange.api.command.Reply;
import lombok.Getter;
import lombok.Setter;

/**
 * @author Guillaume LAMIRAND (guillaume.lamirand at graviteesource.com)
 * @author GraviteeSource Team
 */
public class GoodByeReply extends Reply<GoodByeReplyPayload> {

    public static final String COMMAND_TYPE = "GOODBYE_REPLY";

    @Setter
    @Getter
    private String installationId;

    public GoodByeReply() {
        this(null, null);
    }

    public GoodByeReply(String commandId, CommandStatus commandStatus) {
        super(COMMAND_TYPE, commandId, commandStatus);
    }

    @Override
    public boolean stopOnErrorStatus() {
        return true;
    }
}
