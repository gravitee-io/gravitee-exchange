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
import io.gravitee.node.vertx.client.ssl.KeyStore;
import io.gravitee.node.vertx.client.ssl.KeyStoreType;
import io.gravitee.node.vertx.client.ssl.TrustStore;
import io.gravitee.node.vertx.client.ssl.TrustStoreType;
import io.gravitee.node.vertx.client.ssl.jks.JKSKeyStore;
import io.gravitee.node.vertx.client.ssl.jks.JKSTrustStore;
import io.gravitee.node.vertx.client.ssl.none.NoneKeyStore;
import io.gravitee.node.vertx.client.ssl.none.NoneTrustStore;
import io.gravitee.node.vertx.client.ssl.pem.PEMTrustStore;
import io.gravitee.node.vertx.client.ssl.pkcs12.PKCS12KeyStore;
import io.gravitee.node.vertx.client.ssl.pkcs12.PKCS12TrustStore;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.net.*;
import io.vertx.rxjava3.core.Vertx;
import io.vertx.rxjava3.core.http.HttpClient;
import java.net.URL;
import java.util.List;
import java.util.Optional;
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

        configureSSL(options, target);
        configureKeyStore(options);
        configureTrustStore(options);
        configureProxy(options);

        options.setMaxWebSocketFrameSize(configuration.maxWebSocketFrameSize());
        options.setMaxWebSocketMessageSize(configuration.maxWebSocketMessageSize());
        options.setTcpKeepAlive(configuration.keepAlive());

        return vertx.createHttpClient(options);
    }

    private void configureSSL(HttpClientOptions options, URL target) {
        if (isSecureProtocol(target.getProtocol())) {
            options.setSsl(true);
            options.setTrustAll(configuration.trustAll());
            options.setVerifyHost(configuration.verifyHost());
        }
    }

    private void configureKeyStore(HttpClientOptions options) {
        if (configuration.keyStoreType() == null) {
            return;
        }

        try {
            createKeystore().flatMap(KeyStore::keyCertOptions).ifPresent(options::setKeyCertOptions);
        } catch (KeyStore.KeyStoreCertOptionsException e) {
            throw new WebSocketConnectorException(e.getMessage(), false);
        }
    }

    private Optional<KeyStore> createKeystore() {
        String type = configuration.keyStoreType();
        String path = configuration.keyStorePath();
        String password = configuration.keyStorePassword();

        try {
            KeyStore keyStore = null;
            switch (KeyStoreType.valueOf(type.toUpperCase())) {
                case PKCS12 -> keyStore = PKCS12KeyStore.builder().path(path).password(password).build();
                case JKS -> keyStore = JKSKeyStore.builder().path(path).password(password).build();
                case NONE -> keyStore = NoneKeyStore.builder().build();
                default -> throw new WebSocketConnectorException("Invalid truststore type: " + type, false);
            }
            return Optional.of(keyStore);
        } catch (IllegalArgumentException e) {
            throw new WebSocketConnectorException("Invalid truststore type: " + type, false);
        }
    }

    private void configureTrustStore(HttpClientOptions options) {
        if (configuration.trustStoreType() == null) {
            return;
        }

        try {
            createTrustStore().flatMap(TrustStore::trustOptions).ifPresent(options::setTrustOptions);
        } catch (TrustStore.TrustOptionsException e) {
            throw new WebSocketConnectorException(e.getMessage(), false);
        }
    }

    private Optional<TrustStore> createTrustStore() {
        String type = configuration.trustStoreType();
        String path = configuration.trustStorePath();
        String content = configuration.trustStoreContent();
        String password = configuration.trustStorePassword();
        String alias = configuration.trustStoreAlias();

        if (type == null) {
            return Optional.empty();
        }

        TrustStore trustStore = null;
        try {
            switch (TrustStoreType.valueOf(type.toUpperCase())) {
                case PEM -> trustStore = PEMTrustStore.builder().path(path).content(content).build();
                case PKCS12 -> trustStore = PKCS12TrustStore.builder().path(path).content(content).password(password).alias(alias).build();
                case JKS -> trustStore = JKSTrustStore.builder().path(path).content(content).password(password).alias(alias).build();
                case NONE -> trustStore = NoneTrustStore.builder().build();
            }
            return Optional.of(trustStore);
        } catch (IllegalArgumentException e) {
            throw new WebSocketConnectorException("Invalid truststore type: " + type, false);
        }
    }

    private void configureProxy(HttpClientOptions options) {
        if (!configuration.isProxyConfigured()) {
            return;
        }
        ProxyOptions proxyOptions = new ProxyOptions();
        if (configuration.proxyUseSystemProxy()) {
            proxyOptions.setType(ProxyType.valueOf(configuration.systemProxyType()));
            proxyOptions.setHost(configuration.systemProxyHost());
            proxyOptions.setPort(configuration.systemProxyPort());
            proxyOptions.setUsername(configuration.systemProxyUsername());
            proxyOptions.setPassword(configuration.systemProxyPassword());
        } else {
            proxyOptions.setType(ProxyType.valueOf(configuration.proxyType()));
            proxyOptions.setHost(configuration.proxyHost());
            proxyOptions.setPort(configuration.proxyPort());
            proxyOptions.setUsername(configuration.proxyUsername());
            proxyOptions.setPassword(configuration.proxyPassword());
        }

        options.setProxyOptions(proxyOptions);
    }

    private boolean isSecureProtocol(String scheme) {
        return scheme.charAt(scheme.length() - 1) == 's' && scheme.length() > 2;
    }
}
