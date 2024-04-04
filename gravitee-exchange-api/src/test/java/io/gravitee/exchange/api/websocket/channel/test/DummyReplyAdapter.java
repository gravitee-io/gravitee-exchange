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

import io.gravitee.exchange.api.command.ReplyAdapter;
import io.reactivex.rxjava3.core.Single;
import io.vertx.junit5.Checkpoint;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class DummyReplyAdapter implements ReplyAdapter<AdaptedDummyReply, DummyReply> {

    private final Checkpoint checkpoint;

    @Override
    public String supportType() {
        return AdaptedDummyCommand.COMMAND_TYPE;
    }

    @Override
    public Single<DummyReply> adapt(final String targetId, final AdaptedDummyReply reply) {
        checkpoint.flag();
        return Single.just(new DummyReply(reply.getCommandId(), reply.getPayload()));
    }
}
