package br.com.gestaodireta.ai.audiobenchmark;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class AiAudioFixtureValidationTest {
    @Test
    void shouldHaveExactlyFiftyNonEmptyOggFixtures() throws IOException {
        List<AudioBenchmarkCase> cases = new AudioBenchmarkDataset().load();

        assertThat(cases).allSatisfy(item -> {
            assertThat(item.audioFile()).endsWith(".ogg");
            Path path = AudioBenchmarkDataset.ROOT.resolve("audio").resolve(item.audioFile());
            assertThat(path).isRegularFile();
            assertThat(Files.size(path)).isGreaterThan(0);
            assertThat(Files.readAllBytes(path)).startsWith((byte) 'O', (byte) 'g', (byte) 'g', (byte) 'S');
        });
        try (var files = Files.list(AudioBenchmarkDataset.ROOT.resolve("audio"))) {
            assertThat(files.filter(path -> path.getFileName().toString().endsWith(".ogg")).count())
                    .isEqualTo(50);
        }
    }
}
