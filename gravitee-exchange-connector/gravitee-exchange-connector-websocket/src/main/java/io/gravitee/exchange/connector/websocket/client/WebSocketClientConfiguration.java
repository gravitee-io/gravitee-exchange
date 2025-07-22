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

import io.gravitee.exchange.api.configuration.IdentifyConfiguration;
import io.vertx.core.http.HttpServerOptions;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;

/**
 * @author Guillaume LAMIRAND (guillaume.lamirand at graviteesource.com)
 * @author GraviteeSource Team
 */
@RequiredArgsConstructor
public class WebSocketClientConfiguration {

    public static final String HEADERS_KEY = "connector.ws.headers";
    public static final String TRUST_ALL_KEY = "connector.ws.ssl.trustAll";
    public static final boolean TRUST_ALL_DEFAULT = false;
    public static final String VERIFY_HOST_KEY = "connector.ws.ssl.verifyHost";
    public static final boolean VERIFY_HOST_DEFAULT = true;
    public static final String KEYSTORE_TYPE_KEY = "connector.ws.ssl.keystore.type";
    public static final String KEYSTORE_PATH_KEY = "connector.ws.ssl.keystore.path";
    public static final String KEYSTORE_PASSWORD_KEY = "connector.ws.ssl.keystore.password";
    public static final String TRUSTSTORE_TYPE_KEY = "connector.ws.ssl.truststore.type";
    public static final String TRUSTSTORE_PATH_KEY = "connector.ws.ssl.truststore.path";
    public static final String TRUSTSTORE_PASSWORD_KEY = "connector.ws.ssl.truststore.password";
    public static final String MAX_WEB_SOCKET_FRAME_SIZE_KEY = "connector.ws.maxWebSocketFrameSize";
    public static final int MAX_WEB_SOCKET_FRAME_SIZE_DEFAULT = 65536;
    public static final String MAX_WEB_SOCKET_MESSAGE_SIZE_KEY = "connector.ws.maxWebSocketMessageSize";
    public static final int MAX_WEB_SOCKET_MESSAGE_SIZE_DEFAULT = 13107200;
    public static final String ENDPOINTS_KEY = "connector.ws.endpoints";
    public static final String AUTO_RECONNECT_KEY = "connector.ws.autoReconnect";
    private final IdentifyConfiguration identifyConfiguration;

    private List<WebSocketEndpoint> endpoints;
    private Map<String, String> headers;

    public Map<String, String> headers() {
        if (headers == null) {
            headers = readHeaders();
        }
        return headers;
    }

    private Map<String, String> readHeaders() {
        int endpointIndex = 0;
        String key = ("%s[%s]").formatted(HEADERS_KEY, endpointIndex);
        Map<String, String> computedHeaders = new HashMap<>();
        while (identifyConfiguration.containsProperty(key + ".name")) {
            String name = identifyConfiguration.getProperty(key + ".name");
            String value = identifyConfiguration.getProperty(key + ".value");
            if (name != null && value != null) {
                computedHeaders.put(name, value);
            }
            endpointIndex++;
            key = ("%s[%s]").formatted(HEADERS_KEY, endpointIndex);
        }
        return computedHeaders;
    }

    public boolean trustAll() {
        return identifyConfiguration.getProperty(TRUST_ALL_KEY, Boolean.class, TRUST_ALL_DEFAULT);
    }

    public boolean verifyHost() {
        return identifyConfiguration.getProperty(VERIFY_HOST_KEY, Boolean.class, VERIFY_HOST_DEFAULT);
    }

    public String keyStoreType() {
        return identifyConfiguration.getProperty(KEYSTORE_TYPE_KEY);
    }

    public String keyStorePath() {
        return identifyConfiguration.getProperty(KEYSTORE_PATH_KEY);
    }

    public String keyStorePassword() {
        return identifyConfiguration.getProperty(KEYSTORE_PASSWORD_KEY);
    }

    public String trustStoreType() {
        return identifyConfiguration.getProperty(TRUSTSTORE_TYPE_KEY);
    }

    public String trustStorePath() {
        return identifyConfiguration.getProperty(TRUSTSTORE_PATH_KEY);
    }

    public String trustStorePassword() {
        return identifyConfiguration.getProperty(TRUSTSTORE_PASSWORD_KEY);
    }

    /**
     * Determines whether auto-reconnection is enabled for the connector.
     * @return a boolean indicating whether auto-reconnection is enabled (true) or disabled (false)
     */
    public boolean autoReconnect() {
        return identifyConfiguration.getProperty(AUTO_RECONNECT_KEY, Boolean.class, Boolean.FALSE);
    }

    /**
     * Max size of a WebSocket frame.
     * Be careful when changing this value, it needs to be a good trade-off between:
     * <ul>
     *     <li>memory consumption (the bigger the value, the more memory is used)</li>
     *     <li>performance (the smaller the value, the more CPU is used)</li>
     *     <li>network usage (the smaller the value, the more network calls are made)</li>
     * </ul>
     * <p>
     * Default value is the same as the one in Vert.x, 65536 bytes (64KB).
     *
     * @see HttpServerOptions#DEFAULT_MAX_WEBSOCKET_FRAME_SIZE
     */
    public int maxWebSocketFrameSize() {
        return identifyConfiguration.getProperty(MAX_WEB_SOCKET_FRAME_SIZE_KEY, Integer.class, MAX_WEB_SOCKET_FRAME_SIZE_DEFAULT);
    }

    /**
     * A WebSocket messages can be composed of several WebSocket frames.
     * This value is the maximum size of a WebSocket message.
     * <p>
     * It should be a multiple of {@link #maxWebSocketFrameSize}.
     * <p>
     * Default value is 200 x {@link #maxWebSocketFrameSize} = 13MB.
     * It can sound big but when doing API Promotion with APIM, the payload can be huge as it includes the doc pages, images etc.
     *
     * @see HttpServerOptions#DEFAULT_MAX_WEBSOCKET_MESSAGE_SIZE
     */
    public int maxWebSocketMessageSize() {
        return identifyConfiguration.getProperty(MAX_WEB_SOCKET_MESSAGE_SIZE_KEY, Integer.class, MAX_WEB_SOCKET_MESSAGE_SIZE_DEFAULT);
    }

    public List<WebSocketEndpoint> endpoints() {
        if (endpoints == null) {
            endpoints = readEndpoints();
        }

        return endpoints;
    }

    private List<WebSocketEndpoint> readEndpoints() {
        List<WebSocketEndpoint> endpointsConfiguration = new ArrayList<>();
        List<String> propertyList = identifyConfiguration.getPropertyList(ENDPOINTS_KEY);
        if (propertyList != null) {
            endpointsConfiguration.addAll(
                propertyList.stream().map(WebSocketEndpoint::newEndpoint).filter(Optional::isPresent).map(Optional::get).toList()
            );
        }
        return endpointsConfiguration;
    }
}
