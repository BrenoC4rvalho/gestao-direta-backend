package br.com.gestaodireta.messaging.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class FinancialMessageEligibilityValidatorTest {
    private final TelegramFinancialMessageEligibilityValidator validator =
            new TelegramFinancialMessageEligibilityValidator(
                    new FinancialMessageEvidenceExtractor());

    @Test
    void shouldAcceptNaturalFinancialMessagesWithContextAndMonetaryAmount() {
        for (String text :
                List.of(
                        "vendi 20kg de milho por 100 reais hoje",
                        "comprei 10 sacos de adubo por 500",
                        "paguei 300 no diesel",
                        "recebi 2000 pela soja",
                        "entrou 1500 da venda do gado",
                        "gastei 90 com combustível",
                        "foi 400 de manutenção no trator")) {
            assertThat(validator.isEligible(text)).as(text).isTrue();
        }
    }

    @Test
    void shouldRejectClearlyInsufficientMessages() {
        for (String text :
                List.of("90", "a", "teste", "R$ 250", "20kg", "milho", "hoje", "/ajuda")) {
            assertThat(validator.isEligible(text)).as(text).isFalse();
        }
    }
}
