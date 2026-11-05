/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.fineract.client.feign;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import feign.RequestLine;
import feign.RetryableException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.security.KeyStore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class FineractFeignClientConfigTest {

    @ParameterizedTest
    @EnumSource(FineractFeignClientConfig.HttpClientType.class)
    void selfSignedServerRequiresExplicitTrust(FineractFeignClientConfig.HttpClientType type) throws Exception {
        KeyStore serverKeys = KeyStore.getInstance("PKCS12");
        try (InputStream input = getClass().getResourceAsStream("/localhost.p12")) {
            assertNotNull(input);
            serverKeys.load(input, "changeit".toCharArray());
        }
        KeyManagerFactory keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keys.init(serverKeys, "changeit".toCharArray());
        SSLContext serverContext = SSLContext.getInstance("TLS");
        serverContext.init(keys.getKeyManagers(), null, null);
        HttpsServer server = HttpsServer.create(new InetSocketAddress("localhost", 0), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(serverContext));
        server.createContext("/test", exchange -> {
            byte[] response = "\"ok\"".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (var output = exchange.getResponseBody()) {
                output.write(response);
            }
        });
        server.start();
        try {
            String url = "https://localhost:" + server.getAddress().getPort();
            TestApi defaultClient = tlsBuilder(url, type).build().createClient(TestApi.class);
            RetryableException failure = assertThrows(RetryableException.class, defaultClient::test);
            assertInstanceOf(SSLHandshakeException.class, failure.getCause());

            KeyStore trusted = KeyStore.getInstance("PKCS12");
            trusted.load(null, null);
            trusted.setCertificateEntry("localhost", serverKeys.getCertificate("localhost"));
            TestApi trustedClient = tlsBuilder(url, type).trustStore(trusted).build().createClient(TestApi.class);
            assertEquals("ok", trustedClient.test());

            TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            factory.init(trusted);
            X509TrustManager manager = (X509TrustManager) factory.getTrustManagers()[0];
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, factory.getTrustManagers(), null);
            TestApi contextClient = tlsBuilder(url, type).sslContext(context).trustManager(manager).build().createClient(TestApi.class);
            assertEquals("ok", contextClient.test());

            AtomicBoolean verifierCalled = new AtomicBoolean();
            TestApi rejectedHostnameClient = tlsBuilder(url, type).trustStore(trusted).hostnameVerifier((hostname, session) -> {
                verifierCalled.set(true);
                return false;
            }).build().createClient(TestApi.class);
            assertThrows(RetryableException.class, rejectedHostnameClient::test);
            assertTrue(verifierCalled.get());

            KeyStore empty = KeyStore.getInstance("PKCS12");
            empty.load(null, null);
            TestApi emptyTrustClient = tlsBuilder(url, type).trustStore(empty).build().createClient(TestApi.class);
            assertThrows(RetryableException.class, emptyTrustClient::test);
        } finally {
            server.stop(0);
        }
    }

    private FineractFeignClientConfig.Builder tlsBuilder(String url, FineractFeignClientConfig.HttpClientType type) {
        return FineractFeignClientConfig.builder().baseUrl(url).credentials("admin", "password").httpClientType(type)
                .connectTimeout(5, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS);
    }

    @Test
    void customTlsRequiresContextAndMatchingManager() throws Exception {
        assertThrows(IllegalStateException.class, () -> tlsBuilder("https://localhost", FineractFeignClientConfig.HttpClientType.OKHTTP)
                .sslContext(SSLContext.getDefault()).build());
        TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        factory.init((KeyStore) null);
        assertThrows(IllegalStateException.class, () -> tlsBuilder("https://localhost", FineractFeignClientConfig.HttpClientType.APACHE)
                .trustManager((X509TrustManager) factory.getTrustManagers()[0]).build());
        assertThrows(NullPointerException.class, () -> FineractFeignClientConfig.builder().trustStore(null));
        KeyStore uninitialized = KeyStore.getInstance("PKCS12");
        assertThrows(IllegalArgumentException.class, () -> FineractFeignClientConfig.builder().trustStore(uninitialized));
    }

    @Test
    void buildersDoNotExposeTlsBypass() {
        assertThrows(NoSuchMethodException.class,
                () -> FineractFeignClientConfig.Builder.class.getMethod("disableSslVerification", boolean.class));
        assertThrows(NoSuchMethodException.class,
                () -> FineractFeignClient.Builder.class.getMethod("disableSslVerification", boolean.class));
    }

    @Test
    void testBuilderDefaults() {
        FineractFeignClientConfig.Builder builder = FineractFeignClientConfig.builder();
        FineractFeignClientConfig config = builder.baseUrl("http://localhost:8080").credentials("admin", "password").build();

        assertNotNull(config);
    }

    @Test
    void testBuilderConfiguration() {
        String baseUrl = "http://example.com:8080";
        String username = "testuser";
        String password = "testpass";
        int connectTimeoutSeconds = 10;
        int readTimeoutSeconds = 30;

        FineractFeignClientConfig config = FineractFeignClientConfig.builder().baseUrl(baseUrl).credentials(username, password)
                .connectTimeout(connectTimeoutSeconds, TimeUnit.SECONDS).readTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
                .debugEnabled(true).build();

        assertNotNull(config);
    }

    @Test
    void testConnectionTimeToLiveConfiguration() {
        long ttl = 5;
        TimeUnit ttlUnit = TimeUnit.MINUTES;

        FineractFeignClientConfig config = FineractFeignClientConfig.builder().baseUrl("http://localhost:8080")
                .credentials("admin", "password").connectionTimeToLive(ttl, ttlUnit).build();

        assertNotNull(config);
    }

    @Test
    void testEncoderDecoderConfiguration() {
        FineractFeignClientConfig config = FineractFeignClientConfig.builder().baseUrl("http://localhost:8080")
                .credentials("admin", "password").build();

        assertNotNull(config);
    }

    @Test
    void testErrorDecoderConfiguration() {
        FineractFeignClientConfig config = FineractFeignClientConfig.builder().baseUrl("http://localhost:8080")
                .credentials("admin", "password").build();

        assertNotNull(config);
    }

    @Test
    void testClientCreation() {
        FineractFeignClientConfig config = FineractFeignClientConfig.builder().baseUrl("http://localhost:8080")
                .credentials("admin", "password").connectTimeout(5, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).build();

        TestApi client = config.createClient(TestApi.class);
        assertNotNull(client);
    }

    interface TestApi {

        @RequestLine("GET /test")
        String test();
    }
}
