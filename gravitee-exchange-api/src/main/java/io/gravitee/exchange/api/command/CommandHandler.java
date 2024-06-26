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
package io.gravitee.exchange.api.command;

import io.reactivex.rxjava3.core.Single;

/**
 * @author Guillaume LAMIRAND (guillaume.lamirand at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface CommandHandler<C extends Command<?>, R extends Reply<?>> {
    /**
     * Returns the type of command supported by this command handler.
     * The type is used to determine the right handler to use when a command need to be handled.
     * @return the type of command supported.
     */
    String supportType();

    /**
     * Method invoked when a command of the expected type is received.
     *
     * @param command the command to handle.
     * @return the reply with a status indicating if the command has been successfully handled or not.
     */
    default Single<R> handle(C command) {
        return Single.error(new RuntimeException("Handle command of type " + supportType() + " is not implemented"));
    }
}
