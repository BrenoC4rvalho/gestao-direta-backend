package br.com.gestaodireta.ai.infrastructure.gemini;

import br.com.gestaodireta.ai.service.AiErrorSanitizer;
import br.com.gestaodireta.ai.service.AiModelNotAvailableException;
import br.com.gestaodireta.ai.service.AiProviderException;
import br.com.gestaodireta.ai.service.provider.AiGenerationRequest;
import br.com.gestaodireta.ai.service.provider.AiTextGenerationClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    private static final String PROVIDER = "gemini";

    private static final String GENERATE_CONTENT_ENDPOINT = "generateContent";

    private static final int PROBE_TEXT_PREVIEW_LENGTH = 120;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final RestClient restClient;

    private final RestClient healthRestClient;
    private final String model;

    public GeminiAiTextGenerationClient(
            RestClient.Builder restClientBuilder, GeminiAiProperties properties) {
        this.model = properties.getModel();
        this.restClient =
                restClientBuilder
                        .baseUrl(BASE_URL)
                        .defaultHeader("x-goog-api-key", properties.getApiKey())
                        .build();
        this.healthRestClient = restClient;
    }

    public GeminiAiTextGenerationClient(
            RestClient.Builder restClientBuilder,
            GeminiAiProperties properties,
            int timeoutSeconds) {
        this(restClientBuilder, properties, timeoutSeconds, 5);
    }

    public GeminiAiTextGenerationClient(
            RestClient.Builder restClientBuilder,
            GeminiAiProperties properties,
            int timeoutSeconds,
            int healthTimeoutSeconds) {
        this.model = properties.getModel();
        this.restClient =
                restClientBuilder
                        .baseUrl(BASE_URL)
                        .defaultHeader("x-goog-api-key", properties.getApiKey())
                        .requestFactory(requestFactory(timeoutSeconds))
                        .build();
        this.healthRestClient =
                restClientBuilder
                        .clone()
                        .baseUrl(BASE_URL)
                        .defaultHeader("x-goog-api-key", properties.getApiKey())
                        .requestFactory(requestFactory(healthTimeoutSeconds))
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
            throw logAndTranslateHttpException(exception, startNanos);
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
                    reason,
                    null,
                    PROVIDER,
                    model,
                    null,
                    "Gemini request timed out or could not be reached",
                    exception);
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
                    PROVIDER,
                    model,
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

    @Override
    public void probe() {
        long startNanos = System.nanoTime();
        try {
            JsonNode response =
                    healthRestClient
                            .post()
                            .uri("/v1beta/models/{model}:generateContent", model)
                            .body(requestBody("Reply only with OK.", null))
                            .retrieve()
                            .body(JsonNode.class);
            probeResponseText(response);
        } catch (RestClientResponseException exception) {
            throw logAndTranslateHttpException(exception, startNanos);
        } catch (ResourceAccessException exception) {
            AiProviderException.Reason reason =
                    hasTimeoutCause(exception)
                            ? AiProviderException.Reason.TIMEOUT
                            : AiProviderException.Reason.SERVICE_UNAVAILABLE;
            throw new AiProviderException(
                    reason,
                    null,
                    PROVIDER,
                    model,
                    null,
                    "Gemini health probe timed out or could not be reached",
                    exception);
        } catch (AiProviderException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            logProbeEvaluation(0, null, 0, null, "unreadable_response");
            throw new AiProviderException(
                    AiProviderException.Reason.INVALID_RESPONSE,
                    null,
                    PROVIDER,
                    model,
                    null,
                    "Gemini returned an invalid health probe response",
                    exception);
        }
    }

    private Map<String, Object> requestBody(AiGenerationRequest request) {
        return requestBody(request.prompt(), request.responseSchema());
    }

    private Map<String, Object> requestBody(String prompt, Map<String, Object> responseSchema) {
        Map<String, Object> body =
                Map.of("contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))));
        if (responseSchema == null || responseSchema.isEmpty()) {
            return body;
        }
        return Map.of(
                "contents", body.get("contents"),
                "generationConfig",
                        Map.of(
                                "responseMimeType",
                                "application/json",
                                "responseJsonSchema",
                                responseSchema));
    }

    private String responseText(JsonNode response) {
        if (response == null || response.path("promptFeedback").hasNonNull("blockReason")) {
            throw new AiProviderException(
                    AiProviderException.Reason.INVALID_RESPONSE,
                    null,
                    PROVIDER,
                    model,
                    null,
                    "Gemini blocked the extraction response",
                    null);
        }
        JsonNode candidate = response.path("candidates").path(0);
        if (candidate.isMissingNode() || candidate.path("finishReason").asText().equals("SAFETY")) {
            throw new AiProviderException(
                    AiProviderException.Reason.INVALID_RESPONSE,
                    null,
                    PROVIDER,
                    model,
                    null,
                    "Gemini returned no usable extraction candidate",
                    null);
        }
        String text = candidate.path("content").path("parts").path(0).path("text").asText();
        if (text == null || text.isBlank()) {
            throw new AiProviderException(
                    AiProviderException.Reason.INVALID_RESPONSE,
                    null,
                    PROVIDER,
                    model,
                    null,
                    "Gemini returned an empty response",
                    null);
        }
        return text;
    }

    private String probeResponseText(JsonNode response) {
        int candidateCount = candidateCount(response);
        if (response == null || response.path("promptFeedback").hasNonNull("blockReason")) {
            throw invalidProbeResponse(candidateCount, null, 0, null, "blocked");
        }

        JsonNode candidates = response.path("candidates");
        if (!candidates.isArray() || candidates.isEmpty()) {
            throw invalidProbeResponse(candidateCount, null, 0, null, "no_candidates");
        }

        String lastFinishReason = null;
        int lastPartCount = 0;
        for (JsonNode candidate : candidates) {
            lastFinishReason = candidate.path("finishReason").asText(null);
            JsonNode parts = candidate.path("content").path("parts");
            lastPartCount = parts.isArray() ? parts.size() : 0;
            if (!parts.isArray()) {
                continue;
            }
            for (JsonNode part : parts) {
                String text = normalizeProbeText(part.path("text").asText(null));
                if (text == null) {
                    continue;
                }
                logProbeEvaluation(
                        candidateCount, lastFinishReason, lastPartCount, text, "usable_text");
                return text;
            }
        }

        throw invalidProbeResponse(
                candidateCount, lastFinishReason, lastPartCount, null, "no_usable_text");
    }

    private int candidateCount(JsonNode response) {
        if (response == null || !response.path("candidates").isArray()) {
            return 0;
        }
        return response.path("candidates").size();
    }

    private String normalizeProbeText(String text) {
        if (text == null) {
            return null;
        }
        String normalized = text.replaceAll("[\\r\\n\\t]+", " ").trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private AiProviderException invalidProbeResponse(
            int candidateCount,
            String finishReason,
            int partCount,
            String text,
            String evaluation) {
        logProbeEvaluation(candidateCount, finishReason, partCount, text, evaluation);
        return new AiProviderException(
                AiProviderException.Reason.INVALID_RESPONSE,
                null,
                PROVIDER,
                model,
                null,
                "Gemini returned an invalid health probe response",
                null);
    }

    private void logProbeEvaluation(
            int candidateCount,
            String finishReason,
            int partCount,
            String normalizedText,
            String evaluation) {
        String preview = normalizedText == null ? null : AiErrorSanitizer.message(normalizedText);
        if (preview != null && preview.length() > PROBE_TEXT_PREVIEW_LENGTH) {
            preview = preview.substring(0, PROBE_TEXT_PREVIEW_LENGTH) + "...";
        }
        LOGGER.debug(
                "Gemini health probe evaluated. model={} candidates={} finishReason={} parts={} textLength={} textPreview={} result={}",
                model,
                candidateCount,
                finishReason,
                partCount,
                normalizedText == null ? 0 : normalizedText.length(),
                preview,
                evaluation);
    }

    private RuntimeException logAndTranslateHttpException(
            RestClientResponseException exception, long startNanos) {
        AiProviderException translated = translateHttpException(exception);
        LOGGER.warn(
                "Gemini request failed: provider={} model={} endpoint={} httpStatus={} apiErrorCode={} apiErrorStatus={} apiErrorMessage={} elapsedMs={}",
                PROVIDER,
                model,
                GENERATE_CONTENT_ENDPOINT,
                translated.getStatusCode(),
                apiErrorCode(exception),
                translated.getProviderStatus(),
                translated.getMessage(),
                elapsedMillis(startNanos));
        return translated;
    }

    private AiProviderException translateHttpException(RestClientResponseException exception) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        GeminiApiError apiError = parseApiError(exception.getResponseBodyAsString());
        if (status == HttpStatus.NOT_FOUND) {
            return new AiModelNotAvailableException(
                    apiError.message(), PROVIDER, model, status.value(), null);
        }
        AiProviderException.Reason reason =
                switch (status.value()) {
                    case 400 -> AiProviderException.Reason.INVALID_REQUEST;
                    case 401 -> AiProviderException.Reason.UNAUTHORIZED;
                    case 403 -> AiProviderException.Reason.FORBIDDEN;
                    case 429 -> AiProviderException.Reason.RATE_LIMIT;
                    default -> AiProviderException.Reason.SERVICE_UNAVAILABLE;
                };
        return new AiProviderException(
                reason,
                status.value(),
                PROVIDER,
                model,
                apiError.status(),
                apiError.message(),
                null);
    }

    private GeminiApiError parseApiError(String responseBody) {
        try {
            JsonNode error = OBJECT_MAPPER.readTree(responseBody).path("error");
            String message = AiErrorSanitizer.message(error.path("message").asText(null));
            String status = error.path("status").asText(null);
            JsonNode codeNode = error.path("code");
            Integer code = codeNode.canConvertToInt() ? codeNode.intValue() : null;
            return new GeminiApiError(code, status, message);
        } catch (RuntimeException | java.io.IOException exception) {
            return new GeminiApiError(
                    null, null, "Gemini API returned an unreadable error response");
        }
    }

    private Integer apiErrorCode(RestClientResponseException exception) {
        return parseApiError(exception.getResponseBodyAsString()).code();
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

    private record GeminiApiError(Integer code, String status, String message) {}
}
