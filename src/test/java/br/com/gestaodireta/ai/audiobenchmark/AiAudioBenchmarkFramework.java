package br.com.gestaodireta.ai.audiobenchmark;

import br.com.gestaodireta.ai.service.dto.FinancialTransactionExtractionResult;
import br.com.gestaodireta.ai.service.dto.FinancialTransactionExtractionStatus;
import br.com.gestaodireta.financial.enumeration.TransactionType;
import br.com.gestaodireta.messaging.service.FinancialMessageEvidenceExtractor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

enum AudioBenchmarkGroup {
    A, B, C, D, E, F
}

enum AudioBenchmarkResultType {
    TRANSCRIPTION_ERROR,
    AMOUNT_TRANSCRIPTION_ERROR,
    EXTRACTION_ERROR,
    WRONG_TYPE,
    WRONG_AMOUNT,
    WRONG_DATE,
    WRONG_DUE_DATE,
    WRONG_DESCRIPTION,
    WRONG_CATEGORY,
    WRONG_MISSING_FIELDS,
    HALLUCINATED_DATA,
    SHOULD_HAVE_BLOCKED,
    CORRECT,
    TECHNICAL_ERROR
}

record AudioBenchmarkGroundTruth(
        String id,
        String status,
        boolean financial,
        TransactionType type,
        BigDecimal amount,
        String description,
        String transactionDate,
        String dueDate,
        String categoryName,
        List<String> missingFields,
        int expectedCount,
        boolean clarification,
        List<String> notEvaluated) {
    AudioBenchmarkGroundTruth {
        missingFields = missingFields == null ? List.of() : List.copyOf(missingFields);
        notEvaluated = notEvaluated == null ? List.of() : List.copyOf(notEvaluated);
    }
}

record AudioBenchmarkCase(
        String id,
        AudioBenchmarkGroup group,
        String originalText,
        String audioFile,
        AudioBenchmarkGroundTruth groundTruth) {}

record AudioBenchmarkExtraction(
        FinancialTransactionExtractionStatus status,
        boolean financial,
        TransactionType type,
        BigDecimal amount,
        LocalDate transactionDate,
        LocalDate dueDate,
        String description,
        String categoryName,
        BigDecimal confidence,
        List<String> missingFields) {
    static AudioBenchmarkExtraction from(FinancialTransactionExtractionResult result) {
        if (result == null) {
            return null;
        }
        return new AudioBenchmarkExtraction(
                result.status(),
                result.isFinancialTransaction(),
                result.type(),
                result.amount(),
                result.transactionDate(),
                result.dueDate(),
                result.description(),
                result.categoryName(),
                result.confidence(),
                result.missingFields());
    }
}

record AudioBenchmarkComparison(
        boolean typeCorrect,
        boolean amountCorrect,
        boolean dateCorrect,
        boolean dueDateCorrect,
        boolean descriptionCorrect,
        boolean categoryCorrect,
        boolean missingFieldsCorrect,
        boolean countCorrect,
        boolean hallucinatedData,
        boolean extractionCorrect,
        String reason) {}

record AudioBenchmarkCaseResult(
        String id,
        AudioBenchmarkGroup group,
        String audioFile,
        String originalText,
        String transcribedText,
        AudioBenchmarkGroundTruth groundTruth,
        AudioBenchmarkExtraction extraction,
        String transcriptionProvider,
        String transcriptionModel,
        String extractionProvider,
        String extractionModel,
        double wer,
        double cer,
        boolean transcriptionCorrect,
        boolean amountTranscriptionCorrect,
        AudioBenchmarkComparison comparison,
        long transcriptionMillis,
        long extractionMillis,
        long totalMillis,
        String error,
        AudioBenchmarkResultType resultType,
        double normalizedWer,
        double normalizedCer,
        boolean normalizedTranscriptionCorrect) {}

record AudioBenchmarkDiagnosticResult(
        String id,
        AudioBenchmarkGroup group,
        String originalText,
        String transcribedText,
        AudioBenchmarkExtraction textExtraction,
        AudioBenchmarkExtraction audioExtraction,
        long textExtractionMillis,
        long audioExtractionMillis) {}

record AudioBenchmarkRejectedResult(
        int lineNumber,
        String id,
        String reason,
        List<String> fields) {
    AudioBenchmarkRejectedResult {
        fields = fields == null ? List.of() : List.copyOf(fields);
    }
}

record AudioBenchmarkReadResults(
        List<AudioBenchmarkCaseResult> results,
        List<AudioBenchmarkRejectedResult> rejectedResults) {
    AudioBenchmarkReadResults {
        results = results == null ? List.of() : List.copyOf(results);
        rejectedResults = rejectedResults == null ? List.of() : List.copyOf(rejectedResults);
    }
}

record AudioBenchmarkParsedResult(
        int lineNumber,
        AudioBenchmarkCaseResult result) {}

final class AudioBenchmarkDataset {
    static final Path ROOT = Path.of("audios");
    private static final Path CASES = ROOT.resolve("cases.json");
    private static final Path GROUND_TRUTH = ROOT.resolve("ground-truth.json");
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    List<AudioBenchmarkCase> load() {
        try {
            List<ManifestItem> cases =
                    objectMapper.readValue(CASES.toFile(), new TypeReference<>() {});
            List<GroundTruthItem> expected =
                    objectMapper.readValue(GROUND_TRUTH.toFile(), new TypeReference<>() {});
            validateIds(cases.stream().map(ManifestItem::id).toList(), "manifest");
            validateIds(expected.stream().map(GroundTruthItem::id).toList(), "ground truth");
            Map<String, GroundTruthItem> byId = expected.stream().collect(Collectors.toMap(GroundTruthItem::id, item -> item));
            return cases.stream().map(item -> toCase(item, Objects.requireNonNull(byId.get(item.id())))).toList();
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("Could not read audio benchmark dataset", exception);
        }
    }

