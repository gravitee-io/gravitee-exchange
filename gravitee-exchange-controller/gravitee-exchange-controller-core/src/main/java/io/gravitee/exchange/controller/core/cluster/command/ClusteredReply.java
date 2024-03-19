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
package io.gravitee.exchange.controller.core.cluster.command;

import io.gravitee.exchange.api.command.Reply;
import io.gravitee.exchange.controller.core.cluster.exception.ControllerClusterException;
import java.io.Serializable;
import lombok.Getter;

/**
 * @author Guillaume LAMIRAND (guillaume.lamirand at graviteesource.com)
 * @author GraviteeSource Team
 */
@Getter
public class ClusteredReply<R extends Reply<?>> implements Serializable {

    private final String commandId;

    private R reply;

    private ControllerClusterException controllerClusterException;

    public ClusteredReply(final String commandId, final R reply) {
        this.commandId = commandId;
        this.reply = reply;
    }

    public ClusteredReply(final String commandId, final ControllerClusterException controllerClusterException) {
        this.commandId = commandId;
        this.controllerClusterException = controllerClusterException;
    }

    public boolean isError() {
        return controllerClusterException != null;
    }
}
