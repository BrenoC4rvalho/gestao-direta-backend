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
        Matcher thousandMatcher = CONTEXTUAL_THOUSAND.matcher(normalized);
        while (thousandMatcher.find()) {
            String multiplier = thousandMatcher.group(1);
            BigDecimal value = multiplier == null ? BigDecimal.ONE : parseAmount(multiplier);
            values.add(value.multiply(BigDecimal.valueOf(1000)).setScale(2));
        }
        return new ArrayList<>(values);
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
}
