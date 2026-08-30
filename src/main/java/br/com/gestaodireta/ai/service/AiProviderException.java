package br.com.gestaodireta.ai.service;

public class AiProviderException extends RuntimeException {

    public enum Reason {
        TIMEOUT,
        UNAUTHORIZED,
        FORBIDDEN,
        RATE_LIMIT,
        SERVICE_UNAVAILABLE,
        INVALID_RESPONSE
    }

    private final Reason reason;

    private final Integer statusCode;

    public AiProviderException(Reason reason, Integer statusCode, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
        this.statusCode = statusCode;
    }

    public Reason getReason() {
        return reason;
    }

    public Integer getStatusCode() {
        return statusCode;
    }
}
