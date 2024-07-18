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
package io.gravitee.exchange.api.controller.metrics;

import io.gravitee.exchange.api.batch.Batch;
import io.gravitee.exchange.api.batch.BatchStatus;
import java.time.Instant;
import lombok.Builder;

@Builder
public record BatchMetric(
    String id,
    String key,
    String targetId,
    BatchStatus status,
    String errorDetails,
    Integer maxRetry,
    Integer retry,
    Instant lastRetryAt
) {
    public BatchMetric(Batch batch) {
        this(
            batch.id(),
            batch.key(),
            batch.targetId(),
            batch.status(),
            batch.errorDetails(),
            batch.maxRetry(),
            batch.retry(),
            batch.lastRetryAt()
        );
    }
}