    private AudioBenchmarkCase toCase(ManifestItem item, GroundTruthItem expected) {
        if (!item.id().equals(expected.id())) {
            throw new IllegalStateException("Ground truth does not match " + item.id());
        }
        return new AudioBenchmarkCase(
                item.id(),
                AudioBenchmarkGroup.valueOf(item.group()),
                item.text(),
                item.audioFile(),
                new AudioBenchmarkGroundTruth(
                        expected.id(), expected.status(), expected.financial(), expected.type(), expected.amount(),
                        expected.description(), expected.transactionDate(), expected.dueDate(), expected.categoryName(),
                        expected.missingFields(), expected.expectedCount(), expected.clarification(), expected.notEvaluated()));
    }

    private void validateIds(List<String> ids, String source) {
        List<String> expected = java.util.stream.IntStream.rangeClosed(1, 50)
                .mapToObj(index -> "AUDIO_%03d".formatted(index)).toList();
        if (!ids.equals(expected)) {
            throw new IllegalStateException(source + " IDs must be AUDIO_001..AUDIO_050");
        }
    }

    private record ManifestItem(
            String id,
            String group,
            String text,
            String audioFile,
            String format,
            String codec,
            BigDecimal durationSeconds) {}

    private record GroundTruthItem(
            String id, String status, boolean financial, TransactionType type, BigDecimal amount, String description,
            String transactionDate, String dueDate, String categoryName, List<String> missingFields,
            int expectedCount, boolean clarification, List<String> notEvaluated) {}
}

final class AudioMoneyNormalizer {
    private static final String NUMBER_WORD =
            "(?:um|uma|dois|duas|tres|quatro|cinco|seis|sete|oito|nove|dez|onze|doze|treze|quatorze|catorze|"
                    + "quinze|dezesseis|dezessete|dezoito|dezenove|vinte|trinta|quarenta|cinquenta|sessenta|"
                    + "setenta|oitenta|noventa|cem|cento|duzentos|trezentos|quatrocentos|quinhentos|seiscentos|"
                    + "setecentos|oitocentos|novecentos|mil|milhao|milhoes)";
    private static final Pattern WORD_AMOUNT = Pattern.compile(
            "(?i)(?<![a-z])(" + NUMBER_WORD + "(?:\\s+(?:e\\s+)?" + NUMBER_WORD + ")*)\\s+reais?"
                    + "(?:\\s+e\\s+(" + NUMBER_WORD + "(?:\\s+(?:e\\s+)?" + NUMBER_WORD
                    + ")*)\\s+centavos?)?");
    private static final Pattern NUMERIC_AMOUNT = Pattern.compile(
            "(?i)(?:r\\$\\s*-?(?:\\d{1,3}(?:\\.\\d{3})+(?:,\\d{1,2})?|\\d+(?:,\\d{1,2})?)"
                    + "|-?(?:\\d{1,3}(?:\\.\\d{3})+(?:,\\d{1,2})?|\\d+(?:,\\d{1,2})?)\\s+reais?)");
    private static final Pattern DECIMAL_NUMERIC_AMOUNT = Pattern.compile(
            "(?<!\\d)-?(?:\\d{1,3}(?:\\.\\d{3})*),\\d{1,2}(?!\\d)");
    private static final Pattern CONTEXTUAL_NUMERIC_AMOUNT = Pattern.compile(
            "(?i)(?:por|paguei|pagou|recebi|recebeu|entrou|entrada(?:\\s+de)?|gastei|gastou|"
                    + "custou|foi|caiu|creditado|pix(?:\\s+de)?|transferencia(?:\\s+de)?|"
                    + "recebimento(?:\\s+de)?|valor\\s+de)\\s*(?:r\\$\\s*)?"
                    + "(-?(?:\\d{1,3}(?:\\.\\d{3})+(?:,\\d{1,2})?|\\d+(?:,\\d{1,2})?))");
    private static final Pattern CONTEXTUAL_WORD_THOUSAND = Pattern.compile(
            "(?i)(?:por|paguei|pagou|recebi|recebeu|entrou|entrada(?:\\s+de)?|gastei|gastou|"
                    + "custou|foi|caiu|creditado|pix(?:\\s+de)?|transferencia(?:\\s+de)?|"
                    + "recebimento(?:\\s+de)?|valor\\s+de)\\s+("
                    + NUMBER_WORD + "(?:\\s+(?:e\\s+)?" + NUMBER_WORD + ")*\\s+mil)"
                    + "(?!\\s+e\\s+" + NUMBER_WORD + ")\\b");
    private static final Map<String, Integer> NUMBER_WORD_VALUES = Map.ofEntries(
            Map.entry("um", 1), Map.entry("uma", 1), Map.entry("dois", 2), Map.entry("duas", 2),
            Map.entry("tres", 3), Map.entry("quatro", 4), Map.entry("cinco", 5), Map.entry("seis", 6),
            Map.entry("sete", 7), Map.entry("oito", 8), Map.entry("nove", 9), Map.entry("dez", 10),
            Map.entry("onze", 11), Map.entry("doze", 12), Map.entry("treze", 13), Map.entry("quatorze", 14),
            Map.entry("catorze", 14), Map.entry("quinze", 15), Map.entry("dezesseis", 16),
            Map.entry("dezessete", 17), Map.entry("dezoito", 18), Map.entry("dezenove", 19),
            Map.entry("vinte", 20), Map.entry("trinta", 30), Map.entry("quarenta", 40),
            Map.entry("cinquenta", 50), Map.entry("sessenta", 60), Map.entry("setenta", 70),
            Map.entry("oitenta", 80), Map.entry("noventa", 90), Map.entry("cem", 100),
            Map.entry("cento", 100), Map.entry("duzentos", 200), Map.entry("trezentos", 300),
            Map.entry("quatrocentos", 400), Map.entry("quinhentos", 500), Map.entry("seiscentos", 600),
            Map.entry("setecentos", 700), Map.entry("oitocentos", 800), Map.entry("novecentos", 900));
    private final FinancialMessageEvidenceExtractor evidence;

