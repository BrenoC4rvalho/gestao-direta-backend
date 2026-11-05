package br.com.gestaodireta.ai.audiobenchmark;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.gestaodireta.ai.service.dto.FinancialTransactionExtractionResult;
import br.com.gestaodireta.ai.service.dto.FinancialTransactionExtractionStatus;
import br.com.gestaodireta.financial.enumeration.TransactionType;
import br.com.gestaodireta.messaging.service.FinancialMessageEvidenceExtractor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AiAudioBenchmarkReportWriterTest {
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @TempDir
    Path temporaryDirectory;

    @Test
    void shouldRoundTripCurrentResultAndPreserveNestedCategoryFields() throws Exception {
        Path output = temporaryDirectory.resolve("roundtrip");
        AudioBenchmarkReportWriter writer = new AudioBenchmarkReportWriter(output);
        writer.append(result("AUDIO_001"));
        writer.write(List.of());

        JsonNode serializedLine = objectMapper.readTree(
                Files.readString(output.resolve("results.jsonl"), StandardCharsets.UTF_8));
        AudioBenchmarkCaseResult[] roundTrip = objectMapper.readValue(
                Files.readString(output.resolve("results.json"), StandardCharsets.UTF_8),
                AudioBenchmarkCaseResult[].class);

        assertThat(serializedLine.has("category")).isFalse();
        assertThat(serializedLine.path("groundTruth").path("categoryName").asText())
                .isEqualTo("Combustível");
        assertThat(roundTrip).hasSize(1);
        assertThat(roundTrip[0].extraction().categoryName()).isEqualTo("Combustível");
        assertThat(writer.completedIds()).contains("AUDIO_001", "ollama:AUDIO_001");
    }

    @Test
    void shouldRejectLegacyCategoryFieldWithoutIgnoringItSilently() throws Exception {
        Path output = temporaryDirectory.resolve("legacy");
        Files.createDirectories(output);
        Files.writeString(output.resolve("results.jsonl"),
                "{\"id\":\"gemini:ENTRY-001\",\"category\":\"ENTRY\"}\n",
                StandardCharsets.UTF_8);

        AudioBenchmarkReportWriter writer = new AudioBenchmarkReportWriter(output);
        writer.write(List.of());

        JsonNode summary = objectMapper.readTree(Files.readString(output.resolve("summary.json")));
        JsonNode rejected = objectMapper.readTree(Files.readString(output.resolve("rejected-results.json")));
        JsonNode results = objectMapper.readTree(Files.readString(output.resolve("results.json")));

        assertThat(summary.path("acceptedResults").asInt()).isZero();
        assertThat(summary.path("rejectedResults").asInt()).isEqualTo(1);
        assertThat(rejected).hasSize(1);
        assertThat(rejected.get(0).path("fields")).hasSize(1);
        assertThat(rejected.get(0).path("fields").get(0).asText()).isEqualTo("category");
        assertThat(results).isEmpty();
    }

    @Test
    void shouldReadFiftyCurrentResultsAndReportDuplicatesExplicitly() throws Exception {
        Path output = temporaryDirectory.resolve("fifty");
        AudioBenchmarkReportWriter writer = new AudioBenchmarkReportWriter(output);
        List<AudioBenchmarkCase> cases = new AudioBenchmarkDataset().load();
        for (AudioBenchmarkCase item : cases) {
            writer.append(result(item.id()));
        }
        writer.append(result("AUDIO_001"));
        writer.append(result("AUDIO_002"));
        writer.append(result("AUDIO_003"));
        writer.append(result("AUDIO_004"));
        Files.writeString(output.resolve("results.jsonl"),
                "{\"id\":\"gemini:ENTRY-001\",\"category\":\"ENTRY\"}\n",
                StandardCharsets.UTF_8, StandardOpenOption.APPEND);

        writer.write(List.of());

        JsonNode summary = objectMapper.readTree(Files.readString(output.resolve("summary.json")));
        JsonNode results = objectMapper.readTree(Files.readString(output.resolve("results.json")));
        JsonNode rejected = objectMapper.readTree(Files.readString(output.resolve("rejected-results.json")));

        assertThat(summary.path("total").asInt()).isEqualTo(50);
        assertThat(summary.path("acceptedResults").asInt()).isEqualTo(50);
        assertThat(summary.path("rejectedResults").asInt()).isEqualTo(5);
        assertThat(results).hasSize(50);
        assertThat(rejected).hasSize(5);
        assertThat(writer.completedIds()).contains("AUDIO_050", "ollama:AUDIO_050");
    }

    @Test
    void shouldPreserveOptionalExtractionAndErrorFields() throws Exception {
        Path output = temporaryDirectory.resolve("optional");
        AudioBenchmarkReportWriter writer = new AudioBenchmarkReportWriter(output);
        AudioBenchmarkCase item = new AudioBenchmarkDataset().load().getFirst();
        AudioBenchmarkCaseResult result = new AudioBenchmarkScorer(new FinancialMessageEvidenceExtractor())
                .score(item, null, null, "whisper", "local", "ollama", "model", 0, 0, 5,
                        "provider timeout");
        writer.append(result);
        writer.write(List.of());

        AudioBenchmarkCaseResult[] roundTrip = objectMapper.readValue(
                Files.readString(output.resolve("results.json"), StandardCharsets.UTF_8),
                AudioBenchmarkCaseResult[].class);

        assertThat(roundTrip).hasSize(1);
        assertThat(roundTrip[0].extraction()).isNull();
        assertThat(roundTrip[0].error()).isEqualTo("provider timeout");
    }

    @Test
    void shouldRescoreStoredResultsWithTheCurrentNumericNormalization() throws Exception {
        Path output = temporaryDirectory.resolve("rescore");
        AudioBenchmarkCase item = new AudioBenchmarkDataset().load().getFirst();
        FinancialTransactionExtractionResult extraction = new FinancialTransactionExtractionResult(
                FinancialTransactionExtractionStatus.VALID,
                true,
                TransactionType.EXPENSE,
                new BigDecimal("150.00"),
                null,
                null,
                "combustível",
                "Combustível",
                BigDecimal.ONE,
                List.of());
        AudioBenchmarkCaseResult current = new AudioBenchmarkScorer(new FinancialMessageEvidenceExtractor())
                .score(item, "Registra uma despesa de R$ 150,00 com combustível.", extraction,
                        "whisper", "local", "ollama", "model", 1, 2, 3, null);
        ObjectNode legacy = objectMapper.valueToTree(current);
        legacy.remove(List.of("normalizedWer", "normalizedCer", "normalizedTranscriptionCorrect"));
        Files.createDirectories(output);
        Files.writeString(output.resolve("results.jsonl"), legacy + "\n", StandardCharsets.UTF_8);

        new AudioBenchmarkReportWriter(output).write(List.of());

        AudioBenchmarkCaseResult[] results = objectMapper.readValue(
                Files.readString(output.resolve("results.json"), StandardCharsets.UTF_8),
                AudioBenchmarkCaseResult[].class);
        assertThat(results).hasSize(1);
        assertThat(results[0].normalizedWer()).isZero();
        assertThat(results[0].normalizedTranscriptionCorrect()).isTrue();
        assertThat(results[0].amountTranscriptionCorrect()).isTrue();
    }

    @Test
    void shouldRejectMalformedJsonInAControlledWay() throws Exception {
        Path output = temporaryDirectory.resolve("malformed");
        Files.createDirectories(output);
        Files.writeString(output.resolve("results.jsonl"), "not-json\n", StandardCharsets.UTF_8);

        AudioBenchmarkReportWriter writer = new AudioBenchmarkReportWriter(output);
        writer.write(List.of());

        JsonNode rejected = objectMapper.readTree(Files.readString(output.resolve("rejected-results.json")));

        assertThat(rejected).hasSize(1);
        assertThat(rejected.get(0).path("reason").asText()).startsWith("invalid JSON:");
    }

    private AudioBenchmarkCaseResult result(String id) {
        AudioBenchmarkCase item = new AudioBenchmarkDataset().load().stream()
                .filter(candidate -> candidate.id().equals(id))
                .findFirst()
                .orElseThrow();
        FinancialTransactionExtractionResult extraction = new FinancialTransactionExtractionResult(
                FinancialTransactionExtractionStatus.VALID,
                true,
                TransactionType.EXPENSE,
                new BigDecimal("150.00"),
                null,
                null,
                "combustível",
                "Combustível",
                BigDecimal.ONE,
                List.of());
        return new AudioBenchmarkScorer(new FinancialMessageEvidenceExtractor())
                .score(item, item.originalText(), extraction, "whisper", "local", "ollama", "model", 1, 2, 3, null);
    }
}
