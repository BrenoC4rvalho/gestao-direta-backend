package br.com.gestaodireta.ai.infrastructure.gemini;

import br.com.gestaodireta.ai.transcription.AudioTranscriptionProperties;
import br.com.gestaodireta.ai.transcription.AudioTranscriptionRequest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class GeminiTranscriptionDebugResponseSaver {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(GeminiTranscriptionDebugResponseSaver.class);

    private final AudioTranscriptionProperties properties;

    GeminiTranscriptionDebugResponseSaver(AudioTranscriptionProperties properties) {
        this.properties = properties;
    }

    void save(AudioTranscriptionRequest request, String responseBody) {
        if (!properties.isDebugResponse() || responseBody == null || responseBody.isBlank()) {
            return;
        }
        Path path = targetPath(request);
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, responseBody, StandardOpenOption.CREATE_NEW);
            LOGGER.debug(
                    "gemini transcription debug response saved: updateId={} sourceMessageId={} path={}",
                    request.updateId(),
                    request.sourceMessageId(),
                    path);
        } catch (IOException exception) {
            LOGGER.warn(
                    "gemini transcription debug response not saved: updateId={} sourceMessageId={} path={} exception={}",
                    request.updateId(),
                    request.sourceMessageId(),
                    path,
                    exception.getClass().getSimpleName());
        }
    }

    private Path targetPath(AudioTranscriptionRequest request) {
        String fileName =
                "gemini-response-%s-%s.json"
                        .formatted(
                                safeIdentifier(request.updateId()),
                                safeIdentifier(request.sourceMessageId()));
        return Path.of(properties.getDebugResponseDirectory()).resolve(fileName);
    }

    private String safeIdentifier(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.replaceAll("[^A-Za-z0-9_-]", "_");
    }
}
