package br.com.gestaodireta.ai.audiobenchmark;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import br.com.gestaodireta.ApiApplication;
import br.com.gestaodireta.ai.service.FinancialTransactionExtractionService;
import br.com.gestaodireta.ai.service.dto.FinancialTransactionExtractionResult;
import br.com.gestaodireta.ai.transcription.AudioTranscriptionClient;
import br.com.gestaodireta.ai.transcription.AudioTranscriptionRequest;
import br.com.gestaodireta.ai.transcription.AudioTranscriptionResult;
import br.com.gestaodireta.financial.entity.FinancialCategory;
import br.com.gestaodireta.financial.enumeration.FinancialCategoryStatus;
import br.com.gestaodireta.financial.enumeration.TransactionType;
import br.com.gestaodireta.messaging.service.FinancialMessageEvidenceExtractor;
import br.com.gestaodireta.messaging.service.FinancialTransactionExtractionResultValidator;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Opt-in live benchmark; it never instantiates Telegram processors or financial repositories. */
@Testcontainers
@Tag("ai-audio-benchmark")
class AiFinancialAudioBenchmarkIT {
    private static final Path AUDIO_ROOT = Path.of("audios", "audio");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("gestaodireta_audio_benchmark")
            .withUsername("gestaodireta")
            .withPassword("benchmark");

    @Test
    void shouldRunConfiguredAudioBenchmark() {
        AudioBenchmarkReportWriter writer = new AudioBenchmarkReportWriter();
        if (Boolean.getBoolean("ai.audio.benchmark.rebuild")) {
            writer.write(List.of());
            return;
        }
        assumeTrue(Boolean.getBoolean("ai.audio.benchmark.allow-external"),
                "Live audio benchmark requires -Dai.audio.benchmark.allow-external=true");
        List<AudioBenchmarkCase> dataset = new AudioBenchmarkDataset().load();
        int limit = integerProperty("ai.audio.benchmark.limit", 50);
        if (limit < 1 || limit > 50) {
            throw new IllegalArgumentException("ai.audio.benchmark.limit must be between 1 and 50");
        }
        List<AudioBenchmarkCase> cases = dataset.subList(0, limit);
        Set<String> completed = Boolean.getBoolean("ai.audio.benchmark.resume")
                ? writer.completedIds() : Set.of();
        List<AudioBenchmarkCaseResult> results = new ArrayList<>();
        Map<String, Transcription> transcriptions = new HashMap<>();
        for (String provider : providers()) {
            try (ConfigurableApplicationContext context = context(provider)) {
                Runner runner = new Runner(context, provider, writer);
                for (AudioBenchmarkCase item : cases) {
                    String key = provider + ":" + item.id();
                    if (completed.contains(key)) {
                        continue;
                    }
                    AudioBenchmarkCaseResult result = runner.run(item, key, transcriptions);
                    results.add(result);
                    writer.append(result);
                }
            }
        }
        writer.write(results);
    }

    private ConfigurableApplicationContext context(String extractionProvider) {
        return new SpringApplicationBuilder(ApiApplication.class).profiles("test")
                .web(WebApplicationType.SERVLET)
                .run("--app.ai.provider=" + extractionProvider,
                        "--app.ai.financial-extraction.enabled=true",
                        "--app.ai.audio-transcription.enabled=true", "--server.port=0",
                        "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                        "--spring.datasource.username=" + POSTGRES.getUsername(),
                        "--spring.datasource.password=" + POSTGRES.getPassword());
    }

    private List<String> providers() {
        String provider = System.getProperty("ai.audio.benchmark.provider", "ollama")
                .toLowerCase(Locale.ROOT);
        return switch (provider) {
            case "gemini", "ollama" -> List.of(provider);
            case "both" -> List.of("gemini", "ollama");
            default -> throw new IllegalArgumentException(
                    "ai.audio.benchmark.provider must be gemini, ollama or both");
        };
    }

    private int integerProperty(String name, int defaultValue) {
        String value = System.getProperty(name);
        return value == null || value.isBlank() ? defaultValue : Integer.parseInt(value);
    }

    private record Transcription(String text, long elapsedMillis) {}

