package br.com.gestaodireta.messaging.service;

import org.springframework.stereotype.Component;

@Component
public class TelegramFinancialMessageEligibilityValidator {
    private final FinancialMessageEvidenceExtractor evidence;

    public TelegramFinancialMessageEligibilityValidator(
            FinancialMessageEvidenceExtractor evidence) {
        this.evidence = evidence;
    }

    public boolean isEligible(String text) {
        String normalized = evidence.normalize(text);
        if (normalized.isBlank()
                || normalized.startsWith("/")
                || normalized.replaceAll("[^a-z0-9]", "").length() < 3) {
            return false;
        }
        if (evidence.monetaryAmounts(text).isEmpty()) {
            return false;
        }
        return evidence.words(text).stream()
                .anyMatch(word -> word.length() >= 2 && word.matches(".*[a-z].*"));
    }
}
