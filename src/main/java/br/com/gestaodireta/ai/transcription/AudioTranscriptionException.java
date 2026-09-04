package br.com.gestaodireta.ai.transcription;

public class AudioTranscriptionException extends RuntimeException {

    public enum Reason {
        DISABLED,
        TIMEOUT,
        PROVIDER_ERROR,
        INVALID_RESPONSE,
        EMPTY_TRANSCRIPT
    }

    private final Reason reason;

    public AudioTranscriptionException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
