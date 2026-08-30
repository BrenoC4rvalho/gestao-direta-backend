package br.com.gestaodireta.ai.infrastructure.gemini;

import br.com.gestaodireta.ai.service.AiModelNotAvailableException;
import br.com.gestaodireta.ai.service.AiProviderException;
import br.com.gestaodireta.ai.service.provider.AiGenerationRequest;
import br.com.gestaodireta.ai.service.provider.AiTextGenerationClient;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

public class GeminiAiTextGenerationClient implements AiTextGenerationClient {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(GeminiAiTextGenerationClient.class);

    private static final String BASE_URL = "https://generativelanguage.googleapis.com";

    private final RestClient restClient;
    private final String model;

    public GeminiAiTextGenerationClient(
            RestClient.Builder restClientBuilder, GeminiAiProperties properties) {
        this.model = properties.getModel();
        this.restClient =
                restClientBuilder
                        .baseUrl(BASE_URL)
                        .defaultHeader("x-goog-api-key", properties.getApiKey())
                        .build();
    }

    public GeminiAiTextGenerationClient(
            RestClient.Builder restClientBuilder,
            GeminiAiProperties properties,
            int timeoutSeconds) {
        this.model = properties.getModel();
        this.restClient =
                restClientBuilder
                        .baseUrl(BASE_URL)
                        .defaultHeader("x-goog-api-key", properties.getApiKey())
                        .requestFactory(requestFactory(timeoutSeconds))
                        .build();
    }

    @Override
    public String generate(AiGenerationRequest request) {
        long startNanos = System.nanoTime();
        try {
            JsonNode response =
                    restClient
                            .post()
                            .uri("/v1beta/models/{model}:generateContent", model)
                            .body(requestBody(request))
                            .retrieve()
                            .body(JsonNode.class);
            String text = responseText(response);
            LOGGER.info(
                    "Gemini generation completed. model={} success=true elapsedMs={}",
                    model,
                    elapsedMillis(startNanos));
            return text;
        } catch (RestClientResponseException exception) {
            LOGGER.warn(
                    "Gemini generation failed. model={} status={} elapsedMs={}",
                    model,
                    exception.getStatusCode().value(),
                    elapsedMillis(startNanos));
            throw translateHttpException(exception);
        } catch (ResourceAccessException exception) {
            AiProviderException.Reason reason =
                    hasTimeoutCause(exception)
                            ? AiProviderException.Reason.TIMEOUT
                            : AiProviderException.Reason.SERVICE_UNAVAILABLE;
            LOGGER.warn(
                    "Gemini generation failed. model={} reason={} elapsedMs={}",
                    model,
                    reason,
                    elapsedMillis(startNanos));
            throw new AiProviderException(
                    reason, null, "Gemini request timed out or could not be reached", exception);
        } catch (AiProviderException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Gemini generation failed. model={} reason=INVALID_RESPONSE elapsedMs={}",
                    model,
                    elapsedMillis(startNanos));
            throw new AiProviderException(
                    AiProviderException.Reason.INVALID_RESPONSE,
                    null,
                    "Gemini returned an invalid response",
                    exception);
        }
    }

    @Override
    public String providerName() {
        return "gemini";
    }

    @Override
    public String modelName() {
        return model;
    }

    private Map<String, Object> requestBody(AiGenerationRequest request) {
        Map<String, Object> body =
                Map.of(
                        "contents",
                        List.of(Map.of("parts", List.of(Map.of("text", request.prompt())))));
        if (!request.hasResponseSchema()) {
            return body;
        }
        return Map.of(
                "contents", body.get("contents"),
                "generationConfig",
                        Map.of(
                                "responseFormat",
                                Map.of(
                                        "text",
                                        Map.of(
                                                "mimeType",
                                                "application/json",
                                                "schema",
                                                request.responseSchema()))));
    }

    private String responseText(JsonNode response) {
        if (response == null || response.path("promptFeedback").hasNonNull("blockReason")) {
            throw new AiProviderException(
                    AiProviderException.Reason.INVALID_RESPONSE,
                    null,
                    "Gemini blocked the extraction response",
                    null);
        }
        JsonNode candidate = response.path("candidates").path(0);
        if (candidate.isMissingNode() || candidate.path("finishReason").asText().equals("SAFETY")) {
            throw new AiProviderException(
                    AiProviderException.Reason.INVALID_RESPONSE,
                    null,
                    "Gemini returned no usable extraction candidate",
                    null);
        }
        String text = candidate.path("content").path("parts").path(0).path("text").asText();
        if (text == null || text.isBlank()) {
            throw new AiProviderException(
                    AiProviderException.Reason.INVALID_RESPONSE,
                    null,
                    "Gemini returned an empty response",
                    null);
        }
        return text;
    }

    private RuntimeException translateHttpException(RestClientResponseException exception) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        if (status == HttpStatus.NOT_FOUND) {
            return new AiModelNotAvailableException(
                    "Configured Gemini model was not found.", model);
        }
        AiProviderException.Reason reason =
                switch (status.value()) {
                    case 401 -> AiProviderException.Reason.UNAUTHORIZED;
                    case 403 -> AiProviderException.Reason.FORBIDDEN;
                    case 429 -> AiProviderException.Reason.RATE_LIMIT;
                    default -> AiProviderException.Reason.SERVICE_UNAVAILABLE;
                };
        return new AiProviderException(
                reason,
                status.value(),
                "Gemini API request failed with HTTP status " + status.value(),
                exception);
    }

    private SimpleClientHttpRequestFactory requestFactory(int timeoutSeconds) {
        Duration timeout = Duration.ofSeconds(timeoutSeconds);
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        return factory;
    }

    private long elapsedMillis(long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
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
}
