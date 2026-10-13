package br.com.joshua.apiflowservice.flowtracking.domain;

import java.util.LinkedHashMap;
import java.util.Map;

/** Compact text format for span tags: one {@code key=value} pair per line. */
public final class SpanTags {

    private SpanTags() {
    }

    public static String encode(Map<String, String> tags) {
        if (tags == null || tags.isEmpty()) {
            return null;
        }
        StringBuilder text = new StringBuilder();
        tags.forEach((key, value) -> {
            if (!text.isEmpty()) {
                text.append('\n');
            }
            text.append(clean(key)).append('=').append(clean(value));
        });
        return text.toString();
    }

    public static Map<String, String> decode(String text) {
        Map<String, String> tags = new LinkedHashMap<>();
        if (text == null || text.isBlank()) {
            return tags;
        }
        for (String line : text.split("\n")) {
            int separator = line.indexOf('=');
            if (separator > 0) {
                tags.put(line.substring(0, separator), line.substring(separator + 1));
            }
        }
        return tags;
    }

    private static String clean(String value) {
        return value.replace('\n', ' ').replace('\r', ' ');
    }
}