    private static final class Runner {
        private final AudioTranscriptionClient transcriptionClient;
        private final FinancialTransactionExtractionService extractionService;
        private final FinancialTransactionExtractionResultValidator validator;
        private final FinancialMessageEvidenceExtractor evidence;
        private final String extractionProvider;
        private final AudioBenchmarkScorer scorer;
        private final AudioBenchmarkReportWriter writer;

        Runner(ConfigurableApplicationContext context, String extractionProvider,
                AudioBenchmarkReportWriter writer) {
            transcriptionClient = context.getBean(AudioTranscriptionClient.class);
            extractionService = context.getBean(FinancialTransactionExtractionService.class);
            validator = context.getBean(FinancialTransactionExtractionResultValidator.class);
            evidence = context.getBean(FinancialMessageEvidenceExtractor.class);
            this.extractionProvider = extractionProvider;
            scorer = new AudioBenchmarkScorer(evidence);
            this.writer = writer;
        }

        AudioBenchmarkCaseResult run(AudioBenchmarkCase item, String resultId,
                Map<String, Transcription> transcriptions) {
            long totalStart = System.nanoTime();
            String transcription = null;
            FinancialTransactionExtractionResult extraction = null;
            long transcriptionMillis = 0;
            long extractionMillis = 0;
            String error = null;
            try {
                String transcriptionKey = transcriptionClient.providerName() + ":" + item.id();
                Transcription cached = transcriptions.get(transcriptionKey);
                if (cached == null) {
                    cached = transcribe(item, resultId);
                    transcriptions.put(transcriptionKey, cached);
                }
                transcriptionMillis = cached.elapsedMillis();
                transcription = cached.text();
                long start = System.nanoTime();
                extraction = extractionService.extract(transcription, "Fazenda Boa Vista", categories());
                extractionMillis = elapsed(start);
                if (extraction != null) {
                    validator.validate(transcription, extraction);
                }
                if (Boolean.getBoolean("ai.audio.benchmark.diagnostic")) {
                    long diagnosticStart = System.nanoTime();
                    FinancialTransactionExtractionResult textExtraction =
                            extractionService.extract(item.originalText(), "Fazenda Boa Vista", categories());
                    writer.appendDiagnostic(new AudioBenchmarkDiagnosticResult(
                            item.id(), item.group(), item.originalText(), transcription,
                            AudioBenchmarkExtraction.from(textExtraction),
                            AudioBenchmarkExtraction.from(extraction), elapsed(diagnosticStart), extractionMillis));
                }
            } catch (Exception exception) {
                error = exception.getClass().getSimpleName() + ": " + exception.getMessage();
            }
            return scorer.score(item, transcription, extraction, transcriptionClient.providerName(),
                    transcriptionClient.modelName(), extractionProvider, extractionService.model(),
                    transcriptionMillis, extractionMillis, elapsed(totalStart), error);
        }

        private Transcription transcribe(AudioBenchmarkCase item, String resultId) throws Exception {
            Path audio = AUDIO_ROOT.resolve(item.audioFile());
            if (!Files.isRegularFile(audio)) {
                throw new IllegalStateException("Audio fixture not found: " + audio);
            }
            long start = System.nanoTime();
            AudioTranscriptionResult result = transcriptionClient.transcribe(new AudioTranscriptionRequest(
                    Files.readAllBytes(audio), "audio/ogg", resultId, item.id()));
            return new Transcription(result.text(), elapsed(start));
        }

        private long elapsed(long start) {
            return (System.nanoTime() - start) / 1_000_000;
        }

        private List<FinancialCategory> categories() {
            return List.of(category("Vendas", TransactionType.INCOME),
                    category("Insumos", TransactionType.EXPENSE),
                    category("Combustível", TransactionType.EXPENSE),
                    category("Manutenção", TransactionType.EXPENSE),
                    category("Utilidades", TransactionType.EXPENSE));
        }

        private FinancialCategory category(String name, TransactionType type) {
            FinancialCategory category = new FinancialCategory();
            category.setName(name);
            category.setType(type);
            category.setStatus(FinancialCategoryStatus.ACTIVE);
            return category;
        }
    }
}
