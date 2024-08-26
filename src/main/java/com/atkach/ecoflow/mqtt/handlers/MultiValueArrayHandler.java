package com.atkach.ecoflow.mqtt.handlers;

import com.atkach.ecoflow.api.dto.Device;
import com.atkach.ecoflow.mqtt.MetricValue;
import io.micrometer.core.instrument.Tag;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.util.ParsingUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class MultiValueArrayHandler implements MetricsHandler {
    @Override
    public List<MetricValue> getMetrics(Device device, String name, Object value) {
        if (value instanceof List listValue) {
            var result = new ArrayList<MetricValue>();
            for (int i = 0; i < listValue.size(); i++) {
                var elValue = listValue.get(i);
                if (elValue instanceof Map mapValue) {
                    var serial = mapValue.get("sn").toString();

                    if (StringUtils.isBlank(serial)) {
                        continue;
                    }

                    for (Object oKey : mapValue.keySet()) {
                        var key = ParsingUtils.reconcatenateCamelCase(oKey.toString().replace(".", "_"), "_");

                        if (key.equals("sn")) {
                            continue;
                        }

                        var oValue = mapValue.get(oKey);
                        result.add(
                                new MetricValue(name + "_" + key, HandlerUtils.toDouble(oValue),
                                        List.of(
                                                Tag.of("sn", serial)
                                        ))
                        );
                    }
                } else throw new IllegalStateException("Cannot process " + value);
            }
            return result;
        } else throw new IllegalStateException("Cannot process " + value);
    }

    @Override
    public boolean canHandle(Device device, String name, Object value) {
        if (value instanceof List listValue) {
            return listValue.stream().allMatch(
                    v -> v instanceof Map
            );
        }

        return false;
    }
}
