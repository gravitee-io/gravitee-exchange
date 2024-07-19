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
import io.gravitee.exchange.api.command.CommandAdapter;
import io.reactivex.rxjava3.core.Single;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class DummyCommandAdapter implements CommandAdapter<DummyCommand, AdaptedDummyCommand, DummyReply> {

    private final Consumer<Command<?>> consumer;

    @Override
    public String supportType() {
        return DummyCommand.COMMAND_TYPE;
    }

    @Override
    public Single<AdaptedDummyCommand> adapt(final String targetId, final DummyCommand command) {
        consumer.accept(command);
        return Single.just(new AdaptedDummyCommand(command.getId(), command.getPayload()));
    }
}
