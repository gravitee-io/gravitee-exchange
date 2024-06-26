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
package io.gravitee.exchange.api.batch;

import io.gravitee.common.utils.UUID;
import io.gravitee.exchange.api.command.CommandStatus;
import io.gravitee.exchange.api.command.Reply;
import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * @author Guillaume LAMIRAND (guillaume.lamirand at graviteesource.com)
 * @author GraviteeSource Team
 */
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Accessors(fluent = true, chain = true)
public class Batch implements Serializable {

    public static final int DEFAULT_MAX_RETRY = 5;

    /**
     * The ID of the batch
     */
    @Builder.Default
    private String id = UUID.random().toString();

    /**
     * The key to identify the batch
     */
    private String key;

    /**
     * The target id of the batch
     */
    private String targetId;

    /**
     * The list of commands for this batch
     */
    private List<BatchCommand> batchCommands;

    /**
     * The status of the batch
     */
    @Builder.Default
    private BatchStatus status = BatchStatus.CREATED;

    /**
     * The message related to the status of the batch. Ex: the error message when a command has failed.
     */
    private String errorDetails;

    @Builder.Default
    private int maxRetry = DEFAULT_MAX_RETRY;

    private Integer retry;

    private Instant lastRetryAt;

    public boolean shouldRetryNow(Long retryDelayMs) {
        Instant now = Instant.now();
        return Optional
            .ofNullable(this.lastRetryAt)
            .map(t -> now.compareTo(t.plusMillis(this.retry * retryDelayMs)))
            .map(compare -> compare >= 0)
            .orElse(true);
    }

    public Batch start() {
        Instant now = Instant.now();
        this.status = BatchStatus.IN_PROGRESS;
        this.retry = Optional.ofNullable(this.retry).map(r -> r + 1).orElse(0);
        this.lastRetryAt = now;
        return this;
    }

    public Batch reset() {
        if (this.status == BatchStatus.IN_PROGRESS) {
            this.status = BatchStatus.PENDING;
            this.retry = 0;
            this.lastRetryAt = null;
            return this;
        }
        return this;
    }

    public Batch markCommandInProgress(final String commandId) {
        return markCommand(commandId, CommandStatus.IN_PROGRESS, null);
    }

    public Batch markCommandInError(final String commandId, final String errorDetails) {
        this.errorDetails = errorDetails;
        return markCommand(commandId, CommandStatus.ERROR, errorDetails);
    }

    private Batch markCommand(final String commandId, final CommandStatus commandStatus, final String errorDetails) {
        this.batchCommands.forEach(c -> {
                if (Objects.equals(c.command().getId(), commandId)) {
                    c.status(commandStatus).errorDetails(errorDetails);
                }
            });
        this.status = computeStatus();
        return this;
    }

    public Batch setCommandReply(final String commandId, final Reply<?> reply) {
        this.batchCommands.forEach(c -> {
                if (Objects.equals(c.command().getId(), commandId)) {
                    c.status(reply.getCommandStatus()).reply(reply).errorDetails(reply.getErrorDetails());
                }
            });
        this.status = computeStatus();
        return this;
    }

    private BatchStatus computeStatus() {
        boolean isActionSucceeded =
            this.batchCommands.stream().allMatch(batchCommand -> batchCommand.status().equals(CommandStatus.SUCCEEDED));
        if (isActionSucceeded) {
            return BatchStatus.SUCCEEDED;
        }

        boolean isActionOnError = this.batchCommands.stream().anyMatch(batchCommand -> batchCommand.status().equals(CommandStatus.ERROR));
        boolean thresholdReached = this.retry >= this.maxRetry;
        if (isActionOnError) {
            if (thresholdReached) {
                return BatchStatus.ERROR;
            }
            return BatchStatus.PENDING;
        }

        return BatchStatus.IN_PROGRESS;
    }
}
