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

import io.gravitee.exchange.connector.websocket.exception.WebSocketConnectorException;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.net.JksOptions;
import io.vertx.core.net.PemTrustOptions;
import io.vertx.core.net.PfxOptions;
import io.vertx.rxjava3.core.Vertx;
import io.vertx.rxjava3.core.http.HttpClient;
import java.net.URL;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Guillaume LAMIRAND (guillaume.lamirand at graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
@RequiredArgsConstructor
public class WebSocketConnectorClientFactory {

    private static final String KEYSTORE_FORMAT_JKS = "JKS";
    private static final String KEYSTORE_FORMAT_PEM = "PEM";
    private static final String KEYSTORE_FORMAT_PKCS12 = "PKCS12";

    private final AtomicInteger counter = new AtomicInteger(0);
    private final Vertx vertx;

    @Getter
    private final WebSocketClientConfiguration configuration;

    public WebSocketEndpoint nextEndpoint() {
        List<WebSocketEndpoint> endpoints = configuration.endpoints();

        if (endpoints.isEmpty()) {
            return null;
        }
        return endpoints.get(Math.abs(counter.getAndIncrement() % endpoints.size()));
    }

    public void resetEndpointRetries() {
        counter.set(0);
    }

    public int endpointRetries() {
        return counter.get();
    }

    public HttpClient createHttpClient(WebSocketEndpoint websocketEndpoint) {
        URL target = websocketEndpoint.getUrl();
        HttpClientOptions options = new HttpClientOptions();
        options.setDefaultHost(websocketEndpoint.getHost());
        options.setDefaultPort(websocketEndpoint.getPort());

        if (isSecureProtocol(target.getProtocol())) {
            options.setSsl(true);
            options.setTrustAll(configuration.trustAll());
            options.setVerifyHost(configuration.verifyHost());
        }
        if (configuration.keyStoreType() != null) {
            if (configuration.keyStoreType().equalsIgnoreCase(KEYSTORE_FORMAT_JKS)) {
                options.setKeyStoreOptions(
                    new JksOptions().setPath(configuration.keyStorePath()).setPassword(configuration.keyStorePassword())
                );
            } else if (configuration.keyStoreType().equalsIgnoreCase(KEYSTORE_FORMAT_PKCS12)) {
                options.setPfxKeyCertOptions(
                    new PfxOptions().setPath(configuration.keyStorePath()).setPassword(configuration.keyStorePassword())
                );
            } else {
                throw new WebSocketConnectorException("Unsupported keystore type", false);
            }
        }

        if (configuration.trustStoreType() != null) {
            if (configuration.trustStoreType().equalsIgnoreCase(KEYSTORE_FORMAT_JKS)) {
                options.setTrustStoreOptions(
                    new JksOptions().setPath(configuration.trustStorePath()).setPassword(configuration.trustStorePassword())
                );
            } else if (configuration.trustStoreType().equalsIgnoreCase(KEYSTORE_FORMAT_PKCS12)) {
                options.setPfxTrustOptions(
                    new PfxOptions().setPath(configuration.trustStorePath()).setPassword(configuration.trustStorePassword())
                );
            } else if (configuration.trustStoreType().equalsIgnoreCase(KEYSTORE_FORMAT_PEM)) {
                options.setPemTrustOptions(new PemTrustOptions().addCertPath(configuration.trustStorePath()));
            } else {
                throw new WebSocketConnectorException("Unsupported truststore type", false);
            }
        }

        options.setMaxWebSocketFrameSize(configuration.maxWebSocketFrameSize());
        options.setMaxWebSocketMessageSize(configuration.maxWebSocketMessageSize());

        return vertx.createHttpClient(options);
    }

    private boolean isSecureProtocol(String scheme) {
        return scheme.charAt(scheme.length() - 1) == 's' && scheme.length() > 2;
    }
}
