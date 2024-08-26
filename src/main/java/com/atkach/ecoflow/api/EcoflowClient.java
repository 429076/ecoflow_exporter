package com.atkach.ecoflow.api;

import com.atkach.ecoflow.api.dto.Device;
import com.atkach.ecoflow.dto.AbstractResponse;
import com.atkach.ecoflow.dto.AppCertificateResponse;
import com.atkach.ecoflow.dto.DeviceListResponse;
import com.atkach.ecoflow.dto.MqttCredentials;
import com.atkach.ecoflow.dto.data.DeviceListResponseData;
import com.atkach.ecoflow.properties.EcoflowProperties;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

import static com.atkach.ecoflow.Constants.TOPIC_TMPL;
import static com.atkach.ecoflow.utils.SignatureUtil.generateSignature;

@Log4j2
@Component
public class EcoflowClient {
    private final RestTemplate restTemplate;
    private final EcoflowProperties ecoflowProperties;
    @Getter
    private Map<String, Device> devices;
    @Getter
    private Map<String, Device> topics;

    private MqttClient mqttClient;
    private MqttConnectOptions connectOptions;

    protected String generateAppCertificationUrl() {
        return String.format("https://%s/iot-open/sign/certification", ecoflowProperties.getApi().getHost());
    }

    protected String generateDeviceListUrl() {
        return String.format("https://%s/iot-open/sign/device/list", ecoflowProperties.getApi().getHost());
    }

    protected <T extends AbstractResponse> T performGet(
            String url,
            Class<T> responseType,
            Map<String, ?> uriVariables
    ) throws Exception {
        HttpEntity<Void> requestEntity = new HttpEntity<>(generateSignature(
                ecoflowProperties.getApi().getAccessKey(),
                ecoflowProperties.getApi().getSecret(),
                uriVariables, null));

        return restTemplate.exchange(url, HttpMethod.GET,
                requestEntity, responseType, uriVariables).getBody();
    }

    protected AppCertificateResponse requestMqttCertificate() throws Exception {
        return performGet(generateAppCertificationUrl(), AppCertificateResponse.class, Collections.emptyMap());
    }

    protected DeviceListResponse requestDeviceList() throws Exception {
        return performGet(generateDeviceListUrl(), DeviceListResponse.class, Collections.emptyMap());
    }

    protected MqttCredentials fetchMqttCredentials() throws Exception {
        AppCertificateResponse appCertificationResponse = requestMqttCertificate();

        return MqttCredentials.builder()
                .host(appCertificationResponse.getData().getUrl())
                .port(appCertificationResponse.getData().getPort())
                .clientId(UUID.randomUUID().toString().toUpperCase())
                .login(appCertificationResponse.getData().getCertificateAccount())
                .password(appCertificationResponse.getData().getCertificatePassword())
                .build();
    }

    protected void initMqttClient() throws Exception {
        MqttCredentials credentials = fetchMqttCredentials();

        mqttClient = new MqttClient("ssl://" + credentials.getHost() + ":" + credentials.getPort(),
                credentials.getClientId(), new MemoryPersistence());
        connectOptions = new MqttConnectOptions();

        connectOptions.setUserName(credentials.getLogin());
        connectOptions.setPassword(credentials.getPassword().toCharArray());
        connectOptions.setAutomaticReconnect(true);
        connectOptions.setCleanSession(true);
        connectOptions.setConnectionTimeout(10);
    }

    public EcoflowClient(RestTemplate restTemplate, EcoflowProperties ecoflowProperties) throws Exception {
        this.restTemplate = restTemplate;
        this.ecoflowProperties = ecoflowProperties;

        this.devices = requestDeviceList().getData().stream()
                .collect(Collectors.toMap(
                        DeviceListResponseData::getSn,
                        d -> new Device(d.getDeviceName(), d.getSn())
                ));

        initMqttClient();
        connect();
    }

    public void subscribe(MqttCallbackExtended mqttCallback, IMqttMessageListener messageListener)
            throws MqttException {
        topics = new HashMap<>();
        for (Device d : devices.values()) {
            var topic = String.format(TOPIC_TMPL, connectOptions.getUserName(), d.getSn());
            topics.put(topic, d);
            mqttClient.subscribe(topic, messageListener);
        }
        mqttClient.setCallback(mqttCallback);
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

    public void connect() throws MqttException {
        mqttClient.connect(connectOptions);
    }

    public Device getDeviceByTopic(String topic) {
        return topics.get(topic);
    }
}
