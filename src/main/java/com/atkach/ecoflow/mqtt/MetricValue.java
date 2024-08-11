package com.atkach.ecoflow.mqtt;

import io.micrometer.core.instrument.Tag;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MetricValue {
    private String metricName;
    private double value;
    private List<Tag> tags;
}
