package br.com.gestaodireta.ai.infrastructure.ollama;

import br.com.gestaodireta.ai.service.AiModelNotAvailableException;
import br.com.gestaodireta.ai.service.provider.AiGenerationRequest;
import br.com.gestaodireta.ai.service.provider.AiTextGenerationClient;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "ollama", matchIfMissing = true)
public class OllamaAiTextGenerationClient implements AiTextGenerationClient {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(OllamaAiTextGenerationClient.class);

    private static final String MODEL_NOT_FOUND_MESSAGE =
            "Modelo de IA não encontrado no Ollama. Baixe o modelo configurado antes de usar a IA.";

    private final RestClient restClient;

    private final String baseUrl;

    private final String model;

    public OllamaAiTextGenerationClient(
            RestClient.Builder restClientBuilder, OllamaAiProperties ollamaAiProperties) {
        this.baseUrl = ollamaAiProperties.getBaseUrl();
        this.model = ollamaAiProperties.getModel();
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
    }

    @Override
    public String generate(AiGenerationRequest request) {
        OllamaGenerateResponse response;

        try {
            response =
                    restClient
                            .post()
                            .uri("/api/generate")
                            .body(
                                    Map.of(
                                            "model",
                                            model,
                                            "prompt",
                                            request.prompt(),
                                            "stream",
                                            false))
                            .retrieve()
                            .body(OllamaGenerateResponse.class);
        } catch (RestClientResponseException exception) {
            if (isModelNotFoundResponse(exception)) {
                LOGGER.warn("Ollama model not found. model={} baseUrl={}", model, baseUrl);
                throw new AiModelNotAvailableException(MODEL_NOT_FOUND_MESSAGE, model);
            }

            throw exception;
        }

        if (response == null || response.response() == null || response.response().isBlank()) {
            throw new IllegalStateException("Ollama returned an empty response");
        }

        return response.response();
    }

    @Override
    public String providerName() {
        return "ollama";
    }

    private boolean isModelNotFoundResponse(RestClientResponseException exception) {
        if (!HttpStatus.NOT_FOUND.equals(exception.getStatusCode())) {
            return false;
        }

        String responseBody = exception.getResponseBodyAsString().toLowerCase();

        return responseBody.contains("model") && responseBody.contains("not found");
    }

    record OllamaGenerateResponse(String response) {}
}
