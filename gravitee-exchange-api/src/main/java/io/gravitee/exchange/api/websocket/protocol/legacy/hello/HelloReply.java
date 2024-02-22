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
package io.gravitee.exchange.api.websocket.protocol.legacy.hello;

import io.gravitee.exchange.api.command.CommandStatus;
import io.gravitee.exchange.api.command.Reply;
import lombok.Getter;
import lombok.Setter;

/**
 * @author Guillaume LAMIRAND (guillaume.lamirand at graviteesource.com)
 * @author GraviteeSource Team
 */
public class HelloReply extends Reply<HelloReplyPayload> {

    public static final String COMMAND_TYPE = "HELLO_REPLY";

    @Getter
    @Setter
    protected String message;

    public HelloReply() {
        super(COMMAND_TYPE);
    }

    public HelloReply(String commandId, String errorDetails) {
        super(COMMAND_TYPE, commandId, CommandStatus.ERROR);
        this.message = errorDetails;
        this.errorDetails = errorDetails;
    }

    public HelloReply(String commandId, HelloReplyPayload helloReplyPayload) {
        super(COMMAND_TYPE, commandId, CommandStatus.SUCCEEDED);
        this.payload = helloReplyPayload;
    }

    @Override
    public boolean stopOnErrorStatus() {
        return true;
    }
}
