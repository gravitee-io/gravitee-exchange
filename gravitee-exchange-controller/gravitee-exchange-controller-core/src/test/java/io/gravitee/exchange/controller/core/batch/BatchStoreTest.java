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
package io.gravitee.exchange.controller.core.batch;

import static org.assertj.core.api.Assertions.assertThat;

import io.gravitee.exchange.api.command.Batch;
import io.gravitee.exchange.controller.core.batch.exception.BatchAlreadyExistsException;
import io.gravitee.exchange.controller.core.batch.exception.BatchNotExistException;
import io.gravitee.node.api.cache.Cache;
import io.gravitee.node.api.cache.CacheConfiguration;
import io.gravitee.node.plugin.cache.common.InMemoryCache;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

/**
 * @author Guillaume LAMIRAND (guillaume.lamirand at graviteesource.com)
 * @author GraviteeSource Team
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class BatchStoreTest {

    private Cache<String, Batch> store;
    private BatchStore cut;

    @BeforeEach
    public void beforeEach() {
        store = new InMemoryCache<>("store", CacheConfiguration.builder().build());
        cut = new BatchStore(store);
    }

    @Test
    void should_add_batch_to_store() {
        cut.add(Batch.builder().id("id").build()).test().awaitDone(10, TimeUnit.SECONDS).assertComplete();

        assertThat(store.containsKey("id")).isTrue();
    }

    @Test
    void should_throw_exception_when_adding_batch_without_id() {
        cut.add(Batch.builder().id(null).build()).test().awaitDone(10, TimeUnit.SECONDS).assertError(IllegalArgumentException.class);
    }

    @Test
    void should_throw_exception_when_adding_already_existing_batch() {
        Batch batch = Batch.builder().id("id").build();
        store.put(batch.id(), batch);
        cut.add(Batch.builder().id("id").build()).test().awaitDone(10, TimeUnit.SECONDS).assertError(BatchAlreadyExistsException.class);
    }

    @Test
    void should_update_batch_to_store() {
        Batch batch = Batch.builder().id("id").build();
        store.put(batch.id(), batch);
        cut.update(batch).test().awaitDone(10, TimeUnit.SECONDS).assertComplete();

        assertThat(store.containsKey("id")).isTrue();
    }

    @Test
    void should_throw_exception_when_updating_batch_without_id() {
        cut.update(Batch.builder().id(null).build()).test().awaitDone(10, TimeUnit.SECONDS).assertError(IllegalArgumentException.class);
    }

    @Test
    void should_throw_exception_when_updating_no_existing_batch() {
        cut.update(Batch.builder().id("id").build()).test().awaitDone(10, TimeUnit.SECONDS).assertError(BatchNotExistException.class);
    }

    @Test
    void should_get_batch_from_store() {
        Batch batch = Batch.builder().id("id").build();
        store.put(batch.id(), batch);
        cut.getById("id").test().awaitDone(10, TimeUnit.SECONDS).assertValue(batch);
    }

    @Test
    void should_clear_store() {
        Batch batch = Batch.builder().id("id").build();
        store.put(batch.id(), batch);
        cut.clear();
        assertThat(store.containsKey("id")).isFalse();
    }
}