    AudioMoneyNormalizer(FinancialMessageEvidenceExtractor evidence) {
        this.evidence = evidence;
    }

    List<BigDecimal> amounts(String text) {
        if (text == null) {
            return List.of();
        }
        if (containsUnsupportedNegativeAmount(text)) {
            return List.of();
        }
        List<BigDecimal> values = new ArrayList<>();
        Matcher numericMatcher = NUMERIC_AMOUNT.matcher(evidence.normalize(text));
        while (numericMatcher.find()) {
            BigDecimal value = parseNumeric(numericMatcher.group());
            if (value != null) {
                addIfAbsent(values, value);
            }
        }
        Matcher decimalMatcher = DECIMAL_NUMERIC_AMOUNT.matcher(evidence.normalize(text));
        while (decimalMatcher.find()) {
            BigDecimal value = parseNumeric(decimalMatcher.group());
            if (value != null) {
                addIfAbsent(values, value);
            }
        }
        Matcher contextualMatcher = CONTEXTUAL_NUMERIC_AMOUNT.matcher(evidence.normalize(text));
        while (contextualMatcher.find()) {
            BigDecimal value = parseNumeric(contextualMatcher.group(1));
            if (value != null) {
                addIfAbsent(values, value);
            }
        }
        Matcher contextualWordMatcher = CONTEXTUAL_WORD_THOUSAND.matcher(evidence.normalize(text));
        while (contextualWordMatcher.find()) {
            BigDecimal value = parseWords(contextualWordMatcher.group(1));
            if (value != null) {
                addIfAbsent(values, value);
            }
        }
        Matcher matcher = WORD_AMOUNT.matcher(evidence.normalize(text));
        while (matcher.find()) {
            BigDecimal whole = parseWords(matcher.group(1));
            if (whole == null) {
                continue;
            }
            BigDecimal value = whole;
            if (matcher.group(2) != null) {
                BigDecimal cents = parseWords(matcher.group(2));
                if (cents == null || cents.compareTo(BigDecimal.valueOf(99)) > 0) {
                    continue;
                }
                removeEquivalent(values, whole);
                value = whole.add(cents.movePointLeft(2));
            }
            addIfAbsent(values, canonical(value));
        }
        return values;
    }

    private boolean containsUnsupportedNegativeAmount(String text) {
        String normalized = evidence.normalize(text);
        return normalized.matches(".*(?:-\\s*(?:r\\$\\s*)?|r\\$\\s*-\\s*)\\d.*");
    }

    String normalizeText(String text) {
        if (text == null) {
            return "";
        }
        String normalized = evidence.normalize(text);
        StringBuffer result = new StringBuffer();
        Matcher numericMatcher = NUMERIC_AMOUNT.matcher(normalized);
        while (numericMatcher.find()) {
            BigDecimal value = parseNumeric(numericMatcher.group());
            if (value == null) {
                numericMatcher.appendReplacement(result, Matcher.quoteReplacement(numericMatcher.group()));
            } else {
                numericMatcher.appendReplacement(result, Matcher.quoteReplacement(token(value)));
            }
        }
        numericMatcher.appendTail(result);
        String withNumericAmounts = result.toString();
        Matcher decimalMatcher = DECIMAL_NUMERIC_AMOUNT.matcher(withNumericAmounts);
        result = new StringBuffer();
        while (decimalMatcher.find()) {
            BigDecimal value = parseNumeric(decimalMatcher.group());
            if (value == null) {
                decimalMatcher.appendReplacement(result, Matcher.quoteReplacement(decimalMatcher.group()));
            } else {
                decimalMatcher.appendReplacement(result, Matcher.quoteReplacement(token(value)));
            }
        }
        decimalMatcher.appendTail(result);
        withNumericAmounts = result.toString();
        Matcher contextualMatcher = CONTEXTUAL_NUMERIC_AMOUNT.matcher(withNumericAmounts);
        result = new StringBuffer();
        while (contextualMatcher.find()) {
            BigDecimal value = parseNumeric(contextualMatcher.group(1));
            if (value == null) {
                contextualMatcher.appendReplacement(result, Matcher.quoteReplacement(contextualMatcher.group()));
            } else {
                contextualMatcher.appendReplacement(result, Matcher.quoteReplacement(token(value)));
            }
        }
        contextualMatcher.appendTail(result);
        withNumericAmounts = result.toString();
        Matcher contextualWordMatcher = CONTEXTUAL_WORD_THOUSAND.matcher(withNumericAmounts);
        result = new StringBuffer();
        while (contextualWordMatcher.find()) {
            BigDecimal value = parseWords(contextualWordMatcher.group(1));
            if (value == null) {
                contextualWordMatcher.appendReplacement(
                        result, Matcher.quoteReplacement(contextualWordMatcher.group()));
            } else {
                contextualWordMatcher.appendReplacement(result, Matcher.quoteReplacement(token(value)));
            }
        }
        contextualWordMatcher.appendTail(result);
        withNumericAmounts = result.toString();
        Matcher wordMatcher = WORD_AMOUNT.matcher(withNumericAmounts);
        result = new StringBuffer();
        while (wordMatcher.find()) {
            BigDecimal whole = parseWords(wordMatcher.group(1));
            BigDecimal value = whole;
            if (whole != null && wordMatcher.group(2) != null) {
                BigDecimal cents = parseWords(wordMatcher.group(2));
                if (cents != null && cents.compareTo(BigDecimal.valueOf(99)) <= 0) {
                    value = whole.add(cents.movePointLeft(2));
                }
            }
            if (value == null) {
                wordMatcher.appendReplacement(result, Matcher.quoteReplacement(wordMatcher.group()));
            } else {
                wordMatcher.appendReplacement(result, Matcher.quoteReplacement(token(value)));
            }
        }
        wordMatcher.appendTail(result);
        return AudioTextAccuracy.normalize(result.toString());
    }

