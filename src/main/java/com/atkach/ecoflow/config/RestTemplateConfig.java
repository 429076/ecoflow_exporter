package com.atkach.ecoflow.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;

@Configuration
public class RestTemplateConfig {
    @Bean
    public RestTemplate defaultRestTemplate(@Qualifier("defaultClientHttpRequestFactory")
                                            HttpComponentsClientHttpRequestFactory defaultClientHttpRequestFactory,
                                            RestTemplateBuilder builder) {
        return builder
                .requestFactory(() -> defaultClientHttpRequestFactory)
                .build();
    }

    @Bean
    public HttpComponentsClientHttpRequestFactory defaultClientHttpRequestFactory(
            @Qualifier("defaultHttpClient")
            CloseableHttpClient httpClient
    ) {
        HttpComponentsClientHttpRequestFactory clientHttpRequestFactory = new HttpComponentsClientHttpRequestFactory();
        clientHttpRequestFactory.setHttpClient(httpClient);
        return clientHttpRequestFactory;
    }

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        JavaTimeModule module = new JavaTimeModule();
        return new ObjectMapper()
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .registerModule(module);
    }
}
