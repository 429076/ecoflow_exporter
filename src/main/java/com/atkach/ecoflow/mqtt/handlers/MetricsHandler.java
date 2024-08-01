package com.atkach.ecoflow.mqtt.handlers;

import com.atkach.ecoflow.devices.Device;
import com.atkach.ecoflow.mqtt.MetricValue;

import java.util.List;

public interface MetricsHandler {
    List<MetricValue> getMetrics(Device device, String name, Object value);
    boolean canHandle(Device device, String name, Object value);
}
