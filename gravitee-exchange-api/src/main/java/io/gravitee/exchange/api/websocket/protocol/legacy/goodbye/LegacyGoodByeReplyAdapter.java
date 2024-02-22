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
import io.gravitee.exchange.api.command.ReplyAdapter;
import io.gravitee.exchange.api.command.goodbye.GoodByeReplyPayload;
import io.gravitee.exchange.api.websocket.protocol.legacy.hello.HelloReply;
import io.gravitee.exchange.api.websocket.protocol.legacy.hello.HelloReplyPayload;
import io.reactivex.rxjava3.core.Single;

/**
 * @author Guillaume LAMIRAND (guillaume.lamirand at graviteesource.com)
 * @author GraviteeSource Team
 */
public class LegacyGoodByeReplyAdapter implements ReplyAdapter<GoodByeReply, io.gravitee.exchange.api.command.goodbye.GoodByeReply> {

    @Override
    public String supportType() {
        return GoodByeReply.COMMAND_TYPE;
    }

    @Override
    public Single<io.gravitee.exchange.api.command.goodbye.GoodByeReply> adapt(final GoodByeReply reply) {
        return Single.fromCallable(() -> {
            if (reply.getCommandStatus() == CommandStatus.SUCCEEDED) {
                return new io.gravitee.exchange.api.command.goodbye.GoodByeReply(
                    reply.getCommandId(),
                    new GoodByeReplyPayload(reply.getInstallationId())
                );
            } else {
                return new io.gravitee.exchange.api.command.goodbye.GoodByeReply(reply.getCommandId(), reply.getErrorDetails());
            }
        });
    }
}
