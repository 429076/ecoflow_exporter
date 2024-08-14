package com.atkach.ecoflow;

import com.atkach.ecoflow.devices.Device;
import com.atkach.ecoflow.devices.DeviceService;
import com.atkach.ecoflow.dto.AppCertificateResponse;
import com.atkach.ecoflow.dto.LoginRequest;
import com.atkach.ecoflow.dto.LoginResponse;
import com.atkach.ecoflow.dto.MqttCredentials;
import com.atkach.ecoflow.properties.EcoflowProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.log4j.Log4j2;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
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
public class EcoflowClient {
    private final RestTemplate restTemplate;
    private final EcoflowProperties ecoflowProperties;
    private final DeviceService deviceService;

    private MqttClient mqttClient;
    private MqttConnectOptions connectOptions;

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

    protected MqttCredentials fetchMqttCredentials(boolean reset) throws IOException {
        Path filePath = Paths.get(ecoflowProperties.getData(), APP_CERT_RESPONSE_FILE);

        AppCertificateResponse appCertificationResponse;

        var loginResponse = login(reset);

        if (Files.exists(filePath) && !reset) {
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

    protected void initMqttClient(boolean reset) throws MqttException, IOException {
        File dataFolder = new File(ecoflowProperties.getData());

        if (!dataFolder.exists()) {
            if (!dataFolder.mkdirs()) {
                log.error("Data folder does not exist and can not be created");
                throw new IllegalStateException("Data folder does not exist and can not be created");
            }
        }

        Path filePath = Paths.get(ecoflowProperties.getData(), MQTT_CREDENTIALS_FILE);
        MqttCredentials credentials;

        if (Files.exists(filePath) && !reset) {
            log.info("Found persisted credentials");
            String content = Files.readString(filePath);
            credentials = objectMapper.readValue(content, MqttCredentials.class);
        } else {
            log.info("Fetching credentials");
            credentials = fetchMqttCredentials(reset);

            String json = objectMapper.writeValueAsString(credentials);
            Files.write(filePath, json.getBytes());
        }

        mqttClient = new MqttClient("ssl://" + credentials.getHost() + ":" + credentials.getPort(),
                credentials.getClientId(), new MemoryPersistence());
        connectOptions = new MqttConnectOptions();

        connectOptions.setUserName(credentials.getLogin());
        connectOptions.setPassword(credentials.getPassword().toCharArray());
        connectOptions.setAutomaticReconnect(true);
        connectOptions.setCleanSession(true);
        connectOptions.setConnectionTimeout(10);
    }

    public EcoflowClient(RestTemplate restTemplate, EcoflowProperties ecoflowProperties, DeviceService deviceService) throws MqttException, IOException {
        this.restTemplate = restTemplate;
        this.ecoflowProperties = ecoflowProperties;
        this.deviceService = deviceService;

        initMqttClient(false);
        connect();
    }

    public void subscribe(MqttCallbackExtended mqttCallback, IMqttMessageListener messageListener)
            throws MqttException {
        for (Device d : deviceService.getDevices().values()) {
            var topic = TOPIC_PREFIX + d.getSn();
            mqttClient.subscribe(topic, messageListener);
        }
        mqttClient.setCallback(mqttCallback);
    }

    public void reset(MqttCallbackExtended mqttCallback) throws MqttException, IOException {
        disconnect();
        initMqttClient(true);
        mqttClient.setCallback(mqttCallback);
        mqttClient.connect();
    }

    public boolean isConnected() {
        return mqttClient.isConnected();
    }

    public void reconnect() throws MqttException {
        mqttClient.reconnect();
    }

    public void disconnect() throws MqttException {
        mqttClient.disconnect();
    }

    public void connect() throws MqttSecurityException, MqttException {
        mqttClient.connect(connectOptions);
    }
}