    private static BigDecimal canonical(BigDecimal value) {
        return value.stripTrailingZeros();
    }

    private static void addIfAbsent(List<BigDecimal> values, BigDecimal candidate) {
        if (values.stream().noneMatch(value -> value.compareTo(candidate) == 0)) {
            values.add(candidate);
        }
    }

    private static void removeEquivalent(List<BigDecimal> values, BigDecimal candidate) {
        values.removeIf(value -> value.compareTo(candidate) == 0);
    }

    private BigDecimal parseNumeric(String value) {
        String normalized = value.toLowerCase(Locale.ROOT)
                .replace("r$", "")
                .replace("reais", "")
                .trim();
        if (normalized.startsWith("-")) {
            return null;
        }
        if (normalized.contains(",")) {
            normalized = normalized.replace(".", "").replace(',', '.');
        } else if (normalized.indexOf('.') >= 0
                && normalized.substring(normalized.lastIndexOf('.') + 1).length() == 3) {
            normalized = normalized.replace(".", "");
        }
        try {
            return canonical(new BigDecimal(normalized));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private BigDecimal parseWords(String value) {
        int current = 0;
        int total = 0;
        boolean found = false;
        for (String word : value.split("\\s+")) {
            if (word.equals("e")) {
                continue;
            }
            if (word.equals("mil")) {
                total += Math.max(1, current) * 1000;
                current = 0;
                found = true;
                continue;
            }
            if (word.equals("milhao") || word.equals("milhoes")) {
                total += Math.max(1, current) * 1_000_000;
                current = 0;
                found = true;
                continue;
            }
            Integer number = NUMBER_WORD_VALUES.get(word);
            if (number == null) {
                return null;
            }
            current += number;
            found = true;
        }
        return found ? canonical(BigDecimal.valueOf(total + current)) : null;
    }

    private String token(BigDecimal value) {
        BigDecimal scaled = value.setScale(2);
        return "money" + scaled.toPlainString().replace(".", "");
    }
}

final class AudioTextAccuracy {
    private AudioTextAccuracy() {
    }

    static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim();
    }

    static double wer(String expected, String actual) {
        List<String> expectedWords = words(expected);
        return distance(expectedWords, words(actual)) / (double) Math.max(1, expectedWords.size());
    }

    static double cer(String expected, String actual) {
        String normalizedExpected = normalize(expected);
        return distance(chars(normalizedExpected), chars(normalize(actual)))
                / (double) Math.max(1, normalizedExpected.length());
    }

    static double normalizedWer(String expected, String actual, AudioMoneyNormalizer money) {
        return wer(money.normalizeText(expected), money.normalizeText(actual));
    }

    static double normalizedCer(String expected, String actual, AudioMoneyNormalizer money) {
        return cer(money.normalizeText(expected), money.normalizeText(actual));
    }

    static boolean sameAmounts(String expected, String actual, FinancialMessageEvidenceExtractor evidence) {
        AudioMoneyNormalizer money = new AudioMoneyNormalizer(evidence);
        List<BigDecimal> expectedAmounts = money.amounts(expected).stream()
                .sorted(BigDecimal::compareTo)
                .toList();
        List<BigDecimal> actualAmounts = money.amounts(actual).stream()
                .sorted(BigDecimal::compareTo)
                .toList();
        if (expectedAmounts.size() != actualAmounts.size()) {
            return false;
        }
        for (int index = 0; index < expectedAmounts.size(); index++) {
            if (expectedAmounts.get(index).compareTo(actualAmounts.get(index)) != 0) {
                return false;
            }
        }
        return true;
    }

    private static List<String> words(String value) {
        String normalized = normalize(value);
        return normalized.isBlank() ? List.of() : List.of(normalized.split(" "));
    }

    private static List<Character> chars(String value) {
        return value.chars().mapToObj(character -> (char) character).toList();
    }

    private static <T> int distance(List<T> left, List<T> right) {
        int[] row = new int[right.size() + 1];
        for (int j = 0; j <= right.size(); j++) {
            row[j] = j;
        }
        for (int i = 1; i <= left.size(); i++) {
            int previous = row[0];
            row[0] = i;
            for (int j = 1; j <= right.size(); j++) {
                int before = row[j];
                row[j] = Math.min(Math.min(row[j] + 1, row[j - 1] + 1),
                        previous + (left.get(i - 1).equals(right.get(j - 1)) ? 0 : 1));
                previous = before;
            }
        }
        return row[right.size()];
    }
}

final class AudioBenchmarkScorer {
    private static final Set<String> DESCRIPTION_STOP_WORDS = Set.of(
            "a", "ao", "aos", "as", "com", "da", "das", "de", "do", "dos", "e", "em", "na", "nas",
            "no", "nos", "o", "os", "para", "pela", "pelas", "pelo", "pelos", "por", "um", "uma", "uns",
            "umas");
    private final FinancialMessageEvidenceExtractor evidence;
    private final AudioMoneyNormalizer money;
    private final Clock clock;

    AudioBenchmarkScorer(FinancialMessageEvidenceExtractor evidence) {
        this(evidence, Clock.system(ZoneId.of("America/Sao_Paulo")));
    }

    AudioBenchmarkScorer(FinancialMessageEvidenceExtractor evidence, Clock clock) {
        this.evidence = evidence;
        this.money = new AudioMoneyNormalizer(evidence);
        this.clock = clock;
    }

