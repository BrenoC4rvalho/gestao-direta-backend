package br.com.gestaodireta.ai.infrastructure.ollama;

import br.com.gestaodireta.ai.service.provider.AiGenerationRequest;
import br.com.gestaodireta.ai.service.provider.AiTextGenerationClient;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "ollama", matchIfMissing = true)
public class OllamaAiTextGenerationClient implements AiTextGenerationClient {

    private final RestClient restClient;

    private final String model;

    public OllamaAiTextGenerationClient(
            RestClient.Builder restClientBuilder,
            @Value("${app.ai.ollama.base-url}") String baseUrl,
            @Value("${app.ai.ollama.model}") String model) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.model = model;
    }

    @Override
    public String generate(AiGenerationRequest request) {
        OllamaGenerateResponse response =
                restClient
                        .post()
                        .uri("/api/generate")
                        .body(Map.of("model", model, "prompt", request.prompt(), "stream", false))
                        .retrieve()
                        .body(OllamaGenerateResponse.class);

        if (response == null || response.response() == null || response.response().isBlank()) {
            throw new IllegalStateException("Ollama returned an empty response");
        }

        return response.response();
    }

    @Override
    public String providerName() {
        return "ollama";
    }

    record OllamaGenerateResponse(String response) {}
}
