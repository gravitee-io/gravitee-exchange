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

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.google.common.io.Resources;
import io.gravitee.exchange.api.configuration.IdentifyConfiguration;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.junit5.VertxExtension;
import io.vertx.rxjava3.core.http.HttpClient;
import io.vertx.rxjava3.core.http.HttpClientRequest;
import java.io.File;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLHandshakeException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.mock.env.MockEnvironment;

/**
 * @author Guillaume LAMIRAND (guillaume.lamirand at graviteesource.com)
 * @author GraviteeSource Team
 */
@ExtendWith(VertxExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class WebSocketConnectorClientFactoryTest {

    private io.vertx.rxjava3.core.Vertx vertx;
    private MockEnvironment environment;
    private WebSocketClientConfiguration webSocketClientConfiguration;
    private WebSocketConnectorClientFactory cut;

    @BeforeEach
    public void beforeEach(Vertx vertx) {
        this.vertx = io.vertx.rxjava3.core.Vertx.newInstance(vertx);
        this.environment = new MockEnvironment();
        this.webSocketClientConfiguration = new WebSocketClientConfiguration(new IdentifyConfiguration(environment));
        cut = new WebSocketConnectorClientFactory(this.vertx, webSocketClientConfiguration);
    }

    @Nested
    class Endpoints {

        @Test
        void should_return_null_without_endpoint() {
            WebSocketEndpoint webSocketEndpoint = cut.nextEndpoint();
            assertThat(webSocketEndpoint).isNull();
        }

        @Test
        void should_return_next_endpoint() {
            environment.setProperty("exchange.connector.ws.endpoints[0]", "http://endpoint:1234");
            WebSocketEndpoint webSocketEndpoint = cut.nextEndpoint();
            assertThat(webSocketEndpoint).isNotNull();
        }

        @Test
        void should_loop_over_endpoint_on_each_next_endpoint() {
            environment.setProperty("exchange.connector.ws.endpoints[0]", "http://endpoint:1234");
            environment.setProperty("exchange.connector.ws.endpoints[1]", "http://endpoint:5678");
            WebSocketEndpoint webSocketEndpoint1 = cut.nextEndpoint();
            WebSocketEndpoint webSocketEndpoint2 = cut.nextEndpoint();
            WebSocketEndpoint webSocketEndpoint3 = cut.nextEndpoint();
            assertThat(webSocketEndpoint1.getPort()).isEqualTo(1234);
            assertThat(webSocketEndpoint2.getPort()).isEqualTo(5678);
            assertThat(webSocketEndpoint3.getPort()).isEqualTo(1234);
        }

        @Test
        void should_return_second_endpoint_when_retrying() {
            environment
                .withProperty("exchange.connector.ws.endpoints[0]", "http://endpoint:1234")
                .withProperty("exchange.connector.ws.endpoints[1]", "http://endpoint2:5678");
            WebSocketEndpoint webSocketEndpoint1 = cut.nextEndpoint();
            assertThat(webSocketEndpoint1).isNotNull();
            assertThat(webSocketEndpoint1.getPort()).isEqualTo(1234);
            WebSocketEndpoint webSocketEndpoint2 = cut.nextEndpoint();
            assertThat(webSocketEndpoint2).isNotNull();
            assertThat(webSocketEndpoint2.getPort()).isEqualTo(5678);
        }
    }

    @Nested
    class CreateHttpClient_NoSSL {

        private static WireMockServer wireMockServer;
        private static WebSocketEndpoint webSocketEndpoint;

        @BeforeAll
        static void setup() {
            final WireMockConfiguration wireMockConfiguration = wireMockConfig()
                .dynamicPort()
                .dynamicHttpsPort()
                .keystorePath(toPath("keystore.jks"))
                .keystorePassword("password")
                .trustStorePath(toPath("truststore.jks"))
                .trustStorePassword("password");
            wireMockServer = new WireMockServer(wireMockConfiguration);
            wireMockServer.start();
            webSocketEndpoint = WebSocketEndpoint.builder().url("http://localhost:%s".formatted(wireMockServer.port())).build();
        }

        @AfterAll
        static void tearDown() {
            wireMockServer.stop();
            wireMockServer.shutdownServer();
        }

        @Test
        void should_create_http_client_from_default_configuration() {
            HttpClient httpClient = cut.createHttpClient(webSocketEndpoint);
            assertThat(httpClient).isNotNull();
            httpClient
                .rxRequest(HttpMethod.GET, "/test")
                .flatMap(HttpClientRequest::rxSend)
                .test()
                .awaitDone(30, TimeUnit.SECONDS)
                .assertComplete();
        }
    }

    @Nested
    class CreateHttpClient_SSL {

        private static WireMockServer wireMockServer;
        private static WebSocketEndpoint webSocketEndpoint;

        @BeforeAll
        static void setup() {
            final WireMockConfiguration wireMockConfiguration = wireMockConfig()
                .dynamicPort()
                .dynamicHttpsPort()
                .keystorePath(toPath("keystore.jks"))
                .keystorePassword("password")
                .trustStorePath(toPath("truststore.jks"))
                .trustStorePassword("password");
            wireMockServer = new WireMockServer(wireMockConfiguration);
            wireMockServer.start();
            webSocketEndpoint = WebSocketEndpoint.builder().url("https://localhost:%s".formatted(wireMockServer.httpsPort())).build();
        }

        @AfterAll
        static void tearDown() {
            wireMockServer.stop();
            wireMockServer.shutdownServer();
        }

        @Test
        void should_create_http_client_without_trust_store() {
            HttpClient httpClient = cut.createHttpClient(webSocketEndpoint);
            assertThat(httpClient).isNotNull();
            httpClient
                .rxRequest(HttpMethod.GET, "/test")
                .flatMap(HttpClientRequest::rxSend)
                .test()
                .awaitDone(30, TimeUnit.SECONDS)
                .assertError(throwable -> {
                    assertThat(throwable.getCause()).isInstanceOf(SSLHandshakeException.class);
                    assertThat(throwable.getCause().getMessage())
                        .isEqualTo(
                            "PKIX path building failed: sun.security.provider.certpath.SunCertPathBuilderException: unable to find valid certification path to requested target"
                        );
                    return true;
                });
        }

        @Test
        void should_create_http_client_with_trust_all() {
            environment.setProperty("exchange.connector.ws.ssl.trustAll", "true");
            HttpClient httpClient = cut.createHttpClient(webSocketEndpoint);
            assertThat(httpClient).isNotNull();
            httpClient
                .rxRequest(HttpMethod.GET, "/test")
                .flatMap(HttpClientRequest::rxSend)
                .test()
                .awaitDone(30, TimeUnit.SECONDS)
                .assertComplete();
        }

        @Test
        void should_create_http_client_with_trust_store() {
            environment
                .withProperty("exchange.connector.ws.ssl.truststore.type", "JKS")
                .withProperty("exchange.connector.ws.ssl.truststore.path", toPath("truststore.jks"))
                .withProperty("exchange.connector.ws.ssl.truststore.password", "password");
            HttpClient httpClient = cut.createHttpClient(webSocketEndpoint);
            assertThat(httpClient).isNotNull();
            httpClient
                .rxRequest(HttpMethod.GET, "/test")
                .flatMap(HttpClientRequest::rxSend)
                .test()
                .awaitDone(30, TimeUnit.SECONDS)
                .assertComplete();
        }
    }

    @Nested
    class CreateHttpClient_MTLS {

        private static WireMockServer wireMockServer;
        private static WebSocketEndpoint webSocketEndpoint;

        @BeforeAll
        static void setup() {
            final WireMockConfiguration wireMockConfiguration = wireMockConfig()
                .dynamicPort()
                .dynamicHttpsPort()
                .keystorePath(toPath("keystore.jks"))
                .keystorePassword("password")
                .trustStorePath(toPath("truststore.jks"))
                .trustStorePassword("password")
                .needClientAuth(true);
            wireMockServer = new WireMockServer(wireMockConfiguration);
            wireMockServer.start();
            webSocketEndpoint = WebSocketEndpoint.builder().url("https://localhost:%s".formatted(wireMockServer.httpsPort())).build();
        }

        @AfterAll
        static void tearDown() {
            wireMockServer.stop();
            wireMockServer.shutdownServer();
        }

        @Test
        void should_create_http_client_without_keystore_store() {
            environment
                .withProperty("exchange.connector.ws.ssl.truststore.type", "JKS")
                .withProperty("exchange.connector.ws.ssl.truststore.path", toPath("truststore.jks"))
                .withProperty("exchange.connector.ws.ssl.truststore.password", "password");
            HttpClient httpClient = cut.createHttpClient(webSocketEndpoint);
            assertThat(httpClient).isNotNull();
            httpClient
                .rxRequest(HttpMethod.GET, "/test")
                .flatMap(HttpClientRequest::rxSend)
                .test()
                .awaitDone(30, TimeUnit.SECONDS)
                .assertError(throwable -> {
                    assertThat(throwable.getCause()).isInstanceOf(SSLHandshakeException.class);
                    assertThat(throwable.getCause().getMessage()).isEqualTo("Received fatal alert: bad_certificate");
                    return true;
                });
        }

        @Test
        void should_create_http_client_with_keystore() {
            environment
                .withProperty("exchange.connector.ws.ssl.truststore.type", "JKS")
                .withProperty("exchange.connector.ws.ssl.truststore.path", toPath("truststore.jks"))
                .withProperty("exchange.connector.ws.ssl.truststore.password", "password")
                .withProperty("exchange.connector.ws.ssl.keystore.type", "JKS")
                .withProperty("exchange.connector.ws.ssl.keystore.path", toPath("keystore.jks"))
                .withProperty("exchange.connector.ws.ssl.keystore.password", "password");
            HttpClient httpClient = cut.createHttpClient(webSocketEndpoint);
            assertThat(httpClient).isNotNull();
            httpClient
                .rxRequest(HttpMethod.GET, "/test")
                .flatMap(HttpClientRequest::rxSend)
                .test()
                .awaitDone(30, TimeUnit.SECONDS)
                .assertComplete();
        }
    }

    private static String toPath(String resourcePath) {
        try {
            return new File(Resources.getResource(resourcePath).toURI()).getCanonicalPath();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
