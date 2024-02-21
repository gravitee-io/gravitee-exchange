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
package io.gravitee.exchange.controller.core.channel;

import io.gravitee.exchange.api.controller.ControllerChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Guillaume LAMIRAND (guillaume.lamirand at graviteesource.com)
 * @author GraviteeSource Team
 */
public class LocalChannelRegistry {

    private final Map<String, ControllerChannel> channels = new ConcurrentHashMap<>();

    public void add(ControllerChannel channel) {
        channels.put(channel.id(), channel);
    }

    public boolean remove(ControllerChannel channel) {
        return channels.remove(channel.id()) != null;
    }

    public Optional<ControllerChannel> getById(String channelId) {
        return Optional.ofNullable(channels.get(channelId));
    }

    public List<ControllerChannel> getAllByTargetId(String targetId) {
        return channels.values().stream().filter(channel -> Objects.equals(targetId, channel.targetId())).toList();
    }

    public List<ControllerChannel> getAll() {
        return new ArrayList<>(channels.values());
    }
}
