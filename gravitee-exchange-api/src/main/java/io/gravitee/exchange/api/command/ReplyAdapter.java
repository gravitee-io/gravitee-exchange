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
public interface ReplyAdapter<R1 extends Reply<?>, R2 extends Reply<?>> {
    /**
     * Returns the type of reply handled by this adapter
     *
     * @return the type of reply supported.
     */
    String supportType();

    /**
     * Method invoked when receiving the reply
     *
     * @param targetId the target id of this command. Could be <code>null</code> during hello handshake.
     * @param reply the original {@link Reply} to adapt
     * @return the reply adapted
     */
    default Single<R2> adapt(final String targetId, final R1 reply) {
        return (Single<R2>) Single.just(reply);
    }
}
