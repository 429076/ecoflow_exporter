package com.atkach.ecoflow.mqtt;

import com.atkach.ecoflow.api.EcoflowClient;
import com.atkach.ecoflow.api.dto.Device;
import com.atkach.ecoflow.dto.MessagePayload;
import com.atkach.ecoflow.mqtt.handlers.MetricsHandler;
import com.atkach.ecoflow.properties.EcoflowProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.micrometer.common.util.StringUtils;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.eclipse.paho.client.mqttv3.*;
import org.springframework.data.util.ParsingUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final Pattern prometheusPattern = Pattern.compile("[a-zA-Z_:][a-zA-Z0-9_:]*");
    private final List<MetricsHandler> handlers;

    @Getter
    private final ConcurrentHashMap<MetricCacheKey, MetricCacheValue> metricsCache = new ConcurrentHashMap<>();

    public MqttSubscriber(EcoflowClient ecoflowClient, EcoflowProperties ecoflowProperties,
                          MeterRegistry meterRegistry, List<MetricsHandler> handlers) throws MqttException {
        this.ecoflowProperties = ecoflowProperties;
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

        ecoflowClient.getDevices().forEach((sn, device) -> {
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

        metricsCache.forEach((key, value) -> {
            if ("ecoflow_inv_ac_in_vol".equals(key.getName())) {
                if (Duration.between(value.getLastUpdateTime(), LocalDateTime.now())
                        .compareTo(ecoflowProperties.getOffgridTimeout()) > 0) {
                    setGaugeValue("ecoflow_offgrid",
                            key.getTags(),
                            1);
                } else {
                    setGaugeValue("ecoflow_offgrid",
                            key.getTags(),
                            0);
                }
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
        log.error("Connection lost");
    }

    protected void processParameters(Device device, String prefix, Map<String, Object> params) {
        params.forEach(
                (p, v) -> {
                    var fullName = StringUtils.isNotBlank(prefix) ?
                            String.format("%s_%s", prefix, p) : p;
                    var name = ParsingUtils.reconcatenateCamelCase(fullName, "_");

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

    @Override
    public void messageArrived(String topic, MqttMessage mqttMessage) {
        try {
            var payloadString = new String(mqttMessage.getPayload());
            try {
                var payload = objectMapper.readValue(payloadString, MessagePayload.class);

                var device = ecoflowClient.getDeviceByTopic(topic);
                device.setLastMessage(LocalDateTime.now());
                meterRegistry.counter("ecoflow_mqtt_messages_receive_total",
                        Tags.of("device", device.getName())
                ).increment();

                if (Objects.nonNull(payload.getParams())) {
                    processParameters(device, payload.getTypeCode(), payload.getParams());
                } else if (Objects.nonNull(payload.getParam())) {
                    processParameters(device, payload.getTypeCode(), payload.getParam());
                } else {
                    log.error("Message without parameters {}", payloadString);
                }
            } catch (Exception e) {
                log.error("Unexpected error in subscriber " + payloadString + ", topic " + topic, e);
            }
        } catch (Exception e) {
            log.error("Unexpected error in subscriber, topic " + topic, e);
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
