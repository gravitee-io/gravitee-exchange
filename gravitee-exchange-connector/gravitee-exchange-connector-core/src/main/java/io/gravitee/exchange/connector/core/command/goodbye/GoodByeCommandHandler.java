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
package io.gravitee.exchange.connector.core.command.goodbye;

import io.gravitee.exchange.api.command.CommandHandler;
import io.gravitee.exchange.api.command.goodbye.GoodByeCommand;
import io.gravitee.exchange.api.command.goodbye.GoodByeReply;
import io.gravitee.exchange.api.command.goodbye.GoodByeReplyPayload;
import io.gravitee.exchange.api.connector.ExchangeConnector;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import lombok.RequiredArgsConstructor;

/**
 * @author Guillaume LAMIRAND (guillaume.lamirand at graviteesource.com)
 * @author GraviteeSource Team
 */
@RequiredArgsConstructor
public class GoodByeCommandHandler implements CommandHandler<GoodByeCommand, GoodByeReply> {

    private final ExchangeConnector exchangeConnector;

    @Override
    public String supportType() {
        return GoodByeCommand.COMMAND_TYPE;
    }

    @Override
    public Single<GoodByeReply> handle(GoodByeCommand command) {
        return Completable
            .defer(() -> {
                if (command.getPayload().isReconnect()) {
                    return exchangeConnector.initialize();
                } else {
                    return exchangeConnector.close();
                }
            })
            .andThen(
                Single.just(
                    new GoodByeReply(command.getId(), GoodByeReplyPayload.builder().targetId(command.getPayload().getTargetId()).build())
                )
            );
    }
}