    AudioBenchmarkCaseResult score(
            AudioBenchmarkCase item, String transcription, FinancialTransactionExtractionResult extraction,
            String transcriptionProvider, String transcriptionModel, String extractionProvider, String extractionModel,
            long transcriptionMillis, long extractionMillis, long totalMillis, String error) {
        String actualText = transcription == null ? "" : transcription;
        boolean transcriptionCorrect = AudioTextAccuracy.wer(item.originalText(), actualText) == 0.0;
        double normalizedWer = AudioTextAccuracy.normalizedWer(item.originalText(), actualText, money);
        double normalizedCer = AudioTextAccuracy.normalizedCer(item.originalText(), actualText, money);
        boolean normalizedTranscriptionCorrect = normalizedWer == 0.0;
        boolean amountTranscriptionCorrect = AudioTextAccuracy.sameAmounts(item.originalText(), actualText, evidence);
        AudioBenchmarkExtraction actual = AudioBenchmarkExtraction.from(extraction);
        AudioBenchmarkComparison comparison = compare(item.groundTruth(), actual, actualText);
        AudioBenchmarkResultType resultType = classify(item.groundTruth(), comparison, normalizedTranscriptionCorrect,
                amountTranscriptionCorrect, error);
        return new AudioBenchmarkCaseResult(
                item.id(), item.group(), item.audioFile(), item.originalText(), actualText, item.groundTruth(), actual,
                transcriptionProvider, transcriptionModel, extractionProvider, extractionModel,
                AudioTextAccuracy.wer(item.originalText(), actualText), AudioTextAccuracy.cer(item.originalText(), actualText),
                transcriptionCorrect, amountTranscriptionCorrect, comparison, transcriptionMillis, extractionMillis,
                totalMillis, error, resultType, normalizedWer, normalizedCer, normalizedTranscriptionCorrect);
    }

    AudioBenchmarkCaseResult rescore(AudioBenchmarkCaseResult stored) {
        AudioBenchmarkCase item = new AudioBenchmarkCase(
                stored.id(), stored.group(), stored.originalText(), stored.audioFile(), stored.groundTruth());
        FinancialTransactionExtractionResult extraction = toExtraction(stored.extraction());
        return score(item, stored.transcribedText(), extraction, stored.transcriptionProvider(),
                stored.transcriptionModel(), stored.extractionProvider(), stored.extractionModel(),
                stored.transcriptionMillis(), stored.extractionMillis(), stored.totalMillis(), stored.error());
    }

    private FinancialTransactionExtractionResult toExtraction(AudioBenchmarkExtraction extraction) {
        if (extraction == null) {
            return null;
        }
        return new FinancialTransactionExtractionResult(
                extraction.status(), extraction.financial(), extraction.type(), extraction.amount(),
                extraction.transactionDate(), extraction.dueDate(), extraction.description(), extraction.categoryName(),
                extraction.confidence(), extraction.missingFields());
    }

    private AudioBenchmarkComparison compare(AudioBenchmarkGroundTruth expected, AudioBenchmarkExtraction actual,
            String actualText) {
        if (actual == null) {
            return new AudioBenchmarkComparison(false, false, false, false, false, false, false, false, false, false,
                    "no extraction");
        }
        boolean typeCorrect = Objects.equals(expected.type(), actual.type());
        boolean amountCorrect = same(expected.amount(), actual.amount());
        boolean dateCorrect = sameDate(expected.transactionDate(), actual.transactionDate());
        boolean dueDateCorrect = sameDate(expected.dueDate(), actual.dueDate());
        boolean descriptionCorrect = sameDescription(expected.description(), actual.description());
        boolean categoryCorrect = sameText(expected.categoryName(), actual.categoryName());
        boolean missingCorrect = new LinkedHashSet<>(expected.missingFields()).equals(
                new LinkedHashSet<>(actual.missingFields() == null ? List.of() : actual.missingFields()));
        boolean countCorrect = expected.expectedCount() == 1
                ? actual.status() == FinancialTransactionExtractionStatus.VALID
                : actual.status() == enumStatus(expected.status());
        boolean hallucinated = (!expected.financial() && actual.financial())
                || expected.amount() == null && actual.amount() != null
                || expected.type() == null && actual.type() != null
                || expected.transactionDate() == null && actual.transactionDate() != null
                || expected.dueDate() == null && actual.dueDate() != null
                || expected.categoryName() == null && actual.categoryName() != null;
        boolean extractionCorrect = expected.status().equals(actual.status().name())
                && (expected.expectedCount() == 0
                ? !actual.financial() || actual.status() != FinancialTransactionExtractionStatus.VALID
                : typeCorrect && amountCorrect && dateCorrect && dueDateCorrect && descriptionCorrect
                && categoryCorrect && missingCorrect && !hallucinated);
        String reason = firstFailure(expected, actual, typeCorrect, amountCorrect, dateCorrect, dueDateCorrect,
                descriptionCorrect, categoryCorrect, missingCorrect, countCorrect, hallucinated, actualText);
        return new AudioBenchmarkComparison(typeCorrect, amountCorrect, dateCorrect, dueDateCorrect, descriptionCorrect,
                categoryCorrect, missingCorrect, countCorrect, hallucinated, extractionCorrect, reason);
    }

