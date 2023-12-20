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
import io.reactivex.rxjava3.core.SingleSource;

/**
 * @author Guillaume LAMIRAND (guillaume.lamirand at graviteesource.com)
 * @author GraviteeSource Team
 */
@FunctionalInterface
public interface ReplyHandler<C extends Command<?>, D extends Command<?>, R extends Reply<?>> {
    /**
     * Returns the type of command handled by this reply handler.
     *
     * @return the type of command handled.
     */
    String handleType();

    /**
     * Method invoked before sending the command in order to fill extra information into the command.

     * @return the command with all necessary information.
     */
    default Single<D> decorate(C command) {
        return (Single<D>) Single.just(command);
    }

    /**
     * Method invoke after a reply has been received.
     *
     * @param reply the reply.
     * @return the same reply altered if necessary.
     */
    default Single<R> handle(R reply) {
        return Single.just(reply);
    }

    /**
     * Method invoke when an error is raised when dealing with a reply.
     *
     * @param throwable a throwable
     */
    default Single<R> handleError(final C command, final Throwable throwable) {
        return Single.error(throwable);
    }
}
