package br.com.gestaodireta.messaging.service;

import static org.assertj.core.api.Assertions.assertThatCode;

import br.com.gestaodireta.messaging.telegram.config.TelegramAudioDebugProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TelegramAudioDebugFileSaverTest {

    @TempDir Path tempDir;

    @Test
    void shouldNotCreateFileWhenDebugIsDisabled() {
        TelegramAudioDebugProperties properties = properties(tempDir.resolve("debug"), false);

        saver(properties).save("519749095", "391", new byte[] {1, 2, 3}, "audio/ogg");

        Assertions.assertThat(tempDir.resolve("debug")).doesNotExist();
    }

    @Test
    void shouldSaveOggBytesUsingUpdateAndSourceMessageIdentifiers() throws Exception {
        Path debugDirectory = tempDir.resolve("missing/debug");
        byte[] audio = new byte[] {0, 1, 2, 3, 4};

        saver(properties(debugDirectory, true)).save("519749095", "391", audio, "audio/ogg");

        Path file = debugDirectory.resolve("telegram-519749095-391.ogg");
        Assertions.assertThat(file).exists();
        Assertions.assertThat(Files.readAllBytes(file)).containsExactly(audio);
        Assertions.assertThat(Files.size(file)).isEqualTo(audio.length);
    }

    @Test
    void shouldPreserveExistingFileWhenNameCollides() throws Exception {
        Path debugDirectory = tempDir.resolve("debug");
        Files.createDirectories(debugDirectory);
        Path file = debugDirectory.resolve("telegram-519749095-391.ogg");
        Files.write(file, new byte[] {9, 9});

        saver(properties(debugDirectory, true))
                .save("519749095", "391", new byte[] {1, 2, 3}, "audio/ogg");

        Assertions.assertThat(Files.readAllBytes(file)).containsExactly(9, 9);
    }

    @Test
    void shouldAbsorbLocalSavingFailure() throws Exception {
        Path fileInsteadOfDirectory = tempDir.resolve("not-a-directory");
        Files.createFile(fileInsteadOfDirectory);

        assertThatCode(
                        () ->
                                saver(properties(fileInsteadOfDirectory, true))
                                        .save(
                                                "519749095",
                                                "391",
                                                new byte[] {1, 2, 3},
                                                "audio/ogg"))
                .doesNotThrowAnyException();
    }

    private TelegramAudioDebugFileSaver saver(TelegramAudioDebugProperties properties) {
        return new TelegramAudioDebugFileSaver(properties);
    }

    private TelegramAudioDebugProperties properties(Path directory, boolean saveEnabled) {
        TelegramAudioDebugProperties properties = new TelegramAudioDebugProperties();
        properties.setDirectory(directory.toString());
        properties.setSaveEnabled(saveEnabled);
        return properties;
    }
}
