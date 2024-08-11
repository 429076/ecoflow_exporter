package com.atkach.ecoflow.mqtt;

import io.micrometer.core.instrument.Tags;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MetricCacheKey {
    private String name;
    private Tags tags;
}
