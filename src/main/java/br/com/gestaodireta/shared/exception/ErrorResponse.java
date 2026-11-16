package br.com.gestaodireta.shared.exception;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record ErrorResponse(
        LocalDateTime timestamp,
        int status,
        String error,
        String message,
        String path,
        List<String> details,
        Map<String, String> fieldErrors) {

    public ErrorResponse(
            LocalDateTime timestamp,
            int status,
            String error,
            String message,
            String path,
            List<String> details) {
        this(timestamp, status, error, message, path, details, Map.of());
    }
}
