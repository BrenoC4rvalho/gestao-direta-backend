package br.com.gestaodireta.messaging.service;

import br.com.gestaodireta.messaging.telegram.config.TelegramAudioDebugProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TelegramAudioDebugFileSaver {

    private static final Logger LOGGER = LoggerFactory.getLogger(TelegramAudioDebugFileSaver.class);

    private final TelegramAudioDebugProperties properties;

    public TelegramAudioDebugFileSaver(TelegramAudioDebugProperties properties) {
        this.properties = properties;
    }

    public void save(String updateId, String sourceMessageId, byte[] audioBytes, String mimeType) {
        if (!properties.isSaveEnabled()) {
            return;
        }
        if (audioBytes == null || audioBytes.length == 0) {
            LOGGER.warn(
                    "telegram voice debug file not saved: reason=EMPTY_AUDIO updateId={} sourceMessageId={} mimeType={}",
                    updateId,
                    sourceMessageId,
                    mimeType);
            return;
        }
        Path path = targetPath(updateId, sourceMessageId, mimeType);
        try {
            Files.createDirectories(path.getParent());
            Files.write(path, audioBytes, StandardOpenOption.CREATE_NEW);
            long writtenBytes = Files.size(path);
            if (writtenBytes != audioBytes.length) {
                LOGGER.error(
                        "telegram voice debug file size mismatch: updateId={} sourceMessageId={} expectedBytes={} writtenBytes={} path={}",
                        updateId,
                        sourceMessageId,
                        audioBytes.length,
                        writtenBytes,
                        path);
                return;
            }
            LOGGER.info(
                    "telegram voice debug file saved: updateId={} sourceMessageId={} bytes={} mimeType={} path={}",
                    updateId,
                    sourceMessageId,
                    audioBytes.length,
                    mimeType,
                    path);
        } catch (IOException exception) {
            LOGGER.warn(
                    "telegram voice debug file not saved: updateId={} sourceMessageId={} bytes={} mimeType={} path={} exception={}",
                    updateId,
                    sourceMessageId,
                    audioBytes.length,
                    mimeType,
                    path,
                    exception.getClass().getSimpleName());
        }
    }

    private Path targetPath(String updateId, String sourceMessageId, String mimeType) {
        String fileName =
                "telegram-%s-%s.%s"
                        .formatted(
                                safeIdentifier(updateId),
                                safeIdentifier(sourceMessageId),
                                extension(mimeType));
        return Path.of(properties.getDirectory()).resolve(fileName);
    }

    private String extension(String mimeType) {
        if (mimeType == null || mimeType.isBlank()) {
            return "ogg";
        }
        return switch (mimeType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT)) {
            case "audio/ogg" -> "ogg";
            case "audio/opus" -> "opus";
            default -> "bin";
        };
    }

    private String safeIdentifier(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.replaceAll("[^A-Za-z0-9_-]", "_");
    }
}
