package com.atkach.ecoflow.devices;

import com.atkach.ecoflow.properties.EcoflowProperties;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import static com.atkach.ecoflow.Constants.TOPIC_PREFIX;

@Component
public class DeviceService {
    @Getter
    private final Map<String, Device> devices;

    public DeviceService(EcoflowProperties properties) {
        devices = Arrays.stream(properties.getDevices().split(","))
                .map(d -> {
                    var dParts = d.split(":");
                    return new Device(dParts[1], dParts[0]);
                })
                .collect(Collectors.toMap(Device::getSn, device -> device));
    }

    public Device getDeviceByTopic(String topic) {
        var sn = topic.substring(TOPIC_PREFIX.length());

        return devices.get(sn);
    }
}
