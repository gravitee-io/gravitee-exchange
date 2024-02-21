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
package io.gravitee.exchange.controller.websocket.server;

import io.gravitee.exchange.api.configuration.IdentifyConfiguration;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.net.TCPSSLOptions;
import lombok.RequiredArgsConstructor;

/**
 * @author Guillaume LAMIRAND (guillaume.lamirand at graviteesource.com)
 * @author GraviteeSource Team
 */
@RequiredArgsConstructor
public class WebSocketControllerServerConfiguration {

    public static final String PORT_KEY = "controller.ws.port";
    public static final int PORT_DEFAULT = 8062;
    public static final String HOST_KEY = "controller.ws.host";
    public static final String HOST_DEFAULT = "0.0.0.0";
    public static final String ALPN_KEY = "controller.ws.alpn";
    public static final boolean ALPN_DEFAULT = false;
    public static final String SECURED_KEY = "controller.ws.secured";
    public static final boolean SECURED_DEFAULT = false;
    public static final String CLIENT_AUTH_KEY = "controller.ws.clientAuth";
    public static final String KEYSTORE_TYPE_KEY = "controller.ws.keystore.type";
    public static final String KEYSTORE_PATH_KEY = "controller.ws.keystore.path";
    public static final String KEYSTORE_PASSWORD_KEY = "controller.ws.keystore.password";
    public static final String TRUSTSTORE_TYPE_KEY = "controller.ws.truststore.type";
    public static final String TRUSTSTORE_PATH_KEY = "controller.ws.truststore.path";
    public static final String TRUSTSTORE_PASSWORD_KEY = "controller.ws.truststore.password";
    public static final String COMPRESSION_SUPPORTED_KEY = "controller.ws.compressionSupported";
    public static final boolean COMPRESSION_SUPPORTED_DEFAULT = HttpServerOptions.DEFAULT_COMPRESSION_SUPPORTED;
    public static final String IDLE_TIMEOUT_KEY = "controller.ws.idleTimeout";
    public static final int IDLE_TIMEOUT_DEFAULT = TCPSSLOptions.DEFAULT_IDLE_TIMEOUT;
    public static final String TCP_KEEP_ALIVE_KEY = "controller.ws.tcpKeepAlive";
    public static final boolean TCP_KEEP_ALIVE_DEFAULT = true;
    public static final String MAX_HEADER_SIZE_KEY = "controller.ws.maxHeaderSize";
    public static final int MAX_HEADER_SIZE_DEFAULT = 8192;
    public static final String MAX_CHUNK_SIZE_KEY = "controller.ws.maxChunkSize";
    public static final int MAX_CHUNK_SIZE_DEFAULT = 8192;
    public static final String MAX_WEB_SOCKET_FRAME_SIZE_KEY = "controller.ws.maxWebSocketFrameSize";
    public static final int MAX_WEB_SOCKET_FRAME_SIZE_DEFAULT = 65536;
    public static final String MAX_WEB_SOCKET_MESSAGE_SIZE_KEY = "controller.ws.maxWebSocketMessageSize";
    public static final int MAX_WEB_SOCKET_MESSAGE_SIZE_DEFAULT = 13107200;
    private final IdentifyConfiguration identifyConfiguration;

    public int port() {
        return identifyConfiguration.getProperty(PORT_KEY, Integer.class, PORT_DEFAULT);
    }

    public String host() {
        return identifyConfiguration.getProperty(HOST_KEY, String.class, HOST_DEFAULT);
    }

    public boolean alpn() {
        return identifyConfiguration.getProperty(ALPN_KEY, Boolean.class, ALPN_DEFAULT);
    }

    public boolean secured() {
        return identifyConfiguration.getProperty(SECURED_KEY, Boolean.class, SECURED_DEFAULT);
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

    public String clientAuth() {
        return identifyConfiguration.getProperty(CLIENT_AUTH_KEY);
    }

    public boolean compressionSupported() {
        return identifyConfiguration.getProperty(COMPRESSION_SUPPORTED_KEY, Boolean.class, COMPRESSION_SUPPORTED_DEFAULT);
    }

    public int idleTimeout() {
        return identifyConfiguration.getProperty(IDLE_TIMEOUT_KEY, Integer.class, IDLE_TIMEOUT_DEFAULT);
    }

    public boolean tcpKeepAlive() {
        return identifyConfiguration.getProperty(TCP_KEEP_ALIVE_KEY, Boolean.class, TCP_KEEP_ALIVE_DEFAULT);
    }

    public int maxHeaderSize() {
        return identifyConfiguration.getProperty(MAX_HEADER_SIZE_KEY, Integer.class, MAX_HEADER_SIZE_DEFAULT);
    }

    public int maxChunkSize() {
        return identifyConfiguration.getProperty(MAX_CHUNK_SIZE_KEY, Integer.class, MAX_CHUNK_SIZE_DEFAULT);
    }

    public int maxWebSocketFrameSize() {
        return identifyConfiguration.getProperty(MAX_WEB_SOCKET_FRAME_SIZE_KEY, Integer.class, MAX_WEB_SOCKET_FRAME_SIZE_DEFAULT);
    }

    public int maxWebSocketMessageSize() {
        return identifyConfiguration.getProperty(MAX_WEB_SOCKET_MESSAGE_SIZE_KEY, Integer.class, MAX_WEB_SOCKET_MESSAGE_SIZE_DEFAULT);
    }
}
