package br.com.gestaodireta.ai.audiobenchmark;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.gestaodireta.messaging.service.FinancialMessageEvidenceExtractor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class AiAudioBenchmarkFrameworkTest {
    @Test
    void shouldLoadTheExpectedDatasetDistributionAndPortableAudioPaths() {
        List<AudioBenchmarkCase> cases = new AudioBenchmarkDataset().load();

        assertThat(cases).hasSize(150);
        assertThat(cases).extracting(AudioBenchmarkCase::id).doesNotHaveDuplicates();
        assertThat(cases)
                .filteredOn(item -> item.category() == AudioBenchmarkGroup.ENTRY)
                .hasSize(50);
        assertThat(cases)
                .filteredOn(item -> item.category() == AudioBenchmarkGroup.EXPENSE)
                .hasSize(50);
        assertThat(cases)
                .filteredOn(item -> item.category() == AudioBenchmarkGroup.INVALID)
                .hasSize(20);
        assertThat(cases)
                .filteredOn(item -> item.category() == AudioBenchmarkGroup.INCOMPLETE)
                .hasSize(30);
        assertThat(cases)
                .allSatisfy(
                        item -> {
                            assertThat(item.audioFile()).endsWith(".ogg").doesNotStartWith("/");
                            assertThat(item.audioVariant()).isEqualTo(AudioBenchmarkVariant.CLEAN);
                            assertThat(item.voiceId()).isNotBlank();
                        });
    }

    @Test
    void shouldCalculateWordAndCharacterErrorRatesUsingLimitedNormalization() {
        assertThat(AudioTextAccuracy.wer("Recebi R$ 1.200,00", "recebi r$ 1.200,00")).isZero();
        assertThat(AudioTextAccuracy.cer("café", "CAFE!")).isZero();
        assertThat(AudioTextAccuracy.wer("recebi mil reais", "recebi dois mil reais"))
                .isGreaterThan(0);
    }

    @Test
    void shouldKeepMonetaryEvidenceObservable() {
        FinancialMessageEvidenceExtractor evidence = new FinancialMessageEvidenceExtractor();

        assertThat(
                        AudioTextAccuracy.sameAmounts(
                                "Paguei R$ 1200 de adubo.",
                                "paguei 1200 reais de adubo.",
                                evidence))
                .isTrue();
        assertThat(
                        AudioTextAccuracy.sameAmounts(
                                "Paguei R$ 1200 de adubo.", "paguei 200 reais de adubo.", evidence))
                .isFalse();
    }

    @Test
    void shouldAppendAndLoadCompletedResultsForResume() throws Exception {
        Path output = Files.createTempDirectory("audio-benchmark");
        AudioBenchmarkReportWriter writer = new AudioBenchmarkReportWriter(output);
        AudioBenchmarkCaseResult result =
                new AudioBenchmarkCaseResult(
                        "ollama:ENTRY-001",
                        "entry/entry-001.ogg",
                        "original",
                        "transcribed",
                        "whisper",
                        "small",
                        "ollama",
                        "model",
                        AudioBenchmarkGroup.ENTRY,
                        AudioBenchmarkVariant.CLEAN,
                        "VALID",
                        null,
                        null,
                        null,
                        null,
                        0,
                        0,
                        true,
                        true,
                        true,
                        1,
                        1,
                        2,
                        AudioBenchmarkResultType.CORRECT,
                        null);

        writer.append(result);

        assertThat(writer.completedIds()).containsExactly("ollama:ENTRY-001");
    }
}
