package br.com.gestaodireta.ai.infrastructure.ollama;

import br.com.gestaodireta.ai.service.AiErrorSanitizer;
import br.com.gestaodireta.ai.service.AiModelNotAvailableException;
import br.com.gestaodireta.ai.service.AiProviderException;
import br.com.gestaodireta.ai.service.provider.AiGenerationRequest;
import br.com.gestaodireta.ai.service.provider.AiTextGenerationClient;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

public class OllamaAiTextGenerationClient implements AiTextGenerationClient {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(OllamaAiTextGenerationClient.class);

    private static final String MODEL_NOT_FOUND_MESSAGE =
            "Modelo de IA não encontrado no Ollama. Baixe o modelo configurado antes de usar a IA.";

    private static final String PROVIDER = "ollama";

    private final RestClient restClient;

    private final RestClient healthRestClient;

    private final String baseUrl;

    private final String model;

    private final String format;

    private final double temperature;

    public OllamaAiTextGenerationClient(
            RestClient.Builder restClientBuilder, OllamaAiProperties ollamaAiProperties) {
        this.baseUrl = ollamaAiProperties.getBaseUrl();
        this.model = ollamaAiProperties.getModel();
        this.format = ollamaAiProperties.getFormat();
        this.temperature = ollamaAiProperties.getTemperature();
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.healthRestClient = restClient;
    }

    public OllamaAiTextGenerationClient(
            RestClient.Builder restClientBuilder,
            OllamaAiProperties ollamaAiProperties,
            int timeoutSeconds) {
        this(restClientBuilder, ollamaAiProperties, timeoutSeconds, 5);
    }

    public OllamaAiTextGenerationClient(
            RestClient.Builder restClientBuilder,
            OllamaAiProperties ollamaAiProperties,
            int timeoutSeconds,
            int healthTimeoutSeconds) {
        this.baseUrl = ollamaAiProperties.getBaseUrl();
        this.model = ollamaAiProperties.getModel();
        this.format = ollamaAiProperties.getFormat();
        this.temperature = ollamaAiProperties.getTemperature();
        this.restClient =
                restClientBuilder
                        .baseUrl(baseUrl)
                        .requestFactory(requestFactory(timeoutSeconds))
                        .build();
        this.healthRestClient =
                restClientBuilder
                        .clone()
                        .baseUrl(baseUrl)
                        .requestFactory(requestFactory(healthTimeoutSeconds))
                        .build();
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
                                            false,
                                            "format",
                                            format,
                                            "options",
                                            Map.of("temperature", temperature)))
                            .retrieve()
                            .body(OllamaGenerateResponse.class);
        } catch (RestClientResponseException exception) {
            if (isModelNotFoundResponse(exception)) {
                LOGGER.warn("Ollama model not found. provider={} model={}", PROVIDER, model);
                throw new AiModelNotAvailableException(
                        MODEL_NOT_FOUND_MESSAGE, PROVIDER, model, 404, null);
            }
            throw translateHttpException(exception);
        } catch (ResourceAccessException exception) {
            throw new AiProviderException(
                    hasTimeoutCause(exception)
                            ? AiProviderException.Reason.TIMEOUT
                            : AiProviderException.Reason.SERVICE_UNAVAILABLE,
                    null,
                    PROVIDER,
                    model,
                    null,
                    "Ollama could not be reached",
                    exception);
        }

        if (response == null || response.response() == null || response.response().isBlank()) {
            throw new AiProviderException(
                    AiProviderException.Reason.INVALID_RESPONSE,
                    null,
                    PROVIDER,
                    model,
                    null,
                    "Ollama returned an empty response",
                    null);
        }

        return response.response();
    }

    @Override
    public String providerName() {
        return "ollama";
    }

    @Override
    public String modelName() {
        return model;
    }

    @Override
    public void probe() {
        try {
            Object response =
                    healthRestClient
                            .post()
                            .uri("/api/show")
                            .body(Map.of("model", model))
                            .retrieve()
                            .body(Object.class);
            if (response == null) {
                throw new AiProviderException(
                        AiProviderException.Reason.INVALID_RESPONSE,
                        null,
                        PROVIDER,
                        model,
                        null,
                        "Ollama returned an empty model probe response",
                        null);
            }
        } catch (RestClientResponseException exception) {
            if (isModelNotFoundResponse(exception)) {
                throw new AiModelNotAvailableException(
                        MODEL_NOT_FOUND_MESSAGE, PROVIDER, model, 404, null);
            }
            throw translateHttpException(exception);
        } catch (ResourceAccessException exception) {
            throw new AiProviderException(
                    hasTimeoutCause(exception)
                            ? AiProviderException.Reason.TIMEOUT
                            : AiProviderException.Reason.SERVICE_UNAVAILABLE,
                    null,
                    PROVIDER,
                    model,
                    null,
                    "Ollama health probe could not reach the service",
                    exception);
        }
    }

    private boolean isModelNotFoundResponse(RestClientResponseException exception) {
        if (!HttpStatus.NOT_FOUND.equals(exception.getStatusCode())) {
            return false;
        }

        String responseBody = exception.getResponseBodyAsString().toLowerCase();

        return responseBody.contains("model") && responseBody.contains("not found");
    }

    private AiProviderException translateHttpException(RestClientResponseException exception) {
        int statusCode = exception.getStatusCode().value();
        AiProviderException.Reason reason =
                switch (statusCode) {
                    case 400 -> AiProviderException.Reason.INVALID_REQUEST;
                    case 401 -> AiProviderException.Reason.UNAUTHORIZED;
                    case 403 -> AiProviderException.Reason.FORBIDDEN;
                    case 429 -> AiProviderException.Reason.RATE_LIMIT;
                    default -> AiProviderException.Reason.SERVICE_UNAVAILABLE;
                };
        return new AiProviderException(
                reason,
                statusCode,
                PROVIDER,
                model,
                null,
                AiErrorSanitizer.message(exception.getResponseBodyAsString()),
                null);
    }

    private boolean hasTimeoutCause(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof java.net.SocketTimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private SimpleClientHttpRequestFactory requestFactory(int timeoutSeconds) {
        Duration timeout = Duration.ofSeconds(timeoutSeconds);
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        return factory;
    }

    record OllamaGenerateResponse(String response) {}
}
