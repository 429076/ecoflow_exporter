package com.atkach.ecoflow.mqtt.handlers;

public class HandlerUtils {
    public static double toDouble(Object value) {
        if (value instanceof Double doubleValue) {
            return doubleValue;
        } else if (value instanceof Integer integerValue) {
            return Double.valueOf(integerValue);
        } else throw new NumberFormatException("Cannot convert " + value + " to double");
    }
}