    private String firstFailure(AudioBenchmarkGroundTruth expected, AudioBenchmarkExtraction actual, boolean typeCorrect,
            boolean amountCorrect, boolean dateCorrect, boolean dueDateCorrect, boolean descriptionCorrect,
            boolean categoryCorrect, boolean missingCorrect, boolean countCorrect, boolean hallucinated, String actualText) {
        if (!countCorrect) {
            return "status/count mismatch";
        }
        if (!amountCorrect && evidence.monetaryOperationCount(actualText) > 0) {
            return "amount mismatch";
        }
        if (!typeCorrect) {
            return "type mismatch";
        }
        if (!amountCorrect) {
            return "amount mismatch";
        }
        if (!dateCorrect) {
            return "transaction date mismatch";
        }
        if (!dueDateCorrect) {
            return "due date mismatch";
        }
        if (!descriptionCorrect) {
            return "description mismatch";
        }
        if (!categoryCorrect) {
            return "category mismatch";
        }
        if (!missingCorrect) {
            return "missing fields mismatch";
        }
        if (hallucinated) {
            return "hallucinated field";
        }
        return expected.status().equals(actual.status().name()) ? "" : "status mismatch";
    }

    private AudioBenchmarkResultType classify(AudioBenchmarkGroundTruth expected, AudioBenchmarkComparison comparison,
            boolean transcriptionCorrect, boolean amountTranscriptionCorrect, String error) {
        if (error != null) {
            return AudioBenchmarkResultType.TECHNICAL_ERROR;
        }
        if (!amountTranscriptionCorrect) {
            return AudioBenchmarkResultType.AMOUNT_TRANSCRIPTION_ERROR;
        }
        if (!transcriptionCorrect) {
            return AudioBenchmarkResultType.TRANSCRIPTION_ERROR;
        }
        if (comparison.hallucinatedData()) {
            return AudioBenchmarkResultType.HALLUCINATED_DATA;
        }
        if (expected.expectedCount() == 0 && comparison.countCorrect()) {
            return AudioBenchmarkResultType.CORRECT;
        }
        if (expected.expectedCount() == 0) {
            return AudioBenchmarkResultType.SHOULD_HAVE_BLOCKED;
        }
        if (!comparison.extractionCorrect()) {
            return switch (comparison.reason()) {
                case "type mismatch" -> AudioBenchmarkResultType.WRONG_TYPE;
                case "amount mismatch" -> AudioBenchmarkResultType.WRONG_AMOUNT;
                case "transaction date mismatch" -> AudioBenchmarkResultType.WRONG_DATE;
                case "due date mismatch" -> AudioBenchmarkResultType.WRONG_DUE_DATE;
                case "description mismatch" -> AudioBenchmarkResultType.WRONG_DESCRIPTION;
                case "category mismatch" -> AudioBenchmarkResultType.WRONG_CATEGORY;
                case "missing fields mismatch" -> AudioBenchmarkResultType.WRONG_MISSING_FIELDS;
                default -> AudioBenchmarkResultType.EXTRACTION_ERROR;
            };
        }
        return AudioBenchmarkResultType.CORRECT;
    }

    private boolean sameDate(String expected, LocalDate actual) {
        if (expected == null) {
            return actual == null;
        }
        LocalDate target = switch (expected) {
            case "TODAY" -> LocalDate.now(clock);
            case "YESTERDAY" -> LocalDate.now(clock).minusDays(1);
            default -> LocalDate.parse(expected);
        };
        return Objects.equals(target, actual);
    }

    boolean sameDescription(String expected, String actual) {
        if (expected == null) {
            return actual == null;
        }
        if (actual == null) {
            return false;
        }
        Set<String> expectedWords = meaningfulWords(expected);
        Set<String> actualWords = meaningfulWords(actual);
        return expectedWords.equals(actualWords);
    }

    Set<String> meaningfulWords(String value) {
        String normalized = AudioTextAccuracy.normalize(value);
        if (normalized.isBlank()) {
            return Set.of();
        }
        return java.util.Arrays.stream(normalized.split(" "))
                .filter(word -> word.length() >= 3)
                .filter(word -> !DESCRIPTION_STOP_WORDS.contains(word))
                .collect(Collectors.toUnmodifiableSet());
    }

    private boolean sameText(String expected, String actual) {
        return expected == null ? actual == null : actual != null
                && AudioTextAccuracy.normalize(expected).equals(AudioTextAccuracy.normalize(actual));
    }

    private boolean same(BigDecimal expected, BigDecimal actual) {
        return expected == null ? actual == null : actual != null && expected.compareTo(actual) == 0;
    }

    private FinancialTransactionExtractionStatus enumStatus(String value) {
        return FinancialTransactionExtractionStatus.valueOf(value);
    }
}

final class AudioBenchmarkReportWriter {
    private final Path output;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final AudioBenchmarkScorer scorer = new AudioBenchmarkScorer(new FinancialMessageEvidenceExtractor());

    AudioBenchmarkReportWriter() {
        this(Path.of("target", "ai-audio-benchmark"));
    }

    AudioBenchmarkReportWriter(Path output) {
        this.output = output;
    }

