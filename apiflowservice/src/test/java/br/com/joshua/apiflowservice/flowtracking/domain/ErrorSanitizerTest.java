package br.com.joshua.apiflowservice.flowtracking.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorSanitizerTest {

    @Test
    void keepsTypeAndSafeMessage() {
        RuntimeException error = new IllegalStateException("Insufficient stock for product 6: requested 4, available 3");

        assertThat(ErrorSanitizer.type(error)).isEqualTo("java.lang.IllegalStateException");
        assertThat(ErrorSanitizer.message(error)).isEqualTo("Insufficient stock for product 6: requested 4, available 3");
    }

    @Test
    void redactsSecretsAndPersonalData() {
        String message = ErrorSanitizer.message(new RuntimeException(
                "login failed for john.doe@example.com password=hunter2 token: abc.def cpf 123.456.789-09 card 4111111111111111"));

        assertThat(message)
                .doesNotContain("john.doe@example.com", "hunter2", "abc.def", "123.456.789-09", "4111111111111111")
                .contains("[redacted]");
    }

    @Test
    void truncatesLongMessagesAndFlattensLines() {
        String message = ErrorSanitizer.message(new RuntimeException("line1\nline2 " + "x".repeat(1000)));

        assertThat(message).hasSize(ErrorSanitizer.MAX_MESSAGE_LENGTH).startsWith("line1 line2").endsWith("…");
    }

    @Test
    void handlesMissingValues() {
        assertThat(ErrorSanitizer.type(null)).isNull();
        assertThat(ErrorSanitizer.message(new RuntimeException())).isNull();
    }

    @Test
    void spanTagsRoundTrip() {
        var tags = new java.util.LinkedHashMap<String, String>();
        tags.put("demo.artificialDelayMs", "1500");
        tags.put("note", "multi\nline");

        assertThat(SpanTags.decode(SpanTags.encode(tags)))
                .containsEntry("demo.artificialDelayMs", "1500")
                .containsEntry("note", "multi line");
        assertThat(SpanTags.encode(java.util.Map.of())).isNull();
    }
}
