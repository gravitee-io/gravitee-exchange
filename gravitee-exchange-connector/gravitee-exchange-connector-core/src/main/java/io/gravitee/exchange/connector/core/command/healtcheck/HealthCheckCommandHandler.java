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
package io.gravitee.exchange.connector.core.command.healtcheck;

import io.gravitee.exchange.api.command.CommandHandler;
import io.gravitee.exchange.api.command.healtcheck.HealthCheckCommand;
import io.gravitee.exchange.api.command.healtcheck.HealthCheckReply;
import io.gravitee.exchange.api.command.healtcheck.HealthCheckReplyPayload;
import io.gravitee.exchange.api.connector.ExchangeConnector;
import io.reactivex.rxjava3.core.Single;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Guillaume LAMIRAND (guillaume.lamirand at graviteesource.com)
 * @author GraviteeSource Team
 */
@RequiredArgsConstructor
@Slf4j
public class HealthCheckCommandHandler implements CommandHandler<HealthCheckCommand, HealthCheckReply> {

    private final ExchangeConnector exchangeConnector;

    @Override
    public String supportType() {
        return HealthCheckCommand.COMMAND_TYPE;
    }

    @Override
    public Single<HealthCheckReply> handle(HealthCheckCommand command) {
        return Single.fromCallable(() -> {
            log.debug("Health check command received for target '{}'", exchangeConnector.targetId());
            return new HealthCheckReply(command.getId(), HealthCheckReplyPayload.builder().healthy(true).build());
        });
    }
}
