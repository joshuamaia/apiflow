package br.com.joshua.apiflowservice.shared.domain;

/**
 * Violation of a business rule. Messages must be safe to show to API clients (no personal data).
 */
public abstract class BusinessException extends RuntimeException {

    private final String code;

    protected BusinessException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
