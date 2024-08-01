package com.atkach.ecoflow;

import com.atkach.ecoflow.dto.AppCertificateResponse;
import com.atkach.ecoflow.dto.LoginRequest;
import com.atkach.ecoflow.dto.LoginResponse;
import com.atkach.ecoflow.dto.MqttCredentials;
import com.atkach.ecoflow.properties.EcoflowProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.UUID;

import static com.atkach.ecoflow.Constants.*;

@Log4j2
@Component
@RequiredArgsConstructor
public class EcoflowClient {
    private final RestTemplate restTemplate;
    private final EcoflowProperties ecoflowProperties;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    protected String generateLoginUrl() {
        return String.format("https://%s/auth/login", ecoflowProperties.getApi().getHost());
    }

    protected String generateAppCertificationUrl(String userId) {
        return String.format("https://%s/iot-auth/app/certification?userId=" + userId, ecoflowProperties.getApi().getHost());
    }

    protected LoginResponse login(boolean reset) throws IOException {
        LoginResponse loginResponse;
        Path filePath = Paths.get(ecoflowProperties.getData(), LOGIN_RESPONSE_FILE);

        if (Files.exists(filePath) && !reset) {
            log.info("Found persisted login response");
            String content = Files.readString(filePath);
            loginResponse = objectMapper.readValue(content, LoginResponse.class);
        } else {
            log.info("Fetching login response");
            loginResponse = restTemplate.postForObject(generateLoginUrl(),
                    LoginRequest.builder()
                            .email(ecoflowProperties.getApi().getEmail())
                            .password(Base64.getEncoder().encodeToString(ecoflowProperties.getApi().getPassword().getBytes()))
                            .build(),
                    LoginResponse.class);

            String json = objectMapper.writeValueAsString(loginResponse);
            Files.write(filePath, json.getBytes());
        }

        return loginResponse;
    }

    protected AppCertificateResponse requestAppCertificateResponse(LoginResponse loginResponse) {
        var token = loginResponse.getData().getToken();
        var userId = loginResponse.getData().getUser().getUserId();

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

        return restTemplate.exchange(generateAppCertificationUrl(userId), HttpMethod.GET,
                requestEntity, AppCertificateResponse.class).getBody();
    }

    protected MqttCredentials fetchMqttCredentials() throws IOException {
        Path filePath = Paths.get(ecoflowProperties.getData(), APP_CERT_RESPONSE_FILE);

        AppCertificateResponse appCertificationResponse;

        var loginResponse = login(false);

        if (Files.exists(filePath)) {
            log.info("Found persisted application certification file");
            String content = Files.readString(filePath);
            appCertificationResponse = objectMapper.readValue(content, AppCertificateResponse.class);
        } else {
            try {
                log.info("Fetching application certification file");
                appCertificationResponse = requestAppCertificateResponse(loginResponse);
            } catch (HttpStatusCodeException e) {
                if (e.getStatusCode().value() == 401) {
                    loginResponse = login(true);
                    appCertificationResponse = requestAppCertificateResponse(loginResponse);
                } else {
                    throw e;
                }
            }

            String json = objectMapper.writeValueAsString(appCertificationResponse);
            Files.write(filePath, json.getBytes());
        }

        return MqttCredentials.builder()
                .host(appCertificationResponse.getData().getUrl())
                .port(appCertificationResponse.getData().getPort())
                .clientId(String.format("ANDROID_%s_%s",
                        UUID.randomUUID().toString().toUpperCase(),
                        loginResponse.getData().getUser().getUserId()))
                .login(appCertificationResponse.getData().getCertificateAccount())
                .password(appCertificationResponse.getData().getCertificatePassword())
                .build();
    }

    public MqttClient initMqttClient() throws MqttException, IOException {
        File dataFolder = new File(ecoflowProperties.getData());

        if (!dataFolder.exists()) {
            if(!dataFolder.mkdirs()) {
                log.error("Data folder does not exist and can not be created");
                throw new IllegalStateException("Data folder does not exist and can not be created");
            }
        }

        Path filePath = Paths.get(ecoflowProperties.getData(), MQTT_CREDENTIALS_FILE);
        MqttCredentials credentials;

        if (Files.exists(filePath)) {
            log.info("Found persisted credentials");
            String content = Files.readString(filePath);
            credentials = objectMapper.readValue(content, MqttCredentials.class);
        } else {
            log.info("Fetching credentials");
            credentials = fetchMqttCredentials();

            String json = objectMapper.writeValueAsString(credentials);
            Files.write(filePath, json.getBytes());
        }

        MqttClient mqttClient = new MqttClient("ssl://" + credentials.getHost() + ":" + credentials.getPort(),
                credentials.getClientId(), new MemoryPersistence());
        var options = new MqttConnectOptions();

        options.setUserName(credentials.getLogin());
        options.setPassword(credentials.getPassword().toCharArray());
        options.setAutomaticReconnect(true);
        options.setCleanSession(true);
        options.setConnectionTimeout(10);

        mqttClient.connect(options);

        return mqttClient;
    }
}
