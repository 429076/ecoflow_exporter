package com.atkach.ecoflow.mqtt;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
public class MetricCacheValue extends Number {
    private double value;
    private LocalDateTime lastUpdateTime;

    public MetricCacheValue(double value) {
        this.value = value;
        this.lastUpdateTime = LocalDateTime.now();
    }

    public void setValue(double value) {
        this.value = value;
        this.lastUpdateTime = LocalDateTime.now();
    }

    @Override
    public int intValue() {
        return (int)value;
    }

    @Override
    public long longValue() {
        return (long)value;
    }

    @Override
    public float floatValue() {
        return (float)value;
    }

    @Override
    public double doubleValue() {
        return value;
    }
}
