package com.atkach.ecoflow.mqtt.handlers;

public class HandlerUtils {
    public static double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        } else throw new NumberFormatException("Cannot convert " + value + " to double");
    }
}
