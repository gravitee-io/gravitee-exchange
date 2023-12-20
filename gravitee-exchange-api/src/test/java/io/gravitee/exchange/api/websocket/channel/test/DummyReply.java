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
package io.gravitee.exchange.api.websocket.channel.test;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import io.gravitee.exchange.api.command.CommandStatus;
import io.gravitee.exchange.api.command.Reply;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonSubTypes({ @JsonSubTypes.Type(value = DummyReply.class, name = DummyCommand.COMMAND_TYPE) })
public class DummyReply extends Reply<DummyPayload> {

    public DummyReply() {
        super(DummyCommand.COMMAND_TYPE);
    }

    public DummyReply(final String commandId, final DummyPayload dummyPayload) {
        super(DummyCommand.COMMAND_TYPE, commandId, CommandStatus.SUCCEEDED);
        this.payload = dummyPayload;
    }
}
