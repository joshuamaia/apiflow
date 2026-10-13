package br.com.joshua.apiflowservice.flowtracking.domain;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Reduces an exception to data that is safe to store and display: the exception type and a truncated
 * message with common secrets and personal data redacted. Stack traces are never kept.
 */
public final class ErrorSanitizer {

    static final int MAX_MESSAGE_LENGTH = 300;
    private static final String REDACTED = "[redacted]";

    private static final List<Pattern> SENSITIVE = List.of(
            Pattern.compile("(?i)(password|passwd|pwd|secret|token|authorization|api[-_]?key|bearer)\\s*[=:]?\\s*\\S+"),
            Pattern.compile("[\\w.+-]+@[\\w-]+\\.[\\w.]+"),
            Pattern.compile("\\b\\d{11,}\\b"),
            Pattern.compile("\\b\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}\\b"));

    private ErrorSanitizer() {
    }

    public static String type(Throwable error) {
        return error == null ? null : error.getClass().getName();
    }

    public static String message(Throwable error) {
        if (error == null || error.getMessage() == null) {
            return null;
        }
        return redact(error.getMessage());
    }

    static String redact(String text) {
        String result = text.replaceAll("[\\r\\n\\t]+", " ").strip();
        for (Pattern pattern : SENSITIVE) {
            result = pattern.matcher(result).replaceAll(REDACTED);
        }
        return result.length() > MAX_MESSAGE_LENGTH ? result.substring(0, MAX_MESSAGE_LENGTH - 1) + "…" : result;
    }
}
