package br.com.gestaodireta.messaging.service;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class FinancialMessageEvidenceExtractor {
    private static final String NUMBER =
            "(?<!\\d)(\\d{1,3}(?:\\.\\d{3})+(?:,\\d{1,2})?|\\d+(?:,\\d{1,2})?)(?!\\d|[.,]\\d)";
    private static final Pattern CURRENCY_PREFIX =
            Pattern.compile("r\\$\\s*" + NUMBER, Pattern.CASE_INSENSITIVE);
    private static final Pattern CURRENCY_SUFFIX =
            Pattern.compile(NUMBER + "\\s*reais?", Pattern.CASE_INSENSITIVE);
    private static final Pattern DECIMAL_AMOUNT =
            Pattern.compile(
                    "(?<!\\d)(\\d{1,3}(?:\\.\\d{3})+,\\d{1,2}|\\d+,\\d{1,2})(?!\\d|[.,]\\d)");
    private static final Pattern CONTEXTUAL_AMOUNT =
            Pattern.compile(
                    "(?:por|paguei|pagou|recebi|recebeu|entrou|entrada(?:\\s+de)?|gastei|gastou|custou|foi|valor\\s+de)\\s*"
                            + NUMBER,
                    Pattern.CASE_INSENSITIVE);
    private static final Pattern CONTEXTUAL_THOUSAND =
            Pattern.compile(
                    "(?:por|paguei|pagou|recebi|recebeu|entrou|entrada(?:\\s+de)?|gastei|gastou|custou|foi|valor\\s+de)\\s*(\\d+(?:[,.]\\d+)?)?\\s*mil",
                    Pattern.CASE_INSENSITIVE);
    private static final Pattern WORD_AMOUNT =
            Pattern.compile(
                    "(?:paguei|recebi|gastei|comprei|vendi|por|de)\\s+([a-z ]+?)\\s+reais?",
                    Pattern.CASE_INSENSITIVE);
    private static final Pattern MONETARY_OPERATION =
            Pattern.compile(
                    "r\\$\\s*"
                            + NUMBER
                            + "|"
                            + NUMBER
                            + "\\s*reais?|"
                            + "(?:paguei|pagou|recebi|recebeu|entrou|gastei|gastou|comprei|vendi|por|custou|valor\\s+de)\\s*"
                            + NUMBER
                            + "|(?:paguei|recebi|gastei|comprei|vendi|por)\\s*(?:\\d+(?:[,.]\\d+)?\\s*)?mil",
                    Pattern.CASE_INSENSITIVE);
    private static final Pattern SECOND_COMPONENT_AMOUNT =
            Pattern.compile(
                    "(?:e|depois)\\s+" + NUMBER + "\\s+(?:de|do|da)\\b", Pattern.CASE_INSENSITIVE);

    public List<BigDecimal> monetaryAmounts(String text) {
        if (text == null) {
            return List.of();
        }
        Set<BigDecimal> values = new LinkedHashSet<>();
        String normalized = normalize(text);
        addMatches(values, CURRENCY_PREFIX, normalized);
        addMatches(values, CURRENCY_SUFFIX, normalized);
        addMatches(values, CONTEXTUAL_AMOUNT, normalized);
        addMatches(values, DECIMAL_AMOUNT, normalized);
        addMatches(values, SECOND_COMPONENT_AMOUNT, normalized);
        Matcher thousandMatcher = CONTEXTUAL_THOUSAND.matcher(normalized);
        while (thousandMatcher.find()) {
            String multiplier = thousandMatcher.group(1);
            BigDecimal value = multiplier == null ? BigDecimal.ONE : parseAmount(multiplier);
            values.add(value.multiply(BigDecimal.valueOf(1000)).setScale(2));
        }
        Matcher wordAmountMatcher = WORD_AMOUNT.matcher(normalized);
        while (wordAmountMatcher.find()) {
            BigDecimal value = parseBrazilianWords(wordAmountMatcher.group(1));
            if (value != null) {
                values.add(value.setScale(2));
            }
        }
        return new ArrayList<>(values);
    }

    public int monetaryOperationCount(String text) {
        if (text == null) {
            return 0;
        }
        String normalized = normalize(text);
        int wordAmounts = countWordAmounts(normalized);
        return Math.max(monetaryAmounts(text).size(), wordAmounts);
    }

    public List<BigDecimal> amounts(String text) {
        return monetaryAmounts(text);
    }

    public Set<String> words(String text) {
        Set<String> words = new LinkedHashSet<>();
        for (String token : normalize(text).split("[^a-z0-9]+")) {
            if (!token.isBlank()) {
                words.add(token);
            }
        }
        return words;
    }

    public String normalize(String text) {
        if (text == null) {
            return "";
        }
        return Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .trim();
    }

    private BigDecimal parseAmount(String value) {
        String normalized = value.replace(".", "").replace(',', '.');
        return new BigDecimal(normalized).setScale(2);
    }

    private void addMatches(Set<BigDecimal> values, Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            values.add(parseAmount(matcher.group(1)));
        }
    }

    private int countMatches(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private int countWordAmounts(String text) {
        Matcher matcher = WORD_AMOUNT.matcher(text);
        int count = 0;
        while (matcher.find()) {
            if (parseBrazilianWords(matcher.group(1)) != null) {
                count++;
            }
        }
        return count;
    }

    private BigDecimal parseBrazilianWords(String text) {
        java.util.Map<String, Integer> units =
                java.util.Map.ofEntries(
                        java.util.Map.entry("um", 1), java.util.Map.entry("uma", 1),
                        java.util.Map.entry("dois", 2), java.util.Map.entry("duas", 2),
                        java.util.Map.entry("tres", 3), java.util.Map.entry("quatro", 4),
                        java.util.Map.entry("cinco", 5), java.util.Map.entry("seis", 6),
                        java.util.Map.entry("sete", 7), java.util.Map.entry("oito", 8),
                        java.util.Map.entry("nove", 9), java.util.Map.entry("dez", 10),
                        java.util.Map.entry("cem", 100), java.util.Map.entry("cento", 100),
                        java.util.Map.entry("duzentos", 200), java.util.Map.entry("trezentos", 300),
                        java.util.Map.entry("quatrocentos", 400),
                                java.util.Map.entry("quinhentos", 500),
                        java.util.Map.entry("seiscentos", 600),
                                java.util.Map.entry("setecentos", 700),
                        java.util.Map.entry("oitocentos", 800),
                                java.util.Map.entry("novecentos", 900));
        int current = 0;
        int total = 0;
        boolean found = false;
        for (String word : text.trim().split("\\s+")) {
            if ("e".equals(word)) {
                continue;
            }
            if ("mil".equals(word)) {
                total += Math.max(1, current) * 1000;
                current = 0;
                found = true;
                continue;
            }
            Integer value = units.get(word);
            if (value == null) {
                return null;
            }
            current += value;
            found = true;
        }
        return found ? BigDecimal.valueOf(total + current) : null;
    }
}
