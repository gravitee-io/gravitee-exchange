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
package io.gravitee.exchange.api.configuration;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.core.env.Environment;

/**
 * @author Guillaume LAMIRAND (guillaume.lamirand at graviteesource.com)
 * @author GraviteeSource Team
 */
@RequiredArgsConstructor
@Getter
@Accessors(fluent = true)
public class PrefixConfiguration {

    private static final String DEFAULT_EXCHANGE_PREFIX = "exchange";
    private final Environment environment;
    private final String keyPrefix;

    public PrefixConfiguration(final Environment environment) {
        this(environment, DEFAULT_EXCHANGE_PREFIX);
    }

    public boolean containsProperty(final String key) {
        return environment.containsProperty(prefixKey(key));
    }

    public String getProperty(final String key) {
        return environment.getProperty(prefixKey(key));
    }

    public <T> T getProperty(final String key, final Class<T> clazz, final T defaultValue) {
        return environment.getProperty(prefixKey(key), clazz, defaultValue);
    }

    public String prefixKey(final String key) {
        return "%s.%s".formatted(keyPrefix, key);
    }
}