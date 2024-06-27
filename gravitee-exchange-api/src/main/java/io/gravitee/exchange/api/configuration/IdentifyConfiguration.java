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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;

/**
 * @author Guillaume LAMIRAND (guillaume.lamirand at graviteesource.com)
 * @author GraviteeSource Team
 */
@RequiredArgsConstructor
@Getter
@Accessors(fluent = true)
@Slf4j
public class IdentifyConfiguration {

    public static final String DEFAULT_EXCHANGE_ID = "exchange";
    private final Environment environment;
    private final String id;
    private final Map<String, String> fallbackKeys;

    public IdentifyConfiguration(final Environment environment) {
        this(environment, DEFAULT_EXCHANGE_ID);
    }

    public IdentifyConfiguration(final Environment environment, final Map<String, String> fallbackKeys) {
        this(environment, DEFAULT_EXCHANGE_ID, fallbackKeys);
    }

    public IdentifyConfiguration(final Environment environment, final String identifier) {
        this(environment, identifier, Map.of());
    }

    public String id() {
        return id;
    }

    public boolean containsProperty(final String key) {
        String identifyProperty = identifyProperty(key);
        boolean containsProperty = environment.containsProperty(identifyProperty);
        if (!containsProperty && fallbackKeys.containsKey(key)) {
            String fallbackKey = fallbackKeys.get(key);
            containsProperty = environment.containsProperty(fallbackKey);
            if (containsProperty) {
                log.warn(
                    "[{}] Using deprecated configuration '{}', replace it by '{}' as it will be removed.",
                    id,
                    fallbackKey,
                    identifyProperty
                );
            }
        }
        return containsProperty;
    }

    public String getProperty(final String key) {
        return this.getProperty(key, String.class, null);
    }

    public <T> T getProperty(final String key, final Class<T> clazz, final T defaultValue) {
        String identifyProperty = identifyProperty(key);
        T value = environment.getProperty(identifyProperty, clazz);
        if (value == null && fallbackKeys.containsKey(key)) {
            String fallbackKey = fallbackKeys.get(key);
            value = environment.getProperty(fallbackKeys.get(key), clazz);
            if (value != null) {
                log.warn(
                    "[{}] Using deprecated configuration '{}', replace it by '{}' as it will be removed.",
                    id,
                    fallbackKey,
                    identifyProperty
                );
            }
        }
        return value != null ? value : defaultValue;
    }

    public List<String> getPropertyList(final String key) {
        List<String> values = new ArrayList<>();
        int index = 0;
        String indexKey = ("%s[%s]").formatted(key, index);
        while (containsProperty(indexKey)) {
            String value = getProperty(indexKey);
            if (value != null && !value.isBlank()) {
                values.add(value);
            }
            index++;
            indexKey = ("%s[%s]").formatted(key, index);
        }

        // Fallback
        if (fallbackKeys.containsKey(key)) {
            String fallbackKey = fallbackKeys.get(key);
            int fallbackIndex = 0;
            String fallbackIndexKey = ("%s[%s]").formatted(fallbackKey, fallbackIndex);
            while (environment.containsProperty(fallbackIndexKey)) {
                String value = environment.getProperty(fallbackIndexKey);
                if (value != null && !value.isBlank()) {
                    values.add(value);
                }
                fallbackIndex++;
                fallbackIndexKey = ("%s[%s]").formatted(fallbackKey, fallbackIndex);
            }
        }

        return values;
    }

    public String identifyProperty(final String key) {
        return identify("%s.%s", key);
    }

    public String identifyName(final String name) {
        return identify("%s-%s", name);
    }

    private String identify(final String format, final String key) {
        return format.formatted(id, key);
    }
}
