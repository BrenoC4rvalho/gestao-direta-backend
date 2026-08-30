package br.com.gestaodireta.ai.infrastructure.fake;

import br.com.gestaodireta.ai.service.provider.AiGenerationRequest;
import br.com.gestaodireta.ai.service.provider.AiTextGenerationClient;

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

    @Override
    public void probe() {}
}
