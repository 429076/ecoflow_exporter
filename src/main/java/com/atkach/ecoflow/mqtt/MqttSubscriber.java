package com.atkach.ecoflow.mqtt;

import com.atkach.ecoflow.EcoflowClient;
import com.atkach.ecoflow.devices.DeviceService;
import com.atkach.ecoflow.dto.MessagePayload;
import com.atkach.ecoflow.mqtt.handlers.MetricsHandler;
import com.atkach.ecoflow.properties.EcoflowProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.eclipse.paho.client.mqttv3.*;
import org.springframework.data.util.ParsingUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Log4j2
@Component
public class MqttSubscriber implements IMqttMessageListener, MqttCallbackExtended {
    private final EcoflowProperties ecoflowProperties;
    private final EcoflowClient ecoflowClient;
    private final DeviceService deviceService;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final Pattern prometheusPattern = Pattern.compile("[a-zA-Z_:][a-zA-Z0-9_:]*");
    private final List<MetricsHandler> handlers;
    private final AtomicInteger messagesFromLastCheck = new AtomicInteger(1);
    private final AtomicInteger reconnectAttempts = new AtomicInteger(0);

    @Getter
    private final ConcurrentHashMap<MetricCacheKey, MetricCacheValue> metricsCache = new ConcurrentHashMap<>();

    public MqttSubscriber(EcoflowClient ecoflowClient, EcoflowProperties ecoflowProperties, DeviceService deviceService,
                          MeterRegistry meterRegistry, List<MetricsHandler> handlers) throws MqttException {
        this.ecoflowProperties = ecoflowProperties;
        this.deviceService = deviceService;
        this.meterRegistry = meterRegistry;
        this.handlers = handlers;
        this.ecoflowClient = ecoflowClient;
        meterRegistry.gauge("ecoflow_metrics_cache_size", metricsCache,
                ConcurrentHashMap::size);
        ecoflowClient.subscribe(this, this);
    }

    @Scheduled(fixedDelay = 60000)
    public void checkDevicesForTimeout() {
        LocalDateTime now = LocalDateTime.now();
        if (!ecoflowClient.isConnected()) {
            log.warn("Connection lost, reconnecting.");
            try {
                meterRegistry.counter("ecoflow_mqtt_reconnects_total",
                        Tags.of(
                                Tag.of("type", "0")
                        )).increment();
                ecoflowClient.reconnect();
            } catch (MqttException e) {
                log.error("Unexpected error occurred while attempting to reconnect.", e);
            }
        } else {
            var messages = messagesFromLastCheck.getAndSet(0);
            if (messages < 1) {
                log.warn("No messages for 1 minute, reconnecting.");
                var attempts = reconnectAttempts.incrementAndGet();
                if (attempts > 5) {
                    log.error("Too many reconnect attempts");
                } else {
                    try {
                        if (!ecoflowClient.isConnected()) {
                            meterRegistry.counter("ecoflow_mqtt_reconnects_total",
                                    Tags.of(
                                            Tag.of("type", "1")
                                    )).increment();
                            ecoflowClient.reconnect();
                        } else {
                            meterRegistry.counter("ecoflow_mqtt_reconnects_total",
                                    Tags.of(
                                            Tag.of("type", "2")
                                    )).increment();
                            ecoflowClient.disconnect();
                            ecoflowClient.connect();
                        }
                    } catch (MqttException e) {
                        log.error("Unexpected error occurred while attempting to reconnect.", e);
                    }
                }
            }
        }
        deviceService.getDevices().forEach((sn, device) -> {
            Duration duration = Duration.between(device.getLastMessage(), now);
            if (duration.compareTo(ecoflowProperties.getOfflineTimeout()) > 0) {
                log.debug("Device '{}' with sn '{}' has not sent a message for {}", device.getName(), sn, duration);
                setGaugeValue("ecoflow_online",
                        Tags.of("device", device.getName()),
                        0);
            } else {
                setGaugeValue("ecoflow_online",
                        Tags.of("device", device.getName()),
                        1);
            }
        });
    }

    private void setGaugeValue(String metricName, Tags tags, double value) {
        var gaugeKey = new MetricCacheKey(metricName, tags);

        var mVal = metricsCache.computeIfAbsent(
                gaugeKey,
                k -> new MetricCacheValue(value)
        );

        mVal.setValue(value);

        meterRegistry.gauge(metricName, tags, mVal);
    }

    @Override
    public void connectionLost(Throwable throwable) {
        log.warn("Connection lost");
    }

    @Override
    public void messageArrived(String topic, MqttMessage mqttMessage) {
        try {
            messagesFromLastCheck.incrementAndGet();
            reconnectAttempts.set(0);
            var payloadString = new String(mqttMessage.getPayload());
            try {
                var payload = objectMapper.readValue(payloadString, MessagePayload.class);
                if (Objects.nonNull(payload.getParams())) {
                    var device = deviceService.getDeviceByTopic(topic);

                    device.setLastMessage(LocalDateTime.now());
                    meterRegistry.counter("ecoflow_mqtt_messages_receive_total",
                            Tags.of("device", device.getName())
                    ).increment();

                    payload.getParams().forEach(
                            (p, v) -> {
                                var name = ParsingUtils.reconcatenateCamelCase(p.replace(".", "_"), "_");

                                if (!name.endsWith("_bytes") && !name.endsWith("_ver") && !name.endsWith("_sn")) {
                                    if (prometheusPattern.matcher(name).matches()) {
                                        boolean processed = false;
                                        for (MetricsHandler handler : handlers) {
                                            if (handler.canHandle(device, name, v)) {
                                                List<MetricValue> metrics = handler.getMetrics(device, name, v);
                                                for (MetricValue metric : metrics) {
                                                    var metricName = String.format("ecoflow_%s", metric.getMetricName());
                                                    var tags = Tags.of(
                                                            Stream.concat(
                                                                    Stream.of(Tag.of("device", device.getName())),
                                                                    metric.getTags().stream()
                                                            ).toList()
                                                    );
                                                    setGaugeValue(metricName, tags, metric.getValue());
                                                }
                                                processed = true;
                                            }
                                        }

                                        if (!processed) {
                                            log.warn("{} can not be processed, value: {}, type {}", name, v, v.getClass());
                                        }
                                    } else {
                                        log.warn("{} does not comply with prometheus name format", name);
                                    }
                                }
                            }
                    );
                }
            } catch (Exception e) {
                log.error("Unexpected error in subscriber " + payloadString, e);
            }
        } catch (Exception e) {
            log.error("Unexpected error in subscriber", e);
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken iMqttDeliveryToken) {
    }

    @Override
    public void connectComplete(boolean b, String s) {
        try {
            log.info("Connected to MQTT broker");
            ecoflowClient.subscribe(this, this);
        } catch (Exception e) {
            log.error("Unexpected error in subscriber during connection", e);
        }
    }
}
