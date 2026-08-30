package br.com.gestaodireta.ai.service;

public class AiModelNotAvailableException extends AiProviderException {

    public AiModelNotAvailableException(String message, String model) {
        this(message, "ai", model, null, null);
    }

    public AiModelNotAvailableException(
            String message, String provider, String model, Integer statusCode, Throwable cause) {
        super(Reason.MODEL_NOT_FOUND, statusCode, provider, model, "NOT_FOUND", message, cause);
    }
}
