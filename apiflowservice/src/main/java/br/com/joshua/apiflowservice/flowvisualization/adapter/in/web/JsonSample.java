package br.com.joshua.apiflowservice.flowvisualization.adapter.in.web;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.temporal.Temporal;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a skeleton JSON value (maps, lists and literals) from a request body type, so the dashboard
 * can pre-fill a request editor. Records are expanded field by field; numbers default to 1.
 */
final class JsonSample {

    private static final int MAX_DEPTH = 5;

    private JsonSample() {
    }

    static Object of(Type type) {
        return sample(type, 0);
    }

    private static Object sample(Type type, int depth) {
        if (depth > MAX_DEPTH) {
            return null;
        }
        Class<?> raw = rawClass(type);
        if (raw == null) {
            return null;
        }
        if (CharSequence.class.isAssignableFrom(raw)) {
            return "example";
        }
        if (raw == boolean.class || raw == Boolean.class) {
            return false;
        }
        if (raw.isPrimitive() || Number.class.isAssignableFrom(raw) || raw == BigDecimal.class || raw == BigInteger.class) {
            return 1;
        }
        if (raw.isEnum()) {
            Object[] constants = raw.getEnumConstants();
            return constants.length > 0 ? ((Enum<?>) constants[0]).name() : null;
        }
        if (Temporal.class.isAssignableFrom(raw)) {
            return "2026-01-01T00:00:00Z";
        }
        if (Collection.class.isAssignableFrom(raw) || raw.isArray()) {
            Type element = raw.isArray() ? raw.getComponentType() : typeArgument(type, 0);
            Object item = element == null ? null : sample(element, depth + 1);
            return item == null ? List.of() : List.of(item);
        }
        if (Map.class.isAssignableFrom(raw)) {
            return Map.of();
        }
        if (raw.isRecord()) {
            Map<String, Object> fields = new LinkedHashMap<>();
            for (RecordComponent component : raw.getRecordComponents()) {
                fields.put(component.getName(), sample(component.getGenericType(), depth + 1));
            }
            return fields;
        }
        return Map.of();
    }

    private static Class<?> rawClass(Type type) {
        if (type instanceof Class<?> c) {
            return c;
        }
        if (type instanceof ParameterizedType p && p.getRawType() instanceof Class<?> c) {
            return c;
        }
        return null;
    }

    private static Type typeArgument(Type type, int index) {
        return type instanceof ParameterizedType p && p.getActualTypeArguments().length > index
                ? p.getActualTypeArguments()[index] : null;
    }
}
