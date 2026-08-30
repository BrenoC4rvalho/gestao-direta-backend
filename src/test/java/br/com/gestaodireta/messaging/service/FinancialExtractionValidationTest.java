package br.com.gestaodireta.messaging.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.gestaodireta.ai.config.FinancialExtractionProperties;
import br.com.gestaodireta.ai.service.dto.FinancialTransactionExtractionResult;
import br.com.gestaodireta.financial.enumeration.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

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
                .isTrue();
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

    @ParameterizedTest
    @MethodSource("brazilianMoneyFormats")
    void shouldNormalizeBrazilianMoneyFormats(String text, BigDecimal expectedAmount) {
        assertThat(evidence.monetaryAmounts(text)).containsExactly(expectedAmount);
    }

    @ParameterizedTest
    @MethodSource("expenseMoneyFormats")
    void shouldAcceptExpenseMoneyFormats(String text, BigDecimal expectedAmount) {
        assertThat(
                        resultValidator.isValid(
                                text,
                                result(
                                        TransactionType.EXPENSE,
                                        expectedAmount,
                                        "Diesel",
                                        new BigDecimal("0.95"),
                                        List.of())))
                .isTrue();
    }

    @Test
    void shouldAcceptRegressionCaseForFourThousandEightHundredReais() {
        String text = "Recebi R$ 4800,00 pela venda de milho hoje.";

        assertThat(evidence.monetaryAmounts(text)).containsExactly(new BigDecimal("4800.00"));
        assertThat(
                        resultValidator.validate(
                                text,
                                result(
                                        TransactionType.INCOME,
                                        new BigDecimal("4800.00"),
                                        "Venda de milho",
                                        new BigDecimal("0.95"),
                                        List.of())))
                .isEqualTo(
                        new FinancialTransactionExtractionResultValidator.ValidationResult(
                                true,
                                FinancialTransactionExtractionResultValidator.RejectionReason
                                        .NONE));
    }

    @Test
    void shouldRejectInventedOrPartialAmountsFromBrazilianMoneySource() {
        assertThat(
                        resultValidator
                                .validate(
                                        "Paguei R$ 400 de combustível.",
                                        result(
                                                TransactionType.EXPENSE,
                                                new BigDecimal("500.00"),
                                                "Combustível",
                                                new BigDecimal("0.95"),
                                                List.of()))
                                .reason())
                .isEqualTo(
                        FinancialTransactionExtractionResultValidator.RejectionReason
                                .AMOUNT_NOT_SUPPORTED_BY_SOURCE);
        assertThat(
                        resultValidator
                                .validate(
                                        "Paguei R$ 4.800,00 de combustível.",
                                        result(
                                                TransactionType.EXPENSE,
                                                new BigDecimal("480.00"),
                                                "Combustível",
                                                new BigDecimal("0.95"),
                                                List.of()))
                                .reason())
                .isEqualTo(
                        FinancialTransactionExtractionResultValidator.RejectionReason
                                .AMOUNT_NOT_SUPPORTED_BY_SOURCE);
    }

    @Test
    void shouldPreferMonetaryAmountOverPhysicalQuantities() {
        String text = "Comprei 10 sacos de fertilizante por R$ 2.500,00.";

        assertThat(evidence.monetaryAmounts(text)).containsExactly(new BigDecimal("2500.00"));
        assertThat(
                        resultValidator.isValid(
                                text,
                                result(
                                        TransactionType.EXPENSE,
                                        new BigDecimal("2500.00"),
                                        "Fertilizante",
                                        new BigDecimal("0.95"),
                                        List.of())))
                .isTrue();
        assertThat(
                        resultValidator.isValid(
                                text,
                                result(
                                        TransactionType.EXPENSE,
                                        new BigDecimal("10.00"),
                                        "Fertilizante",
                                        new BigDecimal("0.95"),
                                        List.of())))
                .isFalse();
    }

    @Test
    void shouldNotTreatPhysicalQuantityAsAmountWhenReaisIsPresent() {
        String text = "Comprei 20 litros de óleo por 350 reais.";

        assertThat(evidence.monetaryAmounts(text)).containsExactly(new BigDecimal("350.00"));
        assertThat(
                        resultValidator.isValid(
                                text,
                                result(
                                        TransactionType.EXPENSE,
                                        new BigDecimal("350.00"),
                                        "Óleo",
                                        new BigDecimal("0.95"),
                                        List.of())))
                .isTrue();
        assertThat(
                        resultValidator.isValid(
                                text,
                                result(
                                        TransactionType.EXPENSE,
                                        new BigDecimal("20.00"),
                                        "Óleo",
                                        new BigDecimal("0.95"),
                                        List.of())))
                .isFalse();
    }

    private static Stream<Arguments> expenseMoneyFormats() {
        return Stream.of(
                Arguments.of("Gastei R$400 com diesel hoje.", new BigDecimal("400.00")),
                Arguments.of("Gastei R$ 400 com diesel hoje.", new BigDecimal("400.00")),
                Arguments.of("Gastei R$400,00 com diesel hoje.", new BigDecimal("400.00")),
                Arguments.of("Gastei R$ 400,00 com diesel hoje.", new BigDecimal("400.00")),
                Arguments.of("Gastei 400 com diesel hoje.", new BigDecimal("400.00")),
                Arguments.of("Gastei 400,00 com diesel hoje.", new BigDecimal("400.00")),
                Arguments.of("Gastei 400,0 com diesel hoje.", new BigDecimal("400.00")));
    }

    private static Stream<Arguments> brazilianMoneyFormats() {
        return Stream.of(
                Arguments.of("Paguei R$400 com diesel.", new BigDecimal("400.00")),
                Arguments.of("Paguei R$ 400 com diesel.", new BigDecimal("400.00")),
                Arguments.of("Paguei R$400,00 com diesel.", new BigDecimal("400.00")),
                Arguments.of("Paguei R$ 400,00 com diesel.", new BigDecimal("400.00")),
                Arguments.of("Paguei 400 com diesel.", new BigDecimal("400.00")),
                Arguments.of("Paguei 400,00 com diesel.", new BigDecimal("400.00")),
                Arguments.of("Paguei 400,0 com diesel.", new BigDecimal("400.00")),
                Arguments.of("Recebi R$4.800,00 pela venda.", new BigDecimal("4800.00")),
                Arguments.of("Recebi R$ 4.800,00 pela venda.", new BigDecimal("4800.00")),
                Arguments.of("Recebi 4.800,00 pela venda.", new BigDecimal("4800.00")),
                Arguments.of("Recebi 4800,00 pela venda.", new BigDecimal("4800.00")),
                Arguments.of("Recebi 4800 pela venda.", new BigDecimal("4800.00")),
                Arguments.of("Recebi R$ 12.500,00 pela venda.", new BigDecimal("12500.00")),
                Arguments.of("Recebi 12.500,00 pela venda.", new BigDecimal("12500.00")),
                Arguments.of("Recebi 12500,00 pela venda.", new BigDecimal("12500.00")),
                Arguments.of("Recebi 12500 pela venda.", new BigDecimal("12500.00")));
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
