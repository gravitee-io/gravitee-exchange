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
public interface CommandAdapter<C1 extends Command<?>, C2 extends Command<?>, R extends Reply<?>> {
    /**
     * Returns the type of command supported by this adapter.
     *
     * @return the type of command supported.
     */
    String supportType();

    /**
     * Method invoked before sending the command.

     * @return the command adapted
     */
    default Single<C2> adapt(C1 command) {
        return (Single<C2>) Single.just(command);
    }

    /**
     * Method invoke when an error is raised when sending a command.
     *
     * @param throwable a throwable
     */
    default Single<R> onError(final Command<?> command, final Throwable throwable) {
        return Single.error(throwable);
    }
}
