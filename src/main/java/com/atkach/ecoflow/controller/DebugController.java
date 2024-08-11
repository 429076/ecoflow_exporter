package com.atkach.ecoflow.controller;

import com.atkach.ecoflow.controller.dto.MetricDTO;
import com.atkach.ecoflow.controller.dto.TagDTO;
import com.atkach.ecoflow.mqtt.MqttSubscriber;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class DebugController {
    private final MqttSubscriber mqttSubscriber;

    @GetMapping("/debug")
    public List<MetricDTO> getCache() {
        return mqttSubscriber.getMetricsCache().entrySet()
                .stream()
                .map(
                        e -> new MetricDTO(e.getKey().getName(),
                                e.getKey().getTags()
                                        .stream().map(
                                                t -> new TagDTO(t.getKey(), t.getValue())
                                        ).toList(), e.getValue().doubleValue())
                ).toList();
    }
}
