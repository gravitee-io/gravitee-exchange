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
package io.gravitee.exchange.controller.core.channel.primary;

import io.gravitee.node.api.cache.Cache;
import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class PrimaryChannelCandidateRegistry {

    private final Cache<String, Set<String>> store;

    public Set<String> get(final String targetId) {
        if (targetId == null) {
            throw new IllegalArgumentException("Target id cannot be null");
        }
        return store.get(targetId);
    }

    public void put(final String targetId, final String channelId) {
        if (targetId == null) {
            throw new IllegalArgumentException("Target id cannot be null");
        }
        if (channelId == null) {
            throw new IllegalArgumentException("Channel id cannot be null");
        }
        store.compute(
            targetId,
            (key, channels) -> {
                if (channels == null) {
                    channels = new HashSet<>();
                }
                channels.add(channelId);
                return channels;
            }
        );
    }

    public void remove(final String targetId, final String channelId) {
        if (targetId == null) {
            throw new IllegalArgumentException("Target id cannot be null");
        }
        if (channelId == null) {
            throw new IllegalArgumentException("Channel id cannot be null");
        }
        store.compute(
            targetId,
            (key, channels) -> {
                if (channels != null) {
                    channels.remove(channelId);

                    if (channels.isEmpty()) {
                        return null;
                    }
                }
                return channels;
            }
        );
    }
}
