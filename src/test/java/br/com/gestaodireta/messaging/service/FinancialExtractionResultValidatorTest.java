package br.com.gestaodireta.messaging.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.gestaodireta.ai.config.FinancialExtractionProperties;
import br.com.gestaodireta.ai.service.dto.FinancialTransactionExtractionResult;
import br.com.gestaodireta.financial.enumeration.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class FinancialExtractionResultValidatorTest {
    private final FinancialTransactionExtractionResultValidator validator =
            new FinancialTransactionExtractionResultValidator(
                    properties(), new FinancialMessageEvidenceExtractor());

    @Test
    void shouldAcceptSaleWithOptionalMissingCategory() {
        String text = "foi vendido 20kg de milho e recebi 100 reais hoje";
        FinancialTransactionExtractionResult result =
                result(new BigDecimal("100.00"), new BigDecimal("0.95"), List.of("category"));

        assertThat(validator.validate(text, result))
                .isEqualTo(
                        new FinancialTransactionExtractionResultValidator.ValidationResult(
                                true,
                                FinancialTransactionExtractionResultValidator.RejectionReason
                                        .NONE));
    }

    void shouldAcceptFinancialResultWhenAiMarksOnlyFinancialContextAsMissing() {
        FinancialTransactionExtractionResult result =
                new FinancialTransactionExtractionResult(
                        true,
                        TransactionType.INCOME,
                        new BigDecimal("1000.00"),
                        LocalDate.of(2026, 8, 9),
                        "Venda de milho",
                        null,
                        new BigDecimal("0.95"),
                        List.of("financialContext"));

        assertThat(validator.isValid("recebi 1000 reais pela venda de milho hoje", result))
                .isTrue();
    }

    void shouldRejectOnlyRequiredMissingFieldsAndLowConfidence() {
        String text = "vendi 20kg de milho por 100 reais hoje";

        assertThat(
                        validator
                                .validate(
                                        text,
                                        result(
                                                new BigDecimal("100.00"),
                                                new BigDecimal("0.95"),
                                                List.of("amount")))
                                .reason())
                .isEqualTo(
                        FinancialTransactionExtractionResultValidator.RejectionReason
                                .MISSING_REQUIRED_FIELD);
        assertThat(
                        validator
                                .validate(
                                        text,
                                        result(
                                                new BigDecimal("100.00"),
                                                new BigDecimal("0.52"),
                                                List.of()))
                                .reason())
                .isEqualTo(
                        FinancialTransactionExtractionResultValidator.RejectionReason
                                .LOW_CONFIDENCE);
        assertThat(
                        validator
                                .validate(
                                        text,
                                        result(
                                                new BigDecimal("20.00"),
                                                new BigDecimal("0.95"),
                                                List.of()))
                                .reason())
                .isEqualTo(
                        FinancialTransactionExtractionResultValidator.RejectionReason
                                .AMOUNT_NOT_SUPPORTED_BY_SOURCE);
    }

    @Test
    void shouldBlockIndependentMonetaryOperationsWithoutTrustingAiResult() {
        FinancialTransactionExtractionResult result =
                result(new BigDecimal("700.00"), new BigDecimal("0.95"), List.of());

        assertThat(
                        validator
                                .validate(
                                        "Comprei R$ 700 de sementes e R$ 500 de agrotóxico.",
                                        result)
                                .reason())
                .isEqualTo(
                        FinancialTransactionExtractionResultValidator.RejectionReason
                                .MULTIPLE_TRANSACTIONS);
    }

    @Test
    void shouldRejectTwoDistinctBrazilianCurrencyAmounts() {
        FinancialTransactionExtractionResult result =
                new FinancialTransactionExtractionResult(
                        true,
                        TransactionType.EXPENSE,
                        new BigDecimal("700.00"),
                        null,
                        "Sementes",
                        null,
                        new BigDecimal("0.95"),
                        List.of());

        assertThat(
                        validator
                                .validate(
                                        "Comprei R$700,00 de semente e R$500,00 de adubo.", result)
                                .reason())
                .isEqualTo(
                        FinancialTransactionExtractionResultValidator.RejectionReason
                                .MULTIPLE_TRANSACTIONS);
    }

    @Test
    void shouldAcceptPhysicalQuantityWithOneMonetaryValue() {
        FinancialTransactionExtractionResult result =
                new FinancialTransactionExtractionResult(
                        true,
                        TransactionType.EXPENSE,
                        new BigDecimal("1200.00"),
                        null,
                        "Sementes",
                        null,
                        new BigDecimal("0.95"),
                        List.of("transactionDate"));

        assertThat(validator.validate("Comprei 10 sacos de sementes por R$ 1.200.", result).valid())
                .isTrue();
    }

    @Test
    void shouldAcceptReceiptAsIncomeSignal() {
        FinancialTransactionExtractionResult result =
                new FinancialTransactionExtractionResult(
                        true,
                        TransactionType.INCOME,
                        new BigDecimal("1250.00"),
                        null,
                        "Venda de café",
                        null,
                        new BigDecimal("0.95"),
                        List.of("transactionDate"));

        assertThat(
                        validator
                                .validate(
                                        "Recebimento por transferência de R$ 1250 pelo café.",
                                        result)
                                .valid())
                .isTrue();
    }

    @Test
    void shouldAcceptSpentMoneyAsExpenseSignal() {
        FinancialTransactionExtractionResult result =
                new FinancialTransactionExtractionResult(
                        true,
                        TransactionType.EXPENSE,
                        new BigDecimal("850.00"),
                        null,
                        "Conserto do trator",
                        null,
                        new BigDecimal("0.95"),
                        List.of("transactionDate"));

        assertThat(
                        validator
                                .validate("Dinheiro gasto: R$ 850 com conserto do trator.", result)
                                .valid())
                .isTrue();
    }

    private FinancialTransactionExtractionResult result(
            BigDecimal amount, BigDecimal confidence, List<String> missingFields) {
        return new FinancialTransactionExtractionResult(
                true,
                TransactionType.INCOME,
                amount,
                LocalDate.of(2026, 7, 27),
                "Venda de 20 kg de milho",
                "Venda de produção",
                confidence,
                missingFields);
    }

    private FinancialExtractionProperties properties() {
        FinancialExtractionProperties properties = new FinancialExtractionProperties();
        properties.setMinimumConfidence(0.60);
        return properties;
    }
}
