package br.com.gestaodireta.messaging.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.gestaodireta.ai.config.FinancialExtractionProperties;
import br.com.gestaodireta.ai.service.dto.FinancialTransactionExtractionResult;
import br.com.gestaodireta.financial.enumeration.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class FinancialExtractionValidationTest {
    private final FinancialMessageEvidenceExtractor evidence =
            new FinancialMessageEvidenceExtractor();
    private final TelegramFinancialMessageEligibilityValidator eligibility =
            new TelegramFinancialMessageEligibilityValidator(evidence);
    private final FinancialTransactionExtractionResultValidator resultValidator =
            new FinancialTransactionExtractionResultValidator(properties(), evidence);

    @Test
    void shouldRejectMessagesWithoutCompleteFinancialContextBeforeAi() {
        for (String text :
                List.of(
                        "90", "1000", "R$ 250", "a", "x", ".", "oi", "teste", "20kg", "milho",
                        "hoje")) {
            assertThat(eligibility.isEligible(text)).as(text).isFalse();
        }
    }

    @Test
    void shouldAcceptMessagesWithFinancialIntentAmountAndDescriptionContext() {
        for (String text :
                List.of(
                        "Gastei 90 reais com combustível",
                        "Recebi 1000 pela venda de milho",
                        "Paguei R$ 250 de adubo",
                        "Entrada de 3500 da venda de soja",
                        "vendi 20kg de milho por 100 reais hoje",
                        "comprei 10 sacos de adubo por 500",
                        "paguei 300 no diesel",
                        "entrou 1500 da venda do gado",
                        "foi 400 de manutenção no trator")) {
            assertThat(eligibility.isEligible(text)).as(text).isTrue();
        }
    }

    @Test
    void shouldRejectInvalidOrInventedAiResults() {
        String text = "Gastei R$ 90 com combustível";

        assertThat(
                        resultValidator.isValid(
                                text,
                                result(
                                        null,
                                        new BigDecimal("90.00"),
                                        "Combustível",
                                        new BigDecimal("0.90"),
                                        List.of())))
                .isFalse();
        assertThat(
                        resultValidator.isValid(
                                text,
                                result(
                                        TransactionType.EXPENSE,
                                        new BigDecimal("90.00"),
                                        null,
                                        new BigDecimal("0.90"),
                                        List.of())))
                .isFalse();
        assertThat(
                        resultValidator.isValid(
                                text,
                                result(
                                        TransactionType.EXPENSE,
                                        new BigDecimal("90.00"),
                                        null,
                                        new BigDecimal("0.90"),
                                        List.of())))
                .isFalse();
        assertThat(
                        resultValidator.isValid(
                                text,
                                result(
                                        TransactionType.EXPENSE,
                                        new BigDecimal("1000.00"),
                                        "Combustível",
                                        new BigDecimal("0.90"),
                                        List.of())))
                .isFalse();
        assertThat(
                        resultValidator.isValid(
                                text,
                                result(
                                        TransactionType.EXPENSE,
                                        BigDecimal.ZERO,
                                        "Combustível",
                                        new BigDecimal("0.90"),
                                        List.of())))
                .isFalse();
        assertThat(
                        resultValidator.isValid(
                                text,
                                result(
                                        TransactionType.EXPENSE,
                                        new BigDecimal("-90.00"),
                                        "Combustível",
                                        new BigDecimal("0.90"),
                                        List.of())))
                .isFalse();
        assertThat(
                        resultValidator.isValid(
                                text,
                                result(
                                        TransactionType.EXPENSE,
                                        new BigDecimal("90.00"),
                                        "null",
                                        new BigDecimal("0.90"),
                                        List.of())))
                .isFalse();
        assertThat(
                        resultValidator.isValid(
                                text,
                                result(
                                        TransactionType.EXPENSE,
                                        new BigDecimal("90.00"),
                                        "Descrição inventada",
                                        new BigDecimal("0.90"),
                                        List.of())))
                .isFalse();
        assertThat(
                        resultValidator.isValid(
                                text,
                                result(
                                        TransactionType.EXPENSE,
                                        new BigDecimal("90.00"),
                                        "Combustível",
                                        new BigDecimal("0.50"),
                                        List.of())))
                .isFalse();
        assertThat(
                        resultValidator.isValid(
                                text,
                                result(
                                        TransactionType.EXPENSE,
                                        new BigDecimal("90.00"),
                                        "Combustível",
                                        new BigDecimal("0.90"),
                                        List.of("category"))))
                .isFalse();
    }

    @Test
    void shouldAcceptConsistentAiResult() {
        assertThat(
                        resultValidator.isValid(
                                "Gastei R$ 90 com combustível",
                                result(
                                        TransactionType.EXPENSE,
                                        new BigDecimal("90.00"),
                                        "Combustível",
                                        new BigDecimal("0.90"),
                                        List.of())))
                .isTrue();
    }

    @Test
    void shouldUseMonetaryAmountInsteadOfPhysicalQuantity() {
        String text = "vendi 20kg de milho por 100 reais hoje";

        assertThat(evidence.monetaryAmounts(text)).containsExactly(new BigDecimal("100.00"));
        assertThat(
                        resultValidator.isValid(
                                text,
                                result(
                                        TransactionType.INCOME,
                                        new BigDecimal("100.00"),
                                        "Venda de 20 kg de milho",
                                        new BigDecimal("0.95"),
                                        List.of())))
                .isTrue();
        assertThat(
                        resultValidator.isValid(
                                text,
                                result(
                                        TransactionType.INCOME,
                                        new BigDecimal("20.00"),
                                        "Venda de 20 kg de milho",
                                        new BigDecimal("0.95"),
                                        List.of())))
                .isFalse();
    }

    private FinancialTransactionExtractionResult result(
            TransactionType type,
            BigDecimal amount,
            String description,
            BigDecimal confidence,
            List<String> missingFields) {
        return new FinancialTransactionExtractionResult(
                true,
                type,
                amount,
                LocalDate.of(2026, 7, 27),
                description,
                null,
                confidence,
                missingFields);
    }

    private FinancialExtractionProperties properties() {
        FinancialExtractionProperties properties = new FinancialExtractionProperties();
        properties.setMinimumConfidence(0.60);
        return properties;
    }
}
