package br.com.gestaodireta.messaging.service;

import br.com.gestaodireta.ai.config.FinancialExtractionProperties;
import br.com.gestaodireta.ai.service.dto.FinancialTransactionExtractionResult;
import br.com.gestaodireta.financial.enumeration.TransactionType;
import java.math.BigDecimal;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class FinancialTransactionExtractionResultValidator {
    public enum RejectionReason {
        NONE,
        NOT_FINANCIAL,
        MISSING_REQUIRED_FIELD,
        INVALID_AMOUNT,
        INVALID_CONFIDENCE,
        LOW_CONFIDENCE,
        AMOUNT_NOT_SUPPORTED_BY_SOURCE,
        TYPE_NOT_SUPPORTED_BY_SOURCE,
        DESCRIPTION_NOT_SUPPORTED_BY_SOURCE
    }

    public record ValidationResult(boolean valid, RejectionReason reason) {}

    private static final Set<String> EXPENSE_SIGNALS =
            Set.of(
                    "paguei",
                    "pagar",
                    "gastei",
                    "gastar",
                    "comprei",
                    "comprar",
                    "comprado",
                    "despesa",
                    "custo",
                    "saida",
                    "saiu",
                    "custou",
                    "manutencao",
                    "combustivel",
                    "adubo");
    private static final Set<String> INCOME_SIGNALS =
            Set.of(
                    "recebi",
                    "receber",
                    "vendi",
                    "vendido",
                    "venda",
                    "entrada",
                    "receita",
                    "entrou",
                    "faturamento",
                    "pagamento",
                    "recebido");
    private static final Set<String> GENERIC_DESCRIPTION_WORDS =
            Set.of(
                    "movimentacao",
                    "financeira",
                    "receita",
                    "despesa",
                    "entrada",
                    "saida",
                    "pagamento",
                    "registro");

    private final FinancialExtractionProperties properties;
    private final FinancialMessageEvidenceExtractor evidence;

    public FinancialTransactionExtractionResultValidator(
            FinancialExtractionProperties properties, FinancialMessageEvidenceExtractor evidence) {
        this.properties = properties;
        this.evidence = evidence;
    }

    public boolean isValid(String originalText, FinancialTransactionExtractionResult result) {
        return validate(originalText, result).valid();
    }

    public ValidationResult validate(
            String originalText, FinancialTransactionExtractionResult result) {
        if (result == null || !result.isFinancialTransaction()) {
            return rejected(RejectionReason.NOT_FINANCIAL);
        }
        if (result.type() == null
                || result.transactionDate() == null
                || invalidDescription(result.description())
                || hasMissingRequiredField(result)) {
            return rejected(RejectionReason.MISSING_REQUIRED_FIELD);
        }
        if (result.amount() == null || result.amount().compareTo(BigDecimal.ZERO) <= 0) {
            return rejected(RejectionReason.INVALID_AMOUNT);
        }
        if (result.confidence() == null
                || result.confidence().compareTo(BigDecimal.ZERO) < 0
                || result.confidence().compareTo(BigDecimal.ONE) > 0) {
            return rejected(RejectionReason.INVALID_CONFIDENCE);
        }
        if (result.confidence().compareTo(BigDecimal.valueOf(properties.getMinimumConfidence()))
                < 0) {
            return rejected(RejectionReason.LOW_CONFIDENCE);
        }
        if (evidence.monetaryAmounts(originalText).stream()
                .noneMatch(amount -> amount.compareTo(result.amount()) == 0)) {
            return rejected(RejectionReason.AMOUNT_NOT_SUPPORTED_BY_SOURCE);
        }
        if (!hasCompatibleTypeSignal(evidence.words(originalText), result.type())) {
            return rejected(RejectionReason.TYPE_NOT_SUPPORTED_BY_SOURCE);
        }
        if (!hasDescriptionEvidence(originalText, result.description())) {
            return rejected(RejectionReason.DESCRIPTION_NOT_SUPPORTED_BY_SOURCE);
        }
        return new ValidationResult(true, RejectionReason.NONE);
    }

    private boolean hasMissingRequiredField(FinancialTransactionExtractionResult result) {
        return result.type() == null || result.amount() == null;
    }

    private ValidationResult rejected(RejectionReason reason) {
        return new ValidationResult(false, reason);
    }

    private boolean invalidDescription(String description) {
        return description == null
                || description.trim().length() < 3
                || "null".equalsIgnoreCase(description.trim());
    }

    private boolean hasCompatibleTypeSignal(Set<String> words, TransactionType type) {
        Set<String> signals = type == TransactionType.EXPENSE ? EXPENSE_SIGNALS : INCOME_SIGNALS;
        return words.stream().anyMatch(signals::contains);
    }

    private boolean hasDescriptionEvidence(String originalText, String description) {
        Set<String> originalWords = evidence.words(originalText);
        return evidence.words(description).stream()
                .filter(word -> word.length() >= 3)
                .filter(word -> !GENERIC_DESCRIPTION_WORDS.contains(word))
                .anyMatch(originalWords::contains);
    }
}
