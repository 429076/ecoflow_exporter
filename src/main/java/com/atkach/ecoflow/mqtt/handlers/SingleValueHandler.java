package com.atkach.ecoflow.mqtt.handlers;

import com.atkach.ecoflow.api.dto.Device;
import com.atkach.ecoflow.mqtt.MetricValue;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class SingleValueHandler implements MetricsHandler {
    @Override
    public List<MetricValue> getMetrics(Device device, String name, Object value) {
        return List.of(
                new MetricValue(name, HandlerUtils.toDouble(value), Collections.emptyList())
        );
    }

    @Override
    public boolean canHandle(Device device, String name, Object value) {
        return value instanceof Number;
    }
}
