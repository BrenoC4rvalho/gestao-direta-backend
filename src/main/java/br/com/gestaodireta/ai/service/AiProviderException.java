package br.com.gestaodireta.ai.service;

public class AiProviderException extends RuntimeException {

    public enum Reason {
        TIMEOUT,
        INVALID_REQUEST,
        UNAUTHORIZED,
        FORBIDDEN,
        MODEL_NOT_FOUND,
        RATE_LIMIT,
        SERVICE_UNAVAILABLE,
        INVALID_RESPONSE
    }

    private final Reason reason;

    private final Integer statusCode;

    private final String provider;

    private final String model;

    private final String providerStatus;

    public AiProviderException(Reason reason, Integer statusCode, String message, Throwable cause) {
        this(reason, statusCode, null, null, null, message, cause);
    }

    public AiProviderException(
            Reason reason,
            Integer statusCode,
            String provider,
            String model,
            String providerStatus,
            String message,
            Throwable cause) {
        super(message, cause);
        this.reason = reason;
        this.statusCode = statusCode;
        this.provider = provider;
        this.model = model;
        this.providerStatus = providerStatus;
    }

    public Reason getReason() {
        return reason;
    }

    public Integer getStatusCode() {
        return statusCode;
    }

    public String getProvider() {
        return provider;
    }

    public String getModel() {
        return model;
    }

    public String getProviderStatus() {
        return providerStatus;
    }
}
