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

import java.net.URI;
import lombok.Builder;
import lombok.Getter;

/**
 * @author Guillaume LAMIRAND (guillaume.lamirand at graviteesource.com)
 * @author GraviteeSource Team
 */
@Getter
public class WebSocketEndpoint {

    private static final String HTTPS_SCHEME = "https";
    private static final int DEFAULT_HTTP_PORT = 80;
    private static final int DEFAULT_HTTPS_PORT = 443;

    private final URI uri;

    @Builder
    public WebSocketEndpoint(final String url) {
        this.uri = URI.create(url);
    }

    public int getPort() {
        if (uri.getPort() != -1) {
            return uri.getPort();
        } else if (HTTPS_SCHEME.equals(uri.getScheme())) {
            return DEFAULT_HTTPS_PORT;
        } else {
            return DEFAULT_HTTP_PORT;
        }
    }

    public String getHost() {
        return uri.getHost();
    }

    public String resolvePath(String path) {
        return uri.resolve(path).getRawPath();
    }
}
