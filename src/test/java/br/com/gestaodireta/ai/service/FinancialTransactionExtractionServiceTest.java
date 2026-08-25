package br.com.gestaodireta.ai.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.gestaodireta.ai.config.FinancialExtractionProperties;
import br.com.gestaodireta.ai.service.dto.FinancialTransactionExtractionResult;
import br.com.gestaodireta.ai.service.provider.AiGenerationRequest;
import br.com.gestaodireta.ai.service.provider.AiTextGenerationClient;
import br.com.gestaodireta.financial.enumeration.TransactionType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class FinancialTransactionExtractionServiceTest {

    @Test
    void shouldMapAValidStructuredFinancialResponse() {
        FinancialTransactionExtractionService service =
                serviceWith(
                        """
                {"isFinancialTransaction":true,"type":"EXPENSE","amount":250.50,
                "transactionDate":"2026-07-26","description":"Compra de adubo",
                "categoryName":"Insumos","confidence":0.94,"missingFields":[]}
                """);

        FinancialTransactionExtractionResult result =
                service.extract("Gastei 250,50 com adubo", "Boa Vista", java.util.List.of());

        assertEquals(TransactionType.EXPENSE, result.type());
        assertEquals(new BigDecimal("250.50"), result.amount());
        assertEquals("Compra de adubo", result.description());
    }

    @Test
    void shouldRejectInvalidStructuredFinancialResponse() {
        FinancialTransactionExtractionService service =
                serviceWith("{\"isFinancialTransaction\":true}");

        assertThrows(
                AiParsingException.class,
                () -> service.extract("Gastei com adubo", "Boa Vista", java.util.List.of()));
    }


    @Test
    void shouldUseCompactPromptAndParseDeterministicStructuredResponses() {
        assertStructuredResponse(
                "Paguei 780 de manutenção da colheitadeira.",
                "{\"isFinancialTransaction\":true,\"type\":\"EXPENSE\",\"amount\":780,\"transactionDate\":null,\"description\":\"Manutenção da colheitadeira\",\"categoryName\":\"Manutenção\",\"confidence\":0.95,\"missingFields\":[\"transactionDate\"]}",
                "Manutenção da colheitadeira");
        assertStructuredResponse(
                "Gastei R$ 350,00 com diesel para o trator hoje.",
                "{\"isFinancialTransaction\":true,\"type\":\"EXPENSE\",\"amount\":350,\"transactionDate\":\"2026-07-26\",\"description\":\"Diesel para o trator\",\"categoryName\":\"Combustível\",\"confidence\":0.95,\"missingFields\":[]}",
                "Diesel para o trator");
        assertStructuredResponse(
                "Comprei R$ 2.300 de fertilizante para a soja.",
                "{\"isFinancialTransaction\":true,\"type\":\"EXPENSE\",\"amount\":2300,\"transactionDate\":null,\"description\":\"Fertilizante para a soja\",\"categoryName\":\"Insumos\",\"confidence\":0.95,\"missingFields\":[\"transactionDate\"]}",
                "Fertilizante para a soja");
        assertStructuredResponse(
                "Recebi R$ 4.800 pela venda de milho.",
                "{\"isFinancialTransaction\":true,\"type\":\"INCOME\",\"amount\":4800,\"transactionDate\":null,\"description\":\"Venda de milho\",\"categoryName\":\"Vendas\",\"confidence\":0.95,\"missingFields\":[\"transactionDate\"]}",
                "Venda de milho");
        assertStructuredResponse(
                "Paguei 450 reais de energia da fazenda ontem.",
                "{\"isFinancialTransaction\":true,\"type\":\"EXPENSE\",\"amount\":450,\"transactionDate\":\"2026-07-25\",\"description\":\"Energia da fazenda\",\"categoryName\":\"Utilidades\",\"confidence\":0.95,\"missingFields\":[]}",
                "Energia da fazenda");
        assertStructuredResponse(
                "Paguei 780 manutenção colheitadeira",
                "{\"isFinancialTransaction\":true,\"type\":\"EXPENSE\",\"amount\":780,\"transactionDate\":null,\"description\":\"Manutenção colheitadeira\",\"categoryName\":\"Manutenção\",\"confidence\":0.95,\"missingFields\":[\"transactionDate\"]}",
                "Manutenção colheitadeira");
        assertStructuredResponse(
                "350 no diesel do trator hj",
                "{\"isFinancialTransaction\":true,\"type\":\"EXPENSE\",\"amount\":350,\"transactionDate\":null,\"description\":\"Diesel do trator\",\"categoryName\":\"Combustível\",\"confidence\":0.95,\"missingFields\":[\"transactionDate\"]}",
                "Diesel do trator");
        assertStructuredResponse(
                "Gastei R$ 300 hoje.",
                "{\"isFinancialTransaction\":true,\"type\":\"EXPENSE\",\"amount\":300,\"transactionDate\":\"2026-07-26\",\"description\":null,\"categoryName\":null,\"confidence\":0.95,\"missingFields\":[\"description\"]}",
                null);
        assertStructuredResponse(
                "oi, tudo bem?",
                "{\"isFinancialTransaction\":false,\"type\":null,\"amount\":null,\"transactionDate\":null,\"description\":null,\"categoryName\":null,\"confidence\":0,\"missingFields\":[]}",
                null);
    }

    private void assertStructuredResponse(String text, String response, String description) {
        AtomicReference<AiGenerationRequest> request = new AtomicReference<>();
        FinancialTransactionExtractionService service = serviceWith(response, request);

        FinancialTransactionExtractionResult result =
                service.extract(text, "Boa Vista", java.util.List.of());

        assertEquals(description, result.description());
        assertTrue(request.get().prompt().contains("Responda somente o JSON"));
        assertTrue(request.get().prompt().contains("description nunca é categoryName"));
    }

    private FinancialTransactionExtractionService serviceWith(String response) {
        return serviceWith(response, new AtomicReference<>());
    }

    private FinancialTransactionExtractionService serviceWith(
            String response, AtomicReference<AiGenerationRequest> capturedRequest) {
        FinancialExtractionProperties properties = new FinancialExtractionProperties();
        properties.setEnabled(true);
        AiTextGenerationClient client =
                new AiTextGenerationClient() {
                    @Override
                    public String generate(AiGenerationRequest request) {
                        capturedRequest.set(request);
                        return response;
                    }

                    @Override
                    public String providerName() {
                        return "fake";
                    }
                };
        return new FinancialTransactionExtractionService(
                client,
                properties,
                new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-07-26T12:00:00Z"), ZoneId.of("America/Sao_Paulo")));
    }
}
