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

import io.gravitee.exchange.api.command.Command;

public class DummyCommand extends Command<DummyPayload> {

    public static final String COMMAND_TYPE = "DUMMY";

    public DummyCommand() {
        super(COMMAND_TYPE);
    }

    public DummyCommand(final DummyPayload dummyPayload) {
        this();
        this.payload = dummyPayload;
    }
}
