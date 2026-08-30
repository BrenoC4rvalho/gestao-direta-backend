package br.com.gestaodireta.ai.service;

public final class AiErrorSanitizer {

    private static final int MAXIMUM_MESSAGE_LENGTH = 500;

    private AiErrorSanitizer() {}

    public static String message(String value) {
        if (value == null || value.isBlank()) {
            return "Provider returned no error message";
        }

        String sanitized =
                value.replaceAll(
                                "(?i)(api[_ -]?key|x-goog-api-key|authorization)\\s*[:=]\\s*\\S+",
                                "$1=[REDACTED]")
                        .replaceAll("(?i)([?&]key=)[^&\\s]+", "$1[REDACTED]")
                        .replaceAll("[\\r\\n]+", " ")
                        .trim();
        if (sanitized.length() <= MAXIMUM_MESSAGE_LENGTH) {
            return sanitized;
        }
        return sanitized.substring(0, MAXIMUM_MESSAGE_LENGTH) + "...";
    }
}
