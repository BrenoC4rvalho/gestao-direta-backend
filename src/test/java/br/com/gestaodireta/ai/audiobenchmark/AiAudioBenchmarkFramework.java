package br.com.gestaodireta.ai.audiobenchmark;

import br.com.gestaodireta.financial.enumeration.TransactionType;
import br.com.gestaodireta.messaging.service.FinancialMessageEvidenceExtractor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

enum AudioBenchmarkGroup {
    ENTRY,
    EXPENSE,
    INVALID,
    INCOMPLETE
}

enum AudioBenchmarkVariant {
    CLEAN,
    FAST,
    SLOW,
    LOW_VOLUME,
    NOISY,
    PAUSES
}

enum AudioBenchmarkResultType {
    TRANSCRIPTION_ERROR,
    AMOUNT_TRANSCRIPTION_ERROR,
    EXTRACTION_ERROR,
    WRONG_TYPE,
    WRONG_AMOUNT,
    WRONG_FIELDS,
    HALLUCINATED_DATA,
    SHOULD_HAVE_BLOCKED,
    CORRECT,
    TECHNICAL_ERROR
}

record AudioBenchmarkCase(
        String id,
        String audioFile,
        String originalText,
        AudioBenchmarkGroup category,
        String expectedOutcome,
        TransactionType expectedTransactionType,
        BigDecimal expectedAmount,
        List<String> missingFields,
        AudioBenchmarkVariant audioVariant,
        String voiceId) {}

record AudioBenchmarkCaseResult(
        String id,
        String audioFile,
        String originalText,
        String transcribedText,
        String transcriptionProvider,
        String transcriptionModel,
        String extractionProvider,
        String extractionModel,
        AudioBenchmarkGroup category,
        AudioBenchmarkVariant audioVariant,
        String expectedOutcome,
        TransactionType expectedType,
        BigDecimal expectedAmount,
        TransactionType actualType,
        BigDecimal actualAmount,
        double wer,
        double cer,
        boolean transcriptionCorrect,
        boolean amountTranscriptionCorrect,
        boolean extractionCorrect,
        long transcriptionMillis,
        long extractionMillis,
        long totalMillis,
        AudioBenchmarkResultType resultType,
        String reason) {}

final class AudioBenchmarkDataset {
    private static final Path MANIFEST = Path.of("audios", "audio-benchmark-dataset.json");
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    List<AudioBenchmarkCase> load() {
        try {
            List<Map<String, Object>> items =
                    objectMapper.readValue(MANIFEST.toFile(), new TypeReference<>() {});
            return items.stream().map(this::caseOf).toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read audio benchmark manifest", exception);
        }
    }

    private AudioBenchmarkCase caseOf(Map<String, Object> item) {
        String category = string(item, "category");
        String expectedOutcome = string(item, "expectedOutcome");
        String file =
                item.containsKey("audioFile")
                        ? string(item, "audioFile")
                        : string(item, "file").replace(".wav", ".ogg");
        String type = string(item, "expectedTransactionType");
        BigDecimal amount =
                item.get("expectedAmount") == null
                        ? null
                        : new BigDecimal(item.get("expectedAmount").toString());
        @SuppressWarnings("unchecked")
        List<String> fields =
                (List<String>)
                        item.getOrDefault("missingFields", defaultMissingFields(expectedOutcome));
        return new AudioBenchmarkCase(
                string(item, "id"),
                file,
                string(item, "originalText"),
                AudioBenchmarkGroup.valueOf(category),
                expectedOutcome,
                type == null ? null : TransactionType.valueOf(type),
                amount,
                fields,
                AudioBenchmarkVariant.valueOf(stringOr(item, "audioVariant", "CLEAN")),
                stringOr(item, "voiceId", "pt_BR-faber-medium"));
    }

    private List<String> defaultMissingFields(String outcome) {
        return "VALID".equals(outcome) ? List.of() : List.of("amount", "description");
    }

