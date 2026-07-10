package br.com.gestaodireta.ai.service;

public class AiModelNotAvailableException extends RuntimeException {

    private final String model;

    public AiModelNotAvailableException(String message, String model) {
        super(message);
        this.model = model;
    }

    public String getModel() {
        return model;
    }
}
