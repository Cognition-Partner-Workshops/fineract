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

import feign.Client;
import feign.Feign;
import feign.Request;
import feign.Retryer;
import feign.codec.Encoder;
import feign.hc5.ApacheHttp5Client;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import feign.slf4j.Slf4jLogger;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import org.apache.fineract.client.feign.support.ApiResponseDecoder;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;

/**
 * Configuration class for Feign client.
 */
public final class FineractFeignClientConfig {

    public enum HttpClientType {
        APACHE, OKHTTP
    }

    private final String baseUrl;
    private final String username;
    private final String password;
    private final String tenantId;
    private final int connectTimeout;
    private final int readTimeout;
    private final boolean debugEnabled;
    private final long connectionTimeToLive;
    private final TimeUnit connectionTimeToLiveUnit;
    private final SSLContext sslContext;
    private final X509TrustManager trustManager;
    private final HostnameVerifier hostnameVerifier;
    private final int maxConnTotal;
    private final int maxConnPerRoute;
    private final long idleConnectionEvictionTime;
    private final TimeUnit idleConnectionEvictionTimeUnit;
    private final HttpClientType clientType;
    private volatile Client cachedHttpClient;

    private FineractFeignClientConfig(Builder builder) {
        this.baseUrl = builder.baseUrl;
        this.username = builder.username;
        this.password = builder.password;
        this.tenantId = builder.tenantId;
        this.connectTimeout = builder.connectTimeout;
        this.readTimeout = builder.readTimeout;
        this.debugEnabled = builder.debugEnabled;
        this.connectionTimeToLive = builder.connectionTimeToLive;
        this.connectionTimeToLiveUnit = builder.connectionTimeToLiveUnit;
        this.sslContext = builder.sslContext;
        this.trustManager = builder.trustManager;
        this.hostnameVerifier = builder.hostnameVerifier;
        this.maxConnTotal = builder.maxConnTotal;
        this.maxConnPerRoute = builder.maxConnPerRoute;
        this.idleConnectionEvictionTime = builder.idleConnectionEvictionTime;
        this.idleConnectionEvictionTimeUnit = builder.idleConnectionEvictionTimeUnit;
        this.clientType = builder.clientType;
    }

    public static Builder builder() {
        return new Builder();
    }

    public <T> T createClient(Class<T> apiType) {
        JacksonEncoder jacksonEncoder = new JacksonEncoder(ObjectMapperFactory.getShared());
        Encoder multipartEncoder = new FineractMultipartEncoder(jacksonEncoder);

        return Feign.builder().client(getOrCreateHttpClient()).encoder(multipartEncoder)
                .decoder(new ApiResponseDecoder(new JacksonDecoder(ObjectMapperFactory.getShared())))
                .errorDecoder(new FineractErrorDecoder())
                .options(new Request.Options(connectTimeout, TimeUnit.MILLISECONDS, readTimeout, TimeUnit.MILLISECONDS, true))
                .retryer(Retryer.NEVER_RETRY).requestInterceptor(new BasicAuthRequestInterceptor(username, password))
                .requestInterceptor(new TenantIdRequestInterceptor(tenantId)).logger(new Slf4jLogger(apiType))
                .logLevel(debugEnabled ? feign.Logger.Level.FULL : feign.Logger.Level.BASIC).target(apiType, baseUrl);
    }

    private Client getOrCreateHttpClient() {
        if (cachedHttpClient == null) {
            synchronized (this) {
                if (cachedHttpClient == null) {
                    cachedHttpClient = createHttpClient();
                }
            }
        }
        return cachedHttpClient;
    }

    private Client createHttpClient() {
        switch (clientType) {
            case APACHE:
                return createApacheHttpClient();
            case OKHTTP:
                return createOkHttpClient();
            default:
                throw new IllegalStateException("Unsupported HTTP client type: " + clientType);
        }
    }

    private Client createApacheHttpClient() {
        try {
            PoolingHttpClientConnectionManagerBuilder connManagerBuilder = PoolingHttpClientConnectionManagerBuilder.create()
                    .setMaxConnTotal(maxConnTotal).setMaxConnPerRoute(maxConnPerRoute);

            if (sslContext != null || hostnameVerifier != null) {
                SSLConnectionSocketFactory sslSocketFactory = SSLConnectionSocketFactoryBuilder.create().setSslContext(sslContext)
                        .setHostnameVerifier(hostnameVerifier).build();
                connManagerBuilder.setSSLSocketFactory(sslSocketFactory);
            }

            if (connectionTimeToLive > 0) {
                connManagerBuilder.setConnectionTimeToLive(TimeValue.of(connectionTimeToLive, connectionTimeToLiveUnit));
            }

            PoolingHttpClientConnectionManager connectionManager = connManagerBuilder.build();

            CloseableHttpClient httpClient = HttpClients.custom().setConnectionManager(connectionManager)
                    .setDefaultRequestConfig(RequestConfig.custom().setConnectTimeout(Timeout.ofMilliseconds(connectTimeout))
                            .setResponseTimeout(Timeout.ofMilliseconds(readTimeout)).build())
                    .evictIdleConnections(TimeValue.of(idleConnectionEvictionTime, idleConnectionEvictionTimeUnit))
                    .evictExpiredConnections().build();

            return new ApacheHttp5Client(httpClient);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create Apache HTTP client", e);
        }
    }

