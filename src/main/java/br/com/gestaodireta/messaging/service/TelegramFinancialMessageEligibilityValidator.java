package br.com.gestaodireta.messaging.service;

import br.com.gestaodireta.ai.config.FinancialExtractionProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TelegramFinancialMessageEligibilityValidator {
    private final FinancialMessageEvidenceExtractor evidence;
    private final FinancialExtractionProperties properties;

    public TelegramFinancialMessageEligibilityValidator(
            FinancialMessageEvidenceExtractor evidence) {
        this(evidence, new FinancialExtractionProperties());
    }

    @Autowired
    public TelegramFinancialMessageEligibilityValidator(
            FinancialMessageEvidenceExtractor evidence, FinancialExtractionProperties properties) {
        this.evidence = evidence;
        this.properties = properties;
    }

    public boolean exceedsMaximumLength(String text) {
        return text != null && text.length() > properties.getMessageMaxLength();
    }

    public boolean isEligible(String text) {
        String normalized = evidence.normalize(text);
        if (normalized.isBlank()
                || normalized.startsWith("/")
                || normalized.replaceAll("[^a-z0-9]", "").length() < 3) {
            return false;
        }
        boolean hasWords =
                evidence.words(text).stream()
                        .anyMatch(word -> word.length() >= 2 && word.matches(".*[a-z].*"));
        return hasWords
                && (!evidence.monetaryAmounts(text).isEmpty()
                        || evidence.words(text).stream()
                                .anyMatch(
                                        word ->
                                                java.util.Set.of(
                                                                "paguei", "recebi", "gastei",
                                                                "comprei", "vendi")
                                                        .contains(word)));
    }
}
