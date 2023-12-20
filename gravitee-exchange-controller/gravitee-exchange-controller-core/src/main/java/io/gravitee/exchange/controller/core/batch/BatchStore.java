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

import io.gravitee.exchange.api.command.Batch;
import io.gravitee.exchange.api.command.BatchStatus;
import io.gravitee.exchange.controller.core.batch.exception.BatchAlreadyExistsException;
import io.gravitee.exchange.controller.core.batch.exception.BatchNotExistException;
import io.gravitee.node.api.cache.Cache;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.util.Objects;
import lombok.RequiredArgsConstructor;

/**
 * @author Guillaume LAMIRAND (guillaume.lamirand at graviteesource.com)
 * @author GraviteeSource Team
 */
@RequiredArgsConstructor
public class BatchStore {

    private final Cache<String, Batch> store;

    public void clear() {
        store.clear();
    }

    public Maybe<Batch> getById(String id) {
        return Maybe
            .fromCallable(() -> {
                if (id == null) {
                    throw new IllegalArgumentException("Batch id cannot be null");
                }
                return store.get(id);
            })
            .subscribeOn(Schedulers.io());
    }

    public Flowable<Batch> findByStatus(final BatchStatus status) {
        return Flowable
            .fromStream(store.values().stream().filter(batch -> Objects.equals(batch.status(), status)))
            .subscribeOn(Schedulers.io());
    }

    public Single<Batch> add(Batch batch) {
        return Single
            .fromCallable(() -> {
                if (batch.id() == null) {
                    throw new IllegalArgumentException("Batch id cannot be null");
                }
                if (store.containsKey(batch.id())) {
                    throw new BatchAlreadyExistsException();
                }
                store.put(batch.id(), batch);
                return batch;
            })
            .subscribeOn(Schedulers.io());
    }

    public Single<Batch> update(Batch batch) {
        return Single
            .fromCallable(() -> {
                if (batch.id() == null) {
                    throw new IllegalArgumentException("Batch id cannot be null");
                }
                if (!store.containsKey(batch.id())) {
                    throw new BatchNotExistException();
                }
                store.put(batch.id(), batch);
                return batch;
            })
            .subscribeOn(Schedulers.io());
    }
}
