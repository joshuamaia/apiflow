package br.com.joshua.apiflowservice.flowtracking.application;

import java.util.HexFormat;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Fallback id generator, used only when Micrometer Tracing has no active span (e.g. tracing disabled).
 * Produces ids in the same W3C/B3 hex format.
 */
public final class IdGenerator {

    private IdGenerator() {
    }

    public static String traceId() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return HexFormat.of().toHexDigits(random.nextLong()) + HexFormat.of().toHexDigits(random.nextLong());
    }

    public static String spanId() {
        return HexFormat.of().toHexDigits(ThreadLocalRandom.current().nextLong());
    }
}