    private Client createOkHttpClient() {
        try {
            okhttp3.OkHttpClient.Builder builder = new okhttp3.OkHttpClient.Builder().connectTimeout(connectTimeout, TimeUnit.MILLISECONDS)
                    .readTimeout(readTimeout, TimeUnit.MILLISECONDS)
                    .connectionPool(new okhttp3.ConnectionPool(maxConnTotal, connectionTimeToLive > 0 ? connectionTimeToLive : 5,
                            connectionTimeToLive > 0 ? connectionTimeToLiveUnit : TimeUnit.MINUTES));

            if (sslContext != null) {
                builder.sslSocketFactory(sslContext.getSocketFactory(), trustManager);
            }
            if (hostnameVerifier != null) {
                builder.hostnameVerifier(hostnameVerifier);
            }

            return new feign.okhttp.OkHttpClient(builder.build());
        } catch (Exception e) {
            throw new RuntimeException("Failed to create OkHttp client", e);
        }
    }

    public static class Builder {

        private String baseUrl;
        private String username;
        private String password;
        private String tenantId = "default";
        private int connectTimeout = 30000; // 30 seconds
        private int readTimeout = 60000; // 60 seconds
        private boolean debugEnabled = false;
        private long connectionTimeToLive = -1;
        private TimeUnit connectionTimeToLiveUnit = TimeUnit.MILLISECONDS;
        private SSLContext sslContext;
        private X509TrustManager trustManager;
        private HostnameVerifier hostnameVerifier;
        private int maxConnTotal = 200;
        private int maxConnPerRoute = 20;
        private long idleConnectionEvictionTime = 30;
        private TimeUnit idleConnectionEvictionTimeUnit = TimeUnit.SECONDS;
        private HttpClientType clientType = HttpClientType.APACHE;

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder credentials(String username, String password) {
            this.username = username;
            this.password = password;
            return this;
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder connectTimeout(int timeout, TimeUnit unit) {
            this.connectTimeout = (int) unit.toMillis(timeout);
            return this;
        }

        public Builder readTimeout(int timeout, TimeUnit unit) {
            this.readTimeout = (int) unit.toMillis(timeout);
            return this;
        }

        public Builder connectionTimeToLive(long ttl, TimeUnit unit) {
            this.connectionTimeToLive = ttl;
            this.connectionTimeToLiveUnit = unit;
            return this;
        }

        public Builder debugEnabled(boolean debugEnabled) {
            this.debugEnabled = debugEnabled;
            return this;
        }

        /**
         * Sets the TLS context. Supply its matching trust manager as well.
         */
        public Builder sslContext(SSLContext sslContext) {
            this.sslContext = Objects.requireNonNull(sslContext);
            return this;
        }

        public Builder trustManager(X509TrustManager trustManager) {
            this.trustManager = Objects.requireNonNull(trustManager);
            return this;
        }

        public Builder hostnameVerifier(HostnameVerifier hostnameVerifier) {
            this.hostnameVerifier = Objects.requireNonNull(hostnameVerifier);
            return this;
        }

        public Builder trustStore(KeyStore trustStore) {
            Objects.requireNonNull(trustStore);
            try {
                trustStore.size();
                TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                factory.init(trustStore);
                X509TrustManager manager = java.util.Arrays.stream(factory.getTrustManagers()).filter(X509TrustManager.class::isInstance)
                        .map(X509TrustManager.class::cast).findFirst()
                        .orElseThrow(() -> new IllegalStateException("No X509 trust manager available"));
                SSLContext context = SSLContext.getInstance("TLS");
                context.init(null, new javax.net.ssl.TrustManager[] { manager }, null);
                this.sslContext = context;
                this.trustManager = manager;
            } catch (GeneralSecurityException e) {
                throw new IllegalArgumentException("Cannot initialize TLS trust store", e);
            }
            return this;
        }

        public Builder maxConnTotal(int maxConnTotal) {
            this.maxConnTotal = maxConnTotal;
            return this;
        }

        public Builder maxConnPerRoute(int maxConnPerRoute) {
            this.maxConnPerRoute = maxConnPerRoute;
            return this;
        }

        public Builder idleConnectionEvictionTime(long time, TimeUnit unit) {
            this.idleConnectionEvictionTime = time;
            this.idleConnectionEvictionTimeUnit = unit;
            return this;
        }

        public Builder httpClientType(HttpClientType clientType) {
            this.clientType = clientType;
            return this;
        }

        public FineractFeignClientConfig build() {
            if ((sslContext == null) != (trustManager == null)) {
                throw new IllegalStateException("sslContext and its matching trustManager must be supplied together");
            }
            if (baseUrl == null || baseUrl.trim().isEmpty()) {
                throw new IllegalStateException("baseUrl is required");
            }
            if (username == null || username.trim().isEmpty()) {
                throw new IllegalStateException("username is required");
            }
            if (password == null) {
                throw new IllegalStateException("password is required");
            }
            return new FineractFeignClientConfig(this);
        }
    }
}
