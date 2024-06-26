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
package io.gravitee.exchange.connector.websocket.client;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Optional;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Guillaume LAMIRAND (guillaume.lamirand at graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
@Getter
public class WebSocketEndpoint {

    private static final String HTTPS_SCHEME = "https";
    private static final int DEFAULT_HTTP_PORT = 80;
    private static final int DEFAULT_HTTPS_PORT = 443;

    private final URL url;

    private WebSocketEndpoint(final URL url) {
        this.url = url;
    }

    /**
     * Creates a new WebSocketEndpoint instance using the given URL.
     *
     * @param value the URL to be used for creating the WebSocketEndpoint
     * @return an Optional containing the WebSocketEndpoint instance if the URL is valid, otherwise an empty Optional
     */
    public static Optional<WebSocketEndpoint> newEndpoint(String value) {
        try {
            if (value != null && !value.isEmpty()) {
                URL url = new URL(value.endsWith("/") ? value.substring(0, value.length() - 1) : value);
                return Optional.of(new WebSocketEndpoint(url));
            }
            log.warn("Invalid websocket endpoint url {}", value);
        } catch (MalformedURLException e) {
            log.warn("Invalid websocket endpoint url {}", value, e);
        }

        return Optional.empty();
    }

    public int getPort() {
        if (url.getPort() != -1) {
            return url.getPort();
        } else if (HTTPS_SCHEME.equals(url.getProtocol())) {
            return DEFAULT_HTTPS_PORT;
        } else {
            return DEFAULT_HTTP_PORT;
        }
    }

    public String getHost() {
        return url.getHost();
    }

    public String resolvePath(String path) {
        String value = path;
        if (path == null) {
            value = "";
        }

        return url.getPath() + (value.startsWith("/") ? value : "/" + value);
    }
}
