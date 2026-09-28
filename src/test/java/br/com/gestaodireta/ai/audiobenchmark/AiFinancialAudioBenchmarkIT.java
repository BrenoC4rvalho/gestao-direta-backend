package br.com.gestaodireta.ai.audiobenchmark;

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
import java.math.BigDecimal;
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

/**
 * Opt-in benchmark. It deliberately does not instantiate Telegram processors or repositories, so no
 * pending or definitive transaction can be written while exercising the real AI clients.
 */
@Testcontainers
@Tag("ai-audio-benchmark")
class AiFinancialAudioBenchmarkIT {
    private static final Path AUDIO_ROOT = Path.of("audios", "audio");

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("gestaodireta_audio_benchmark")
                    .withUsername("gestaodireta")
                    .withPassword("benchmark");

    @Test
    void shouldRunConfiguredAudioBenchmark() {
        List<AudioBenchmarkCase> dataset = new AudioBenchmarkDataset().load();
        int limit = integerProperty("ai.audio.benchmark.limit", dataset.size());
        List<AudioBenchmarkCase> cases = dataset.stream().limit(Math.max(0, limit)).toList();
        AudioBenchmarkReportWriter writer = new AudioBenchmarkReportWriter();
        boolean resume = Boolean.getBoolean("ai.audio.benchmark.resume");
        Set<String> completed = resume ? writer.completedIds() : Set.of();
        List<AudioBenchmarkCaseResult> results = new ArrayList<>();
        Map<String, Transcription> transcriptions = new HashMap<>();
        for (String provider : providers()) {
            try (ConfigurableApplicationContext context = context(provider)) {
                Runner runner = new Runner(context, provider);
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
        return new SpringApplicationBuilder(ApiApplication.class)
                .profiles("test")
                .web(WebApplicationType.SERVLET)
                .run(
                        "--app.ai.provider=" + extractionProvider,
                        "--app.ai.financial-extraction.enabled=true",
                        "--app.ai.audio-transcription.enabled=true",
                        "--server.port=0",
                        "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                        "--spring.datasource.username=" + POSTGRES.getUsername(),
                        "--spring.datasource.password=" + POSTGRES.getPassword());
    }

    private List<String> providers() {
        String provider =
                System.getProperty("ai.audio.benchmark.provider", "both").toLowerCase(Locale.ROOT);
        return switch (provider) {
            case "gemini", "ollama" -> List.of(provider);
            case "both" -> List.of("gemini", "ollama");
            default ->
                    throw new IllegalArgumentException(
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

        Runner(ConfigurableApplicationContext context, String extractionProvider) {
            this.transcriptionClient = context.getBean(AudioTranscriptionClient.class);
            this.extractionService = context.getBean(FinancialTransactionExtractionService.class);
            this.validator = context.getBean(FinancialTransactionExtractionResultValidator.class);
            this.evidence = context.getBean(FinancialMessageEvidenceExtractor.class);
            this.extractionProvider = extractionProvider;
        }

        AudioBenchmarkCaseResult run(
                AudioBenchmarkCase item, String id, Map<String, Transcription> transcriptions) {
            long totalStart = System.nanoTime();
            String transcription = null;
            FinancialTransactionExtractionResult extraction = null;
            long transcriptionMillis = 0;
            long extractionMillis = 0;
            String error = null;
            try {
                Transcription cached = transcriptions.get(item.id());
                if (cached == null) {
                    cached = transcribe(item, id);
                    transcriptions.put(item.id(), cached);
                }
                transcriptionMillis = cached.elapsedMillis();
                transcription = cached.text();
                long start = System.nanoTime();
                extraction =
                        extractionService.extract(transcription, "Fazenda Boa Vista", categories());
                extractionMillis = elapsed(start);
            } catch (Exception exception) {
                error = exception.getClass().getSimpleName() + ": " + exception.getMessage();
            }
            return score(
                    item,
                    transcription,
                    extraction,
                    transcriptionMillis,
                    extractionMillis,
                    elapsed(totalStart),
                    error);
        }

        private Transcription transcribe(AudioBenchmarkCase item, String id) throws Exception {
            Path audio = AUDIO_ROOT.resolve(item.audioFile());
            if (!Files.isRegularFile(audio)) {
                throw new IllegalStateException("Audio fixture not found: " + audio);
            }
            long start = System.nanoTime();
            AudioTranscriptionResult result =
                    transcriptionClient.transcribe(
                            new AudioTranscriptionRequest(
                                    Files.readAllBytes(audio), "audio/ogg", id, item.id()));
            return new Transcription(result.text(), elapsed(start));
        }

        private AudioBenchmarkCaseResult score(
                AudioBenchmarkCase item,
                String transcription,
                FinancialTransactionExtractionResult extraction,
                long transcriptionMillis,
                long extractionMillis,
                long totalMillis,
                String error) {
            String transcribed = transcription == null ? "" : transcription;
            double wer = AudioTextAccuracy.wer(item.originalText(), transcribed);
            double cer = AudioTextAccuracy.cer(item.originalText(), transcribed);
            boolean textCorrect = wer == 0.0d;
            boolean amountCorrect =
                    AudioTextAccuracy.sameAmounts(item.originalText(), transcribed, evidence);
            FinancialTransactionExtractionResultValidator.ValidationResult validation =
                    error == null ? validator.validate(transcribed, extraction) : null;
            AudioBenchmarkResultType resultType =
                    classify(item, extraction, validation, textCorrect, amountCorrect, error);
            boolean extractionCorrect = resultType == AudioBenchmarkResultType.CORRECT;
            return new AudioBenchmarkCaseResult(
                    extractionProvider + ":" + item.id(),
                    item.audioFile(),
                    item.originalText(),
                    transcribed,
                    transcriptionClient.providerName(),
                    transcriptionClient.modelName(),
                    extractionProvider,
                    extractionService.model(),
                    item.category(),
                    item.audioVariant(),
                    item.expectedOutcome(),
                    item.expectedTransactionType(),
                    item.expectedAmount(),
                    extraction == null ? null : extraction.type(),
                    extraction == null ? null : extraction.amount(),
                    wer,
                    cer,
                    textCorrect,
                    amountCorrect,
                    extractionCorrect,
                    transcriptionMillis,
                    extractionMillis,
                    totalMillis,
                    resultType,
                    error == null && validation != null ? validation.reason().name() : error);
        }

        private AudioBenchmarkResultType classify(
                AudioBenchmarkCase item,
                FinancialTransactionExtractionResult extraction,
                FinancialTransactionExtractionResultValidator.ValidationResult validation,
                boolean textCorrect,
                boolean amountCorrect,
                String error) {
            if (error != null) return AudioBenchmarkResultType.TECHNICAL_ERROR;
            if (!amountCorrect) return AudioBenchmarkResultType.AMOUNT_TRANSCRIPTION_ERROR;
            if (!textCorrect) return AudioBenchmarkResultType.TRANSCRIPTION_ERROR;
            if (!"VALID".equals(item.expectedOutcome())) {
                return validation.valid()
                        ? AudioBenchmarkResultType.SHOULD_HAVE_BLOCKED
                        : AudioBenchmarkResultType.CORRECT;
            }
            if (!validation.valid()) return AudioBenchmarkResultType.EXTRACTION_ERROR;
            if (item.expectedTransactionType() != extraction.type())
                return AudioBenchmarkResultType.WRONG_TYPE;
            if (!same(item.expectedAmount(), extraction.amount()))
                return AudioBenchmarkResultType.WRONG_AMOUNT;
            return AudioBenchmarkResultType.CORRECT;
        }

        private boolean same(BigDecimal expected, BigDecimal actual) {
            return expected != null && actual != null && expected.compareTo(actual) == 0;
        }

        private long elapsed(long start) {
            return (System.nanoTime() - start) / 1_000_000;
        }

        private List<FinancialCategory> categories() {
            return List.of(
                    category("Vendas", TransactionType.INCOME),
                    category("Insumos", TransactionType.EXPENSE),
                    category("Combustível", TransactionType.EXPENSE),
                    category("Manutenção", TransactionType.EXPENSE));
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
