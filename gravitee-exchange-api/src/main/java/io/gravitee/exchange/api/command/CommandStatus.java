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

import java.util.List;

/**
 * @author Guillaume LAMIRAND (guillaume.lamirand at graviteesource.com)
 * @author GraviteeSource Team
 */
public enum CommandStatus {
    /**
     * The command is waiting for processing.
     */
    PENDING,

    /**
     * The command is processing.
     */
    IN_PROGRESS,

    /**
     * The command have been successfully processed.
     */
    SUCCEEDED,

    /**
     * The command got an unexpected error.
     */
    ERROR;

    public static CommandStatus merge(List<CommandStatus> commandStatuses) {
        boolean pending = false;

        for (CommandStatus commandStatus : commandStatuses) {
            if (commandStatus == ERROR) {
                return ERROR;
            } else if (commandStatus == PENDING) {
                pending = true;
            }
        }

        if (pending) {
            return PENDING;
        }

        return SUCCEEDED;
    }
}
