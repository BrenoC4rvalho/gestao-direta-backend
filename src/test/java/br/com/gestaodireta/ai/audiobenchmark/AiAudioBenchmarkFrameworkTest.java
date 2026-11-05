package br.com.gestaodireta.ai.audiobenchmark;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.gestaodireta.ai.service.dto.FinancialTransactionExtractionResult;
import br.com.gestaodireta.ai.service.dto.FinancialTransactionExtractionStatus;
import br.com.gestaodireta.financial.enumeration.TransactionType;
import br.com.gestaodireta.messaging.service.FinancialMessageEvidenceExtractor;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class AiAudioBenchmarkFrameworkTest {
    @Test
    void shouldLoadExactlyTheExpectedDataset() {
        List<AudioBenchmarkCase> cases = new AudioBenchmarkDataset().load();

        assertThat(cases).hasSize(50);
        assertThat(cases).extracting(AudioBenchmarkCase::id).containsExactlyElementsOf(
                java.util.stream.IntStream.rangeClosed(1, 50)
                        .mapToObj(index -> "AUDIO_%03d".formatted(index)).toList());
        assertThat(cases).filteredOn(item -> item.group() == AudioBenchmarkGroup.A).hasSize(10);
        assertThat(cases).filteredOn(item -> item.group() == AudioBenchmarkGroup.B).hasSize(8);
        assertThat(cases).filteredOn(item -> item.group() == AudioBenchmarkGroup.C).hasSize(8);
        assertThat(cases).filteredOn(item -> item.group() == AudioBenchmarkGroup.D).hasSize(8);
        assertThat(cases).filteredOn(item -> item.group() == AudioBenchmarkGroup.E).hasSize(8);
        assertThat(cases).filteredOn(item -> item.group() == AudioBenchmarkGroup.F).hasSize(8);
        assertThat(cases).allSatisfy(item -> {
            assertThat(item.originalText()).isNotBlank();
            assertThat(item.audioFile()).isEqualTo(item.id() + ".ogg");
            assertThat(item.groundTruth()).isNotNull();
        });
    }

    @Test
    void shouldCalculateWordAndCharacterErrorRatesWithoutDestroyingNumbers() {
        assertThat(AudioTextAccuracy.wer("Recebi R$ 1.200,00", "recebi r$ 1.200,00")).isZero();
        assertThat(AudioTextAccuracy.cer("café", "CAFE!")).isZero();
        assertThat(AudioTextAccuracy.wer("recebi mil reais", "recebi dois mil reais")).isGreaterThan(0);
        assertThat(AudioTextAccuracy.sameAmounts(
                "Paguei R$ 1.200,00 de adubo.",
                "paguei 1200 reais de adubo.",
                new FinancialMessageEvidenceExtractor())).isTrue();
    }

    @Test
    void shouldTreatWrittenAndBrazilianNumericMoneyAsEquivalent() {
        FinancialMessageEvidenceExtractor evidence = new FinancialMessageEvidenceExtractor();

        assertThat(AudioTextAccuracy.sameAmounts(
                "cento e cinquenta reais", "R$ 150,00", evidence)).isTrue();
        assertThat(AudioTextAccuracy.sameAmounts(
                "dois mil e trezentos reais", "2.300 reais", evidence)).isTrue();
        assertThat(AudioTextAccuracy.sameAmounts(
                "oitocentos e cinquenta reais", "R$ 850", evidence)).isTrue();
        assertThat(AudioTextAccuracy.sameAmounts(
                "noventa e nove reais e noventa centavos", "R$ 99,90", evidence)).isTrue();
        assertThat(AudioTextAccuracy.sameAmounts(
                "mil e duzentos reais", "1.200,00", evidence)).isTrue();
        assertThat(AudioTextAccuracy.sameAmounts(
                "trezentos e vinte reais", "R$ 520,00", evidence)).isFalse();
    }

    @Test
    void shouldExposeRawAndMoneyNormalizedTranscriptionMetricsSeparately() {
        FinancialMessageEvidenceExtractor evidence = new FinancialMessageEvidenceExtractor();
        AudioMoneyNormalizer money = new AudioMoneyNormalizer(evidence);
        String expected = "Registra uma despesa de cento e cinquenta reais com combustível.";
        String actual = "Registra uma despesa de R$ 150,00 com combustível.";

        assertThat(AudioTextAccuracy.wer(expected, actual)).isGreaterThan(0);
        assertThat(AudioTextAccuracy.normalizedWer(expected, actual, money)).isZero();
        assertThat(AudioTextAccuracy.normalizedCer(expected, actual, money)).isZero();
        assertThat(AudioTextAccuracy.sameAmounts(expected, actual, evidence)).isTrue();
    }

    @Test
    void shouldPreserveMultipleAmountsAndRejectUnsupportedNegativeAmounts() {
        FinancialMessageEvidenceExtractor evidence = new FinancialMessageEvidenceExtractor();

        assertThat(AudioTextAccuracy.sameAmounts(
                "trezentos reais e oitocentos reais", "R$ 300,00 e R$ 800,00", evidence)).isTrue();
        assertThat(new AudioMoneyNormalizer(evidence).amounts("-R$ 100,00")).isEmpty();
    }

    @Test
    void shouldClassifyNumericRepresentationAsCorrectAndRealNumericErrorAsTranscriptionError() {
        AudioBenchmarkCase item = new AudioBenchmarkDataset().load().stream()
                .filter(candidate -> candidate.id().equals("AUDIO_001"))
                .findFirst().orElseThrow();
        FinancialTransactionExtractionResult extraction = valid(
                TransactionType.EXPENSE, "150.00", null, null, "combustível", "Combustível");
        AudioBenchmarkScorer scorer = new AudioBenchmarkScorer(new FinancialMessageEvidenceExtractor());

        AudioBenchmarkCaseResult equivalent = scorer.score(
                item, "Registra uma despesa de R$ 150,00 com combustível.", extraction,
                "test", "test", "test", "test", 1, 1, 2, null);
        AudioBenchmarkCaseResult different = scorer.score(
                item, "Registra uma despesa de R$ 520,00 com combustível.", extraction,
                "test", "test", "test", "test", 1, 1, 2, null);

        assertThat(equivalent.transcriptionCorrect()).isFalse();
        assertThat(equivalent.normalizedTranscriptionCorrect()).isTrue();
        assertThat(equivalent.amountTranscriptionCorrect()).isTrue();
        assertThat(equivalent.resultType()).isEqualTo(AudioBenchmarkResultType.CORRECT);
        assertThat(different.amountTranscriptionCorrect()).isFalse();
        assertThat(different.resultType()).isEqualTo(AudioBenchmarkResultType.AMOUNT_TRANSCRIPTION_ERROR);
    }

    @Test
    void shouldDeduplicateRepeatedWordsAndIgnoreRepeatedStopWords() {
        AudioBenchmarkScorer scorer = new AudioBenchmarkScorer(new FinancialMessageEvidenceExtractor());

        assertThat(scorer.meaningfulWords("Venda de de milho, milho."))
                .containsExactlyInAnyOrder("venda", "milho");
        assertThat(scorer.sameDescription("Venda de milho", "venda de de milho milho"))
                .isTrue();
    }

    @Test
    void shouldIgnoreCasePunctuationAndFormattingDifferences() {
        AudioBenchmarkScorer scorer = new AudioBenchmarkScorer(new FinancialMessageEvidenceExtractor());

        assertThat(scorer.sameDescription("Manutenção do trator", "  MANUTENÇÃO, de   trator! "))
                .isTrue();
        assertThat(scorer.sameDescription("Diesel para o pulverizador", "diesel para o pulverizador"))
                .isTrue();
    }

    @Test
    void shouldRejectSemanticallyDifferentDescriptions() {
        AudioBenchmarkScorer scorer = new AudioBenchmarkScorer(new FinancialMessageEvidenceExtractor());

        assertThat(scorer.sameDescription("Venda de milho", "Venda de soja")).isFalse();
        assertThat(scorer.sameDescription("Manutenção do trator", "Manutenção da colheitadeira"))
                .isFalse();
    }

    @Test
    void shouldPreserveExistingNullAndEmptyDescriptionContract() {
        AudioBenchmarkScorer scorer = new AudioBenchmarkScorer(new FinancialMessageEvidenceExtractor());

        assertThat(scorer.sameDescription(null, null)).isTrue();
        assertThat(scorer.sameDescription(null, "")).isFalse();
        assertThat(scorer.sameDescription("", null)).isFalse();
        assertThat(scorer.sameDescription("", "   ")).isTrue();
        assertThat(scorer.sameDescription("", "milho")).isFalse();
    }

    @Test
    void shouldResolveRelativeDatesAgainstTheBenchmarkClock() {
        AudioBenchmarkCase item = new AudioBenchmarkDataset().load().stream()
                .filter(candidate -> candidate.id().equals("AUDIO_021"))
                .findFirst().orElseThrow();
        Clock clock = Clock.fixed(Instant.parse("2026-11-01T15:00:00Z"), ZoneId.of("America/Sao_Paulo"));
        FinancialTransactionExtractionResult extraction = valid(
                TransactionType.EXPENSE, "500.00", java.time.LocalDate.of(2026, 11, 1), null, "combustível", "Combustível");

        AudioBenchmarkCaseResult result = new AudioBenchmarkScorer(new FinancialMessageEvidenceExtractor(), clock)
                .score(item, item.originalText(), extraction, "test", "test", "test", "test", 1, 1, 2, null);

        assertThat(result.comparison().dateCorrect()).isTrue();
    }

    @Test
    void shouldClassifyMultipleTransactionsAsAControlledBlock() {
        AudioBenchmarkCase item = new AudioBenchmarkDataset().load().stream()
                .filter(candidate -> candidate.id().equals("AUDIO_043"))
                .findFirst().orElseThrow();
        FinancialTransactionExtractionResult extraction = new FinancialTransactionExtractionResult(
                FinancialTransactionExtractionStatus.MULTIPLE_TRANSACTIONS, true, null, null, null, null, null, null,
                BigDecimal.ONE, List.of("multipleTransactions"));

        AudioBenchmarkCaseResult result = new AudioBenchmarkScorer(new FinancialMessageEvidenceExtractor())
                .score(item, item.originalText(), extraction, "test", "test", "test", "test", 1, 1, 2, null);

        assertThat(result.resultType()).isEqualTo(AudioBenchmarkResultType.CORRECT);
        assertThat(result.comparison().countCorrect()).isTrue();
    }

    @Test
    void shouldWriteStructuredReportsAndResumeIds() throws Exception {
        java.nio.file.Path output = java.nio.file.Files.createTempDirectory("audio-benchmark-report");
        AudioBenchmarkCase item = new AudioBenchmarkDataset().load().getFirst();
        AudioBenchmarkCaseResult result = new AudioBenchmarkScorer(new FinancialMessageEvidenceExtractor())
                .score(item, item.originalText(), valid(TransactionType.EXPENSE, "150.00", null, null,
                        "combustível", "Combustível"), "whisper", "local", "ollama", "model", 1, 2, 3, null);
        AudioBenchmarkReportWriter writer = new AudioBenchmarkReportWriter(output);
        writer.append(result);
        writer.write(List.of(result));

        assertThat(writer.completedIds()).contains("AUDIO_001", "ollama:AUDIO_001");
        assertThat(output.resolve("summary.json")).exists();
        assertThat(output.resolve("results.csv")).exists();
        assertThat(output.resolve("report.html")).exists();
    }

    private FinancialTransactionExtractionResult valid(TransactionType type, String amount,
            java.time.LocalDate date, java.time.LocalDate dueDate, String description, String category) {
        return new FinancialTransactionExtractionResult(
                FinancialTransactionExtractionStatus.VALID, true, type, new BigDecimal(amount), date, dueDate,
                description, category, BigDecimal.ONE, List.of());
    }
}
