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
package io.gravitee.exchange.api.websocket.protocol.legacy.primary;

import io.gravitee.exchange.api.channel.exception.ChannelTimeoutException;
import io.gravitee.exchange.api.command.Command;
import io.gravitee.exchange.api.command.CommandAdapter;
import io.gravitee.exchange.api.command.primary.PrimaryCommand;
import io.gravitee.exchange.api.command.primary.PrimaryReply;
import io.gravitee.exchange.api.command.primary.PrimaryReplyPayload;
import io.reactivex.rxjava3.core.Single;

/**
 * @author Guillaume LAMIRAND (guillaume.lamirand at graviteesource.com)
 * @author GraviteeSource Team
 */
public class PrimaryCommandAdapter implements CommandAdapter<PrimaryCommand, PrimaryCommand, PrimaryReply> {

    @Override
    public String supportType() {
        return PrimaryCommand.COMMAND_TYPE;
    }

    @Override
    public Single<PrimaryCommand> adapt(final String targetId, final PrimaryCommand command) {
        return Single.fromCallable(() -> {
            command.setReplyTimeoutMs(0);
            return command;
        });
    }

    @Override
    public Single<PrimaryReply> onError(final Command<?> command, final Throwable throwable) {
        return Single.defer(() -> {
            if (throwable instanceof ChannelTimeoutException) {
                return Single.just(new PrimaryReply(command.getId(), new PrimaryReplyPayload()));
            }
            return Single.error(throwable);
        });
    }
}
