package br.com.gestaodireta.ai.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    private FinancialTransactionExtractionService serviceWith(String response) {
        FinancialExtractionProperties properties = new FinancialExtractionProperties();
        properties.setEnabled(true);
        AiTextGenerationClient client =
                new AiTextGenerationClient() {
                    @Override
                    public String generate(AiGenerationRequest request) {
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
