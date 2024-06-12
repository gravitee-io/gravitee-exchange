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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.gravitee.node.api.cache.Cache;
import io.gravitee.node.api.cache.CacheConfiguration;
import io.gravitee.node.plugin.cache.common.InMemoryCache;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

/**
 * @author Guillaume LAMIRAND (guillaume.lamirand at graviteesource.com)
 * @author GraviteeSource Team
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class PrimaryChannelCandidateStoreTest {

    private Cache<String, Set<String>> store;
    private PrimaryChannelCandidateStore cut;

    @BeforeEach
    public void beforeEach() {
        store = new InMemoryCache<>("store", CacheConfiguration.builder().build());
        cut = new PrimaryChannelCandidateStore(store);
    }

    @Test
    void should_get_from_store() {
        store.put("targetId", Set.of("channelId", "channelId2"));
        assertThat(cut.get("targetId")).containsOnly("channelId", "channelId2");
    }

    @Test
    void should_get_null_from_store() {
        assertThat(cut.get("targetId")).isNull();
    }

    @Test
    void should_throw_exception_when_getting_without_target_id() {
        assertThatThrownBy(() -> cut.get(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_put_to_store() {
        cut.put("targetId", "channelId");
        assertThat(store.containsKey("targetId")).isTrue();
    }

    @Test
    void should_throw_exception_when_putting_without_target_id() {
        assertThatThrownBy(() -> cut.put(null, "channelId")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_throw_exception_when_putting_without_channel_id() {
        assertThatThrownBy(() -> cut.put("targetId", null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_remove_from_store() {
        store.put("targetId", new HashSet<>(List.of("channelId")));
        cut.remove("targetId", "channelId");
        assertThat(store.containsKey("targetId")).isFalse();
    }

    @Test
    void should_remove_only_one_channel_from_store() {
        store.put("targetId", new HashSet<>(List.of("channelId", "channelId2")));
        cut.remove("targetId", "channelId");
        assertThat(store.containsKey("targetId")).isTrue();
        assertThat(store.get("targetId")).containsOnly("channelId2");
    }

    @Test
    void should_throw_exception_when_removing_without_target_id() {
        assertThatThrownBy(() -> cut.remove(null, "channelId")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_throw_exception_when_removing_without_channel_id() {
        assertThatThrownBy(() -> cut.remove("targetId", null)).isInstanceOf(IllegalArgumentException.class);
    }
}
