package com.atkach.ecoflow.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Data
@Component
@ConfigurationProperties(prefix = "ecoflow")
public class EcoflowProperties {
    private Api api;
    private Duration offlineTimeout;
    private Duration offgridTimeout;

    @Data
    public static class Api {
        private String host;
        private String accessKey;
        private String secret;
    }
}