    private String string(Map<String, Object> item, String key) {
        return item.get(key) == null ? null : item.get(key).toString();
    }

    private String stringOr(Map<String, Object> item, String key, String fallback) {
        String value = string(item, key);
        return value == null ? fallback : value;
    }
}

final class AudioTextAccuracy {
    private AudioTextAccuracy() {}

    static String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    static double wer(String expected, String actual) {
        return distance(
                        List.of(normalize(expected).split(" ")),
                        List.of(normalize(actual).split(" ")))
                / (double) Math.max(1, words(expected));
    }

    static double cer(String expected, String actual) {
        return distance(chars(normalize(expected)), chars(normalize(actual)))
                / (double) Math.max(1, normalize(expected).length());
    }

    static boolean sameAmounts(
            String expected, String actual, FinancialMessageEvidenceExtractor evidence) {
        return new LinkedHashSet<>(evidence.monetaryAmounts(expected))
                .equals(new LinkedHashSet<>(evidence.monetaryAmounts(actual)));
    }

    private static int words(String value) {
        String normalized = normalize(value);
        return normalized.isBlank() ? 0 : normalized.split(" ").length;
    }

    private static List<Character> chars(String value) {
        return value.chars().mapToObj(c -> (char) c).toList();
    }

    private static <T> int distance(List<T> left, List<T> right) {
        int[] row = new int[right.size() + 1];
        for (int j = 0; j <= right.size(); j++) row[j] = j;
        for (int i = 1; i <= left.size(); i++) {
            int previous = row[0];
            row[0] = i;
            for (int j = 1; j <= right.size(); j++) {
                int before = row[j];
                row[j] =
                        Math.min(
                                Math.min(row[j] + 1, row[j - 1] + 1),
                                previous + (left.get(i - 1).equals(right.get(j - 1)) ? 0 : 1));
                previous = before;
            }
        }
        return row[right.size()];
    }
}

final class AudioBenchmarkReportWriter {
    private final Path output;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    AudioBenchmarkReportWriter() {
        this(Path.of("target", "ai-audio-benchmark"));
    }

    AudioBenchmarkReportWriter(Path output) {
        this.output = output;
    }

