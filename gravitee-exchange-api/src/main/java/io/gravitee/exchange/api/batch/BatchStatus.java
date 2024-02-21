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

/**
 * @author Guillaume LAMIRAND (guillaume.lamirand at graviteesource.com)
 * @author GraviteeSource Team
 */
public enum BatchStatus {
    /**
     * The batch was just created
     */
    CREATED,
    /**
     * The batch is waiting for one of its commands to be retried
     */
    PENDING,
    /**
     * The batch is currently being processed
     */
    IN_PROGRESS,
    /**
     * The batch has been processed successfully
     */
    SUCCEEDED,
    /**
     * The batch has been processed but ends in error even after multiple retries
     */
    ERROR,
}
