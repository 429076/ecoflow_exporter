package com.atkach.ecoflow.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Data
@Component
@ConfigurationProperties(prefix = "ecoflow")
public class EcoflowProperties {
    private String data;
    private Api api;
    private Duration offlineTimeout;
    private Duration offgridTimeout;
    private String devices;

    @Data
    public static class Api {
        private String host;
        private String email;
        private String password;
    }
}
