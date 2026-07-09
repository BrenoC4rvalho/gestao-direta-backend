package br.com.gestaodireta.ai.infrastructure.fake;

import br.com.gestaodireta.ai.service.provider.AiGenerationRequest;
import br.com.gestaodireta.ai.service.provider.AiTextGenerationClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "fake")
public class FakeAiTextGenerationClient implements AiTextGenerationClient {

    @Override
    public String generate(AiGenerationRequest request) {
        return """
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
    }

    @Override
    public String providerName() {
        return "fake";
    }
}