    void append(AudioBenchmarkCaseResult result) {
        try {
            Files.createDirectories(output);
            Files.writeString(output.resolve("results.jsonl"), objectMapper.writeValueAsString(result) + "\n",
                    StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not append audio benchmark result", exception);
        }
    }

    void appendDiagnostic(AudioBenchmarkDiagnosticResult result) {
        try {
            Files.createDirectories(output);
            Files.writeString(output.resolve("diagnostic.jsonl"), objectMapper.writeValueAsString(result) + "\n",
                    StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not append audio benchmark diagnostic", exception);
        }
    }

    Set<String> completedIds() {
        return readResults().results().stream()
                .flatMap(result -> java.util.stream.Stream.of(
                        result.id(), result.extractionProvider() + ":" + result.id()))
                .collect(Collectors.toSet());
    }

    void write(Collection<AudioBenchmarkCaseResult> currentResults) {
        try {
            Files.createDirectories(output);
            AudioBenchmarkReadResults readResults = readResults();
            List<AudioBenchmarkCaseResult> results = readResults.results();
            if (results.isEmpty()) {
                results = currentResults.stream().toList();
            }
            results = results.stream().sorted(Comparator.comparing(AudioBenchmarkCaseResult::id)).toList();
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("executedAt", java.time.OffsetDateTime.now().toString());
            summary.put("total", results.size());
            summary.put("acceptedResults", results.size());
            summary.put("rejectedResults", readResults.rejectedResults().size());
            summary.put("rejectionDetails", readResults.rejectedResults());
            summary.put("metrics", metrics(results));
            summary.put("byGroup", byGroup(results));
            objectMapper.writeValue(output.resolve("summary.json").toFile(), summary);
            objectMapper.writeValue(output.resolve("results.json").toFile(), results);
            objectMapper.writeValue(
                    output.resolve("rejected-results.json").toFile(), readResults.rejectedResults());
            writeCsv(results);
            writeHtml(results);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not write audio benchmark report", exception);
        }
    }

    private AudioBenchmarkReadResults readResults() {
        Path file = output.resolve("results.jsonl");
        if (!Files.exists(file)) {
            return new AudioBenchmarkReadResults(List.of(), List.of());
        }
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            List<AudioBenchmarkParsedResult> parsedResults = new ArrayList<>();
            List<AudioBenchmarkRejectedResult> rejectedResults = new ArrayList<>();
            Set<String> knownProperties = resultProperties();
            for (int index = 0; index < lines.size(); index++) {
                String line = lines.get(index);
                if (line.isBlank()) {
                    continue;
                }
                readLine(line, index + 1, knownProperties, parsedResults, rejectedResults);
            }
            return deduplicate(parsedResults, rejectedResults);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not load audio benchmark results", exception);
        }
    }

    private void readLine(
            String line,
            int lineNumber,
            Set<String> knownProperties,
            List<AudioBenchmarkParsedResult> parsedResults,
            List<AudioBenchmarkRejectedResult> rejectedResults) {
        JsonNode node;
        try {
            node = objectMapper.readTree(line);
        } catch (IOException exception) {
            rejectedResults.add(new AudioBenchmarkRejectedResult(
                    lineNumber, null, "invalid JSON: " + exception.getMessage(), List.of()));
            return;
        }
        if (node == null || !node.isObject()) {
            rejectedResults.add(new AudioBenchmarkRejectedResult(
                    lineNumber, null, "result must be a JSON object", List.of()));
            return;
        }
        String id = textValue(node, "id");
        List<String> unknownProperties = unknownProperties(node, knownProperties);
        if (!unknownProperties.isEmpty()) {
            rejectedResults.add(new AudioBenchmarkRejectedResult(
                    lineNumber, id, "unknown result fields", unknownProperties));
            return;
        }
        try {
            AudioBenchmarkCaseResult result = objectMapper.treeToValue(node, AudioBenchmarkCaseResult.class);
            String validationError = validate(result);
            if (validationError != null) {
                rejectedResults.add(new AudioBenchmarkRejectedResult(
                        lineNumber, id, validationError, List.of()));
                return;
            }
            parsedResults.add(new AudioBenchmarkParsedResult(lineNumber, scorer.rescore(result)));
        } catch (IOException exception) {
            rejectedResults.add(new AudioBenchmarkRejectedResult(
                    lineNumber, id, "invalid result: " + exception.getMessage(), List.of()));
        }
    }

    private Set<String> resultProperties() {
        return objectMapper.getDeserializationConfig()
                .introspect(objectMapper.constructType(AudioBenchmarkCaseResult.class))
                .findProperties().stream()
                .map(BeanPropertyDefinition::getName)
                .collect(Collectors.toUnmodifiableSet());
    }

    private List<String> unknownProperties(JsonNode node, Set<String> knownProperties) {
        List<String> unknown = new ArrayList<>();
        node.fieldNames().forEachRemaining(field -> {
            if (!knownProperties.contains(field)) {
                unknown.add(field);
            }
        });
        return unknown.stream().sorted().toList();
    }

    private String textValue(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isTextual() ? value.textValue() : null;
    }

    private String validate(AudioBenchmarkCaseResult result) {
        if (result.id() == null || result.id().isBlank()) {
            return "missing id";
        }
        if (result.group() == null) {
            return "missing group";
        }
        if (result.audioFile() == null || result.audioFile().isBlank()) {
            return "missing audioFile";
        }
        if (result.originalText() == null) {
            return "missing originalText";
        }
        if (result.transcriptionProvider() == null || result.transcriptionProvider().isBlank()) {
            return "missing transcriptionProvider";
        }
        if (result.extractionProvider() == null || result.extractionProvider().isBlank()) {
            return "missing extractionProvider";
        }
        if (result.comparison() == null) {
            return "missing comparison";
        }
        if (result.resultType() == null) {
            return "missing resultType";
        }
        return null;
    }

    private AudioBenchmarkReadResults deduplicate(
            List<AudioBenchmarkParsedResult> parsedResults,
            List<AudioBenchmarkRejectedResult> rejectedResults) {
        Map<String, AudioBenchmarkParsedResult> unique = new LinkedHashMap<>();
        List<AudioBenchmarkRejectedResult> allRejections = new ArrayList<>(rejectedResults);
        for (AudioBenchmarkParsedResult parsed : parsedResults) {
            AudioBenchmarkCaseResult result = parsed.result();
            String key = result.extractionProvider() + ":" + result.id();
            AudioBenchmarkParsedResult previous = unique.put(key, parsed);
            if (previous != null) {
                allRejections.add(new AudioBenchmarkRejectedResult(
                        parsed.lineNumber(), result.id(), "duplicate result; latest result retained", List.of(key)));
            }
        }
        return new AudioBenchmarkReadResults(
                unique.values().stream().map(AudioBenchmarkParsedResult::result).toList(), allRejections);
    }

    private Map<String, Object> metrics(List<AudioBenchmarkCaseResult> results) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("averageWer", averageDouble(results, AudioBenchmarkCaseResult::wer));
        metrics.put("averageCer", averageDouble(results, AudioBenchmarkCaseResult::cer));
        metrics.put("averageNormalizedWer", averageDouble(results, AudioBenchmarkCaseResult::normalizedWer));
        metrics.put("averageNormalizedCer", averageDouble(results, AudioBenchmarkCaseResult::normalizedCer));
        metrics.put("transcriptionAccuracy", percentage(results, AudioBenchmarkCaseResult::transcriptionCorrect));
        metrics.put("normalizedTranscriptionAccuracy",
                percentage(results, AudioBenchmarkCaseResult::normalizedTranscriptionCorrect));
        metrics.put("amountTranscriptionAccuracy", percentage(results, AudioBenchmarkCaseResult::amountTranscriptionCorrect));
        metrics.put("extractionAccuracy", percentage(results, result -> result.comparison().extractionCorrect()));
        metrics.put("endToEndAccuracy", percentage(results, result -> result.resultType() == AudioBenchmarkResultType.CORRECT));
        metrics.put("hallucinationRate", percentage(results, result -> result.comparison().hallucinatedData()));
        metrics.put("technicalFailureRate", percentage(results, result -> result.resultType() == AudioBenchmarkResultType.TECHNICAL_ERROR));
        metrics.put("averageTotalMillis", average(results, AudioBenchmarkCaseResult::totalMillis));
        metrics.put("medianTotalMillis", percentile(results, AudioBenchmarkCaseResult::totalMillis, 0.50));
        metrics.put("p95TotalMillis", percentile(results, AudioBenchmarkCaseResult::totalMillis, 0.95));
        metrics.put("p99TotalMillis", percentile(results, AudioBenchmarkCaseResult::totalMillis, 0.99));
        return metrics;
    }

