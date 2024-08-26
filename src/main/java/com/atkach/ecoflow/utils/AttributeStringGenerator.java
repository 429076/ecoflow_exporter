package com.atkach.ecoflow.utils;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.TreeMap;

public class AttributeStringGenerator {

    public static Map<String, String> generateAttributesMap(Object obj) {
        Map<String, String> attributes = new TreeMap<>();
        processFields(obj, "", attributes);
        return attributes;
    }

    private static void processFields(Object obj, String prefix, Map<String, String> attributes) {
        if (obj == null) {
            return;
        }

        Class<?> objClass = obj.getClass();
        Field[] fields = objClass.getDeclaredFields();

        for (Field field : fields) {
            field.setAccessible(true);
            try {
                Object value = field.get(obj);
                String fieldName = prefix.isEmpty() ? field.getName() : prefix + "." + field.getName();
                if (value != null && !isPrimitiveOrWrapper(value.getClass())) {
                    processFields(value, fieldName, attributes);
                } else {
                    attributes.put(fieldName, String.valueOf(value));
                }
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
    }

    private static boolean isPrimitiveOrWrapper(Class<?> type) {
        return type.isPrimitive() || type == String.class || type == Integer.class || type == Long.class ||
                type == Short.class || type == Byte.class || type == Float.class || type == Double.class ||
                type == Boolean.class || type == Character.class;
    }
}
