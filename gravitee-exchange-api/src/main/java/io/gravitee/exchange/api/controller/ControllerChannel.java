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
package io.gravitee.exchange.api.controller;

import io.gravitee.exchange.api.channel.Channel;

/**
 * @author Guillaume LAMIRAND (guillaume.lamirand at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface ControllerChannel extends Channel {
    /**
     * Return <code>true</code> is the current channel is active and ready to receive new commands, <code>false</code> otherwise.
     *
     * @return status of the channel.
     */
    boolean isActive();

    /**
     * Enforce the active status of this controller channel.
     */
    void enforceActiveStatus(final boolean isActive);
}
