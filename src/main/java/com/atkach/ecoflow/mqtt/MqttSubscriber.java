package com.atkach.ecoflow.mqtt;

import com.atkach.ecoflow.devices.Device;
import com.atkach.ecoflow.devices.DeviceService;
import com.atkach.ecoflow.dto.MessagePayload;
import com.atkach.ecoflow.mqtt.handlers.MetricsHandler;
import com.atkach.ecoflow.properties.EcoflowProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.mutable.MutableDouble;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.springframework.data.util.ParsingUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.atkach.ecoflow.Constants.TOPIC_PREFIX;

@Log4j2
@Component
public class MqttSubscriber implements IMqttMessageListener {
    private final EcoflowProperties ecoflowProperties;
    private final DeviceService deviceService;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final Pattern prometheusPattern = Pattern.compile("[a-zA-Z_:][a-zA-Z0-9_:]*");
    private final List<MetricsHandler> handlers;
    private final ConcurrentHashMap<String, MutableDouble> metricsCache = new ConcurrentHashMap<>();

    public MqttSubscriber(MqttClient mqttClient, EcoflowProperties ecoflowProperties, DeviceService deviceService,
                          MeterRegistry meterRegistry, List<MetricsHandler> handlers) throws MqttException {
        this.ecoflowProperties = ecoflowProperties;
        this.deviceService = deviceService;
        this.meterRegistry = meterRegistry;
        this.handlers = handlers;
        meterRegistry.gauge("ecoflow_metrics_cache_size", metricsCache,
                ConcurrentHashMap::size);
        for (Device d : deviceService.getDevices().values()) {
            var topic = TOPIC_PREFIX + d.getSn();
            mqttClient.subscribe(topic, this);
        }
    }


    @Scheduled(fixedDelay = 60000)
    public void checkDevicesForTimeout() {
        LocalDateTime now = LocalDateTime.now();
        deviceService.getDevices().forEach((sn, device) -> {
            Duration duration = Duration.between(device.getLastMessage(), now);
            if (duration.compareTo(ecoflowProperties.getOfflineTimeout()) > 0) {
                log.warn("Device '{}' with sn '{}' has not sent a message for {}", device.getName(), sn, duration);
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

    private String gaugeKey(String name, Tags tags) {
        return name + ":" + tags.stream().map(tag -> tag.getKey() + tag.getValue()).collect(Collectors.joining(""));
    }

    private void setGaugeValue(String metricName, Tags tags, double value) {
        var gaugeKey = gaugeKey(metricName, tags);

        var mVal = metricsCache.computeIfAbsent(
                gaugeKey,
                k -> new MutableDouble(value)
        );

        mVal.setValue(value);

        meterRegistry.gauge(metricName, tags, mVal);
    }

    @Override
    public void messageArrived(String topic, MqttMessage mqttMessage) {
        try {
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

                                if (!name.endsWith("_bytes") && !name.endsWith("_ver")) {
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
                                            log.warn("{} can not be processed, value: {}", name, v);
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
}
