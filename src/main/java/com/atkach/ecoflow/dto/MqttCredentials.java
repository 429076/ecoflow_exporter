package com.atkach.ecoflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MqttCredentials {
    private String host;
    private int port;
    private String login;
    private String password;
    private String clientId;
}
