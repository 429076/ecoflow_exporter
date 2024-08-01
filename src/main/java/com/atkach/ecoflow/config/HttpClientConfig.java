package com.atkach.ecoflow.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.httpcomponents.hc5.PoolingHttpClientConnectionManagerMetricsBinder;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.socket.ConnectionSocketFactory;
import org.apache.hc.client5.http.socket.PlainConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.TrustAllStrategy;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;

@Slf4j
@Configuration
public class HttpClientConfig {
    @Value("${httpClient.timeout.connectTimeout}")
    private int connectTimeout;
    @Value("${httpClient.timeout.readTimeout}")
    private int requestTimeout;
    @Value("${httpClient.timeout.socketTimeout}")
    private int socketTimeout;

    @Value("${httpClient.connections.maxConnections}")
    private int maxConnections;
    @Value("${httpClient.connections.maxConnectionsPerRoute}")
    private int maxConnectionsPerRoute;

    public PoolingHttpClientConnectionManager poolingConnectionManager(MeterRegistry registry) {
        SSLContextBuilder builder = new SSLContextBuilder();
        try {
            builder.loadTrustMaterial(null, new TrustAllStrategy());
        } catch (NoSuchAlgorithmException | KeyStoreException e) {
            log.error("Pooling Connection Manager Initialisation failure because of " + e.getMessage(), e);
        }

        var registryBuilder = RegistryBuilder
                .<ConnectionSocketFactory>create()
                .register("http", new PlainConnectionSocketFactory());

        try {
            registryBuilder.register("https", new SSLConnectionSocketFactory(builder.build()));
        } catch (KeyManagementException | NoSuchAlgorithmException e) {
            log.error("Pooling Connection Manager Initialisation failure because of " + e.getMessage(), e);
        }

        var socketFactoryRegistry = registryBuilder.build();

        PoolingHttpClientConnectionManager poolingConnectionManager =
                new PoolingHttpClientConnectionManager(socketFactoryRegistry);
        poolingConnectionManager.setMaxTotal(maxConnections);
        poolingConnectionManager.setDefaultMaxPerRoute(maxConnectionsPerRoute);

        new PoolingHttpClientConnectionManagerMetricsBinder(poolingConnectionManager, "http-client-pool")
                .bindTo(registry);

        return poolingConnectionManager;
    }

    @Bean
    public CloseableHttpClient defaultHttpClient(MeterRegistry registry) {
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(requestTimeout, TimeUnit.MILLISECONDS)
                .setResponseTimeout(requestTimeout, TimeUnit.MILLISECONDS)
                .build();

        var cm = poolingConnectionManager(registry);

        ConnectionConfig connConfig = ConnectionConfig.custom()
                .setConnectTimeout(connectTimeout, TimeUnit.MILLISECONDS)
                .setSocketTimeout(socketTimeout, TimeUnit.MILLISECONDS)
                .build();

        cm.setDefaultConnectionConfig(connConfig);

        return HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .setConnectionManager(cm)
                .build();
    }
}