    private Map<String, Object> byGroup(List<AudioBenchmarkCaseResult> results) {
        return java.util.Arrays.stream(AudioBenchmarkGroup.values()).collect(Collectors.toMap(
                Enum::name,
                group -> metrics(results.stream().filter(result -> result.group() == group).toList()),
                (left, right) -> left,
                LinkedHashMap::new));
    }

    private double percentage(List<AudioBenchmarkCaseResult> results, Predicate<AudioBenchmarkCaseResult> predicate) {
        return results.isEmpty() ? 0 : 100.0 * results.stream().filter(predicate).count() / results.size();
    }

    private double average(List<AudioBenchmarkCaseResult> results,
            java.util.function.ToLongFunction<AudioBenchmarkCaseResult> value) {
        return results.stream().mapToLong(value).average().orElse(0);
    }

    private double averageDouble(List<AudioBenchmarkCaseResult> results,
            java.util.function.ToDoubleFunction<AudioBenchmarkCaseResult> value) {
        return results.stream().mapToDouble(value).average().orElse(0);
    }

    private long percentile(List<AudioBenchmarkCaseResult> results,
            java.util.function.ToLongFunction<AudioBenchmarkCaseResult> value, double percentile) {
        List<Long> values = results.stream().map(value::applyAsLong).sorted().toList();
        return values.isEmpty() ? 0 : values.get(Math.min(values.size() - 1,
                (int) Math.ceil(values.size() * percentile) - 1));
    }

    private void writeCsv(List<AudioBenchmarkCaseResult> results) throws IOException {
        StringBuilder csv = new StringBuilder(
                "id,group,audioFile,wer,cer,normalizedWer,normalizedCer,transcriptionCorrect,"
                        + "normalizedTranscriptionCorrect,amountTranscriptionCorrect,result,"
                        + "transcriptionMillis,extractionMillis,totalMillis,reason\n");
        for (AudioBenchmarkCaseResult result : results) {
            csv.append(csv(result.id())).append(',').append(csv(result.group())).append(',')
                    .append(csv(result.audioFile())).append(',').append(result.wer()).append(',')
                    .append(result.cer()).append(',').append(result.normalizedWer()).append(',')
                    .append(result.normalizedCer()).append(',').append(result.transcriptionCorrect()).append(',')
                    .append(result.normalizedTranscriptionCorrect()).append(',')
                    .append(result.amountTranscriptionCorrect()).append(',').append(csv(result.resultType())).append(',')
                    .append(result.transcriptionMillis()).append(',').append(result.extractionMillis()).append(',')
                    .append(result.totalMillis()).append(',').append(csv(result.comparison().reason())).append('\n');
        }
        Files.writeString(output.resolve("results.csv"), csv, StandardCharsets.UTF_8);
    }

    private void writeHtml(List<AudioBenchmarkCaseResult> results) throws IOException {
        String rows = results.stream().map(result -> "<tr><td>" + escape(result.id())
                + "</td><td><audio controls src=\"../../audios/audio/" + escape(result.audioFile())
                + "\"></audio></td><td>" + escape(result.originalText()) + "</td><td>"
                + escape(result.transcribedText()) + "</td><td>" + result.resultType() + "</td><td>"
                + escape(result.comparison().reason()) + "</td></tr>").collect(Collectors.joining());
        String html = "<!doctype html><meta charset=\"utf-8\"><title>Benchmark de áudio</title>"
                + "<h1>Benchmark de áudio financeiro</h1><table border=\"1\"><tr><th>ID</th>"
                + "<th>Áudio</th><th>Esperado</th><th>Transcrição</th><th>Resultado</th><th>Motivo</th></tr>"
                + rows + "</table>";
        Files.writeString(output.resolve("report.html"), html, StandardCharsets.UTF_8);
    }

    private String csv(Object value) {
        return value == null ? "" : "\"" + value.toString().replace("\"", "\"\"") + "\"";
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }
}