    void append(AudioBenchmarkCaseResult result) {
        try {
            Files.createDirectories(output);
            Files.writeString(
                    output.resolve("results.jsonl"),
                    objectMapper.writeValueAsString(result) + "\n",
                    StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not append audio benchmark result", exception);
        }
    }

    Set<String> completedIds() {
        Path file = output.resolve("results.jsonl");
        if (!Files.exists(file)) return Set.of();
        try {
            return Files.lines(file)
                    .filter(line -> !line.isBlank())
                    .map(
                            line -> {
                                try {
                                    return objectMapper
                                            .readValue(line, AudioBenchmarkCaseResult.class)
                                            .id();
                                } catch (IOException exception) {
                                    throw new IllegalStateException(
                                            "Invalid results.jsonl", exception);
                                }
                            })
                    .collect(Collectors.toSet());
        } catch (IOException exception) {
            throw new IllegalStateException("Could not load resumed results", exception);
        }
    }

    void write(Collection<AudioBenchmarkCaseResult> results) {
        try {
            Files.createDirectories(output);
            List<AudioBenchmarkCaseResult> sorted =
                    results.stream()
                            .sorted(
                                    Comparator.comparing(AudioBenchmarkCaseResult::id)
                                            .thenComparing(
                                                    AudioBenchmarkCaseResult::extractionProvider))
                            .toList();
            Map<String, Object> summary =
                    Map.of(
                            "cases",
                            sorted.size(),
                            "byProvider",
                            sorted.stream()
                                    .collect(
                                            Collectors.groupingBy(
                                                    AudioBenchmarkCaseResult::extractionProvider,
                                                    Collectors.collectingAndThen(
                                                            Collectors.toList(), this::metrics))));
            objectMapper.writeValue(output.resolve("summary.json").toFile(), summary);
            StringBuilder csv =
                    new StringBuilder(
                            "id,audioFile,originalText,transcribedText,transcriptionProvider,extractionProvider,wer,cer,result,totalMillis,reason\n");
            for (AudioBenchmarkCaseResult result : sorted)
                csv.append(csv(result.id()))
                        .append(',')
                        .append(csv(result.audioFile()))
                        .append(',')
                        .append(csv(result.originalText()))
                        .append(',')
                        .append(csv(result.transcribedText()))
                        .append(',')
                        .append(csv(result.transcriptionProvider()))
                        .append(',')
                        .append(csv(result.extractionProvider()))
                        .append(',')
                        .append(result.wer())
                        .append(',')
                        .append(result.cer())
                        .append(',')
                        .append(result.resultType())
                        .append(',')
                        .append(result.totalMillis())
                        .append(',')
                        .append(csv(result.reason()))
                        .append('\n');
            Files.writeString(output.resolve("results.csv"), csv, StandardCharsets.UTF_8);
            Files.writeString(output.resolve("report.html"), html(sorted), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not write audio benchmark report", exception);
        }
    }

    private Map<String, Object> metrics(List<AudioBenchmarkCaseResult> values) {
        return Map.of(
                "total",
                values.size(),
                "transcriptionAccuracy",
                rate(values, AudioBenchmarkCaseResult::transcriptionCorrect),
                "amountTranscriptionAccuracy",
                rate(values, AudioBenchmarkCaseResult::amountTranscriptionCorrect),
                "extractionAccuracy",
                rate(values, AudioBenchmarkCaseResult::extractionCorrect),
                "endToEndAccuracy",
                rate(values, r -> r.resultType() == AudioBenchmarkResultType.CORRECT));
    }

    private double rate(
            List<AudioBenchmarkCaseResult> values,
            java.util.function.Predicate<AudioBenchmarkCaseResult> predicate) {
        return values.isEmpty()
                ? 0
                : 100.0 * values.stream().filter(predicate).count() / values.size();
    }

    private String html(List<AudioBenchmarkCaseResult> results) {
        String rows =
                results.stream()
                        .map(
                                r ->
                                        "<tr data-filter=\""
                                                + r.category()
                                                + " "
                                                + r.audioVariant()
                                                + " "
                                                + r.resultType()
                                                + "\"><td>"
                                                + escape(r.id())
                                                + "</td><td><audio controls src=\"../../audios/audio/"
                                                + escape(r.audioFile())
                                                + "\"></audio></td><td>"
                                                + escape(r.originalText())
                                                + "</td><td>"
                                                + escape(r.transcribedText())
                                                + "</td><td>"
                                                + r.resultType()
                                                + "</td></tr>")
                        .collect(Collectors.joining());
        return "<!doctype html><meta charset=\"utf-8\"><title>Benchmark de áudio</title><h1>Benchmark IA — Áudio</h1><p>Filtros: <button onclick=\"f('')\">Todos</button><button onclick=\"f('ENTRY')\">ENTRY</button><button onclick=\"f('EXPENSE')\">EXPENSE</button><button onclick=\"f('CLEAN')\">CLEAN</button></p><table border=\"1\"><tr><th>ID</th><th>Áudio</th><th>Original</th><th>Transcrição</th><th>Resultado</th></tr><tbody id=\"cases\">"
                + rows
                + "</tbody></table><script>function f(v){document.querySelectorAll('#cases tr').forEach(r=>r.hidden=v&&!r.dataset.filter.includes(v))}</script>";
    }

    private String csv(Object value) {
        return value == null ? "" : "\"" + value.toString().replace("\"", "\"\"") + "\"";
    }

    private String escape(String value) {
        return value == null
                ? ""
                : value.replace("&", "&amp;")
                        .replace("<", "&lt;")
                        .replace(">", "&gt;")
                        .replace("\"", "&quot;");
    }
}
