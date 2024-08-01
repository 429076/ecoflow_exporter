package com.atkach.ecoflow.mqtt.handlers;

import com.atkach.ecoflow.devices.Device;
import com.atkach.ecoflow.mqtt.MetricValue;
import io.micrometer.core.instrument.Tag;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class SingleValueArrayHandler implements MetricsHandler {
    @Override
    public List<MetricValue> getMetrics(Device device, String name, Object value) {
        if (value instanceof List listValue) {
            var result = new ArrayList<MetricValue>();
            for (int i = 0; i < listValue.size(); i++) {
                var elValue = listValue.get(i);
                result.add(
                        new MetricValue(name, HandlerUtils.toDouble(elValue),
                                List.of(
                                        Tag.of("index", Integer.toString(i))
                                ))
                );
            }
            return result;
        } else throw new IllegalStateException("Cannot process " + value);
    }

    @Override
    public boolean canHandle(Device device, String name, Object value) {
        if (value instanceof List listValue) {
            return listValue.stream().allMatch(
                    v -> v instanceof Double || v instanceof Integer
            );
        }

        return false;
    }
}
