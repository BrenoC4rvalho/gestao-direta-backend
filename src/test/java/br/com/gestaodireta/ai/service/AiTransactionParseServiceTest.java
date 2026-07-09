package br.com.gestaodireta.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.gestaodireta.ai.service.dto.ParseTransactionTextRequest;
import br.com.gestaodireta.ai.service.dto.ParsedTransactionResponse;
import br.com.gestaodireta.ai.service.provider.AiGenerationRequest;
import br.com.gestaodireta.ai.service.provider.AiTextGenerationClient;
import br.com.gestaodireta.financial.enumeration.PaymentMethod;
import br.com.gestaodireta.financial.enumeration.PaymentStatus;
import br.com.gestaodireta.financial.enumeration.TransactionType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class AiTransactionParseServiceTest {

    private final CapturingAiTextGenerationClient aiTextGenerationClient =
            new CapturingAiTextGenerationClient();

    private final AiTransactionParseService service =
            new AiTransactionParseService(
                    aiTextGenerationClient,
                    new TransactionTextPromptBuilder(),
                    new ParsedTransactionJsonParser(new ObjectMapper()),
                    Clock.fixed(
                            Instant.parse("2026-07-07T03:00:00Z"), ZoneId.of("America/Sao_Paulo")));

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldCallProviderBuildPromptAndParseValidJson() {
        authenticate();
        aiTextGenerationClient.response =
                """
                {
                  "type": "EXPENSE",
                  "amount": 250.00,
                  "description": "Adubo",
                  "transactionDate": "2026-07-06",
                  "dueDate": null,
                  "paymentStatus": "PAID",
                  "paymentMethod": "PIX",
                  "categoryName": "Insumos",
                  "harvestSeasonName": "Milho",
                  "confidence": 0.87,
                  "missingFields": [],
                  "warnings": []
                }
                """;

        ParsedTransactionResponse response =
                service.parse(
                        new ParseTransactionTextRequest(
                                19L,
                                "paguei 250 reais de adubo para a safra de milho ontem no pix"));

        assertThat(aiTextGenerationClient.requests).hasSize(1);
        assertThat(aiTextGenerationClient.requests.getFirst().prompt())
                .contains("Data atual: 2026-07-07")
                .contains("paguei 250 reais de adubo")
                .contains("paymentMethod: PIX");
        assertThat(response.farmId()).isEqualTo(19L);
        assertThat(response.type()).isEqualTo(TransactionType.EXPENSE);
        assertThat(response.paymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(response.paymentMethod()).isEqualTo(PaymentMethod.PIX);
        assertThat(response.categoryName()).isEqualTo("Insumos");
    }

    @Test
    void shouldExtractJsonWhenProviderReturnsTextAroundIt() {
        authenticate();
        aiTextGenerationClient.response =
                """
                Segue:
                {
                  "type": "INCOME",
                  "amount": 1000.00,
                  "description": "Venda de milho",
                  "transactionDate": "2026-07-07",
                  "dueDate": null,
                  "paymentStatus": "PAID",
                  "paymentMethod": "BANK_TRANSFER",
                  "categoryName": "Vendas",
                  "harvestSeasonName": "Milho",
                  "confidence": 0.75,
                  "missingFields": [],
                  "warnings": []
                }
                Obrigado.
                """;

        ParsedTransactionResponse response =
                service.parse(new ParseTransactionTextRequest(19L, "recebi venda de milho"));

        assertThat(response.type()).isEqualTo(TransactionType.INCOME);
        assertThat(response.paymentMethod()).isEqualTo(PaymentMethod.BANK_TRANSFER);
    }

    @Test
    void shouldRejectInvalidJson() {
        authenticate();
        aiTextGenerationClient.response = "not-json";

        assertThatThrownBy(() -> service.parse(new ParseTransactionTextRequest(19L, "texto")))
                .isInstanceOf(AiParsingException.class)
                .hasMessage("Não foi possível interpretar o texto como movimentação financeira.");
    }

    @Test
    void shouldRejectInvalidEnum() {
        authenticate();
        aiTextGenerationClient.response =
                """
                {
                  "type": "INVALID",
                  "amount": 250.00,
                  "description": "Adubo",
                  "transactionDate": "2026-07-06",
                  "dueDate": null,
                  "paymentStatus": "PAID",
                  "paymentMethod": "PIX",
                  "categoryName": "Insumos",
                  "harvestSeasonName": "Milho",
                  "confidence": 0.87,
                  "missingFields": [],
                  "warnings": []
                }
                """;

        assertThatThrownBy(() -> service.parse(new ParseTransactionTextRequest(19L, "texto")))
                .isInstanceOf(AiParsingException.class);
    }

    @Test
    void shouldRejectCanceledPaymentStatus() {
        authenticate();
        aiTextGenerationClient.response =
                """
                {
                  "type": "EXPENSE",
                  "amount": 250.00,
                  "description": "Adubo",
                  "transactionDate": "2026-07-06",
                  "dueDate": null,
                  "paymentStatus": "CANCELED",
                  "paymentMethod": "PIX",
                  "categoryName": "Insumos",
                  "harvestSeasonName": "Milho",
                  "confidence": 0.87,
                  "missingFields": [],
                  "warnings": []
                }
                """;

        assertThatThrownBy(() -> service.parse(new ParseTransactionTextRequest(19L, "texto")))
                .isInstanceOf(AiParsingException.class);
    }

    private void authenticate() {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken("10", null, List.of()));
    }

    private static class CapturingAiTextGenerationClient implements AiTextGenerationClient {

        private final List<AiGenerationRequest> requests = new java.util.ArrayList<>();

        private String response;

        @Override
        public String generate(AiGenerationRequest request) {
            requests.add(request);

            return response;
        }

        @Override
        public String providerName() {
            return "test";
        }
    }
}
