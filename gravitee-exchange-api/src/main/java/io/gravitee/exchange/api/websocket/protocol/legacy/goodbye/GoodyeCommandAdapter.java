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

import io.gravitee.exchange.api.channel.exception.ChannelClosedException;
import io.gravitee.exchange.api.command.CommandAdapter;
import io.gravitee.exchange.api.command.goodbye.GoodByeReply;
import io.reactivex.rxjava3.core.Single;

/**
 * @author Guillaume LAMIRAND (guillaume.lamirand at graviteesource.com)
 * @author GraviteeSource Team
 */
public class GoodyeCommandAdapter
    implements CommandAdapter<io.gravitee.exchange.api.command.goodbye.GoodByeCommand, GoodByeCommand, GoodByeReply> {

    @Override
    public String supportType() {
        return io.gravitee.exchange.api.command.goodbye.GoodByeCommand.COMMAND_TYPE;
    }

    @Override
    public Single<GoodByeCommand> adapt(final io.gravitee.exchange.api.command.goodbye.GoodByeCommand command) {
        return Single.fromCallable(() -> {
            // The legacy protocol doesn't support reconnect option, we ignore the command to generate a websocket.close()
            // from the controller instead of doing it on controller.
            if (command.getPayload().isReconnect()) {
                throw new ChannelClosedException();
            }
            return new GoodByeCommand(command.getId());
        });
    }
}
