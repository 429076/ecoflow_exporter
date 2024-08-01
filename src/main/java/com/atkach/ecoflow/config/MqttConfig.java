package com.atkach.ecoflow.config;

import com.atkach.ecoflow.EcoflowClient;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
public class MqttConfig {
    @Bean
    public MqttClient mqttClient(EcoflowClient ecoflowClient) throws MqttException, IOException {
        return ecoflowClient.initMqttClient();
    }
}
