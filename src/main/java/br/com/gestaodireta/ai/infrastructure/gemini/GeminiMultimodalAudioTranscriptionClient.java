package br.com.gestaodireta.ai.infrastructure.gemini;

import br.com.gestaodireta.ai.transcription.AudioTranscriptionClient;
import br.com.gestaodireta.ai.transcription.AudioTranscriptionException;
import br.com.gestaodireta.ai.transcription.AudioTranscriptionProperties;
import br.com.gestaodireta.ai.transcription.AudioTranscriptionRequest;
import br.com.gestaodireta.ai.transcription.AudioTranscriptionResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

public class GeminiMultimodalAudioTranscriptionClient implements AudioTranscriptionClient {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(GeminiMultimodalAudioTranscriptionClient.class);
    private static final String BASE_URL = "https://generativelanguage.googleapis.com";
    private static final String FILES_UPLOAD_ENDPOINT = "/upload/v1beta/files";
    private static final String PROMPT =
            "Transcreva o conteúdo falado neste áudio.\n"
                    + "Retorne apenas a transcrição, sem comentários adicionais.\n"
                    + "O idioma esperado é português do Brasil.";

    private final RestClient restClient;
    private final AudioTranscriptionProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final GeminiTranscriptionDebugResponseSaver debugResponseSaver;

    public GeminiMultimodalAudioTranscriptionClient(
            RestClient.Builder builder,
            GeminiAiProperties geminiProperties,
            AudioTranscriptionProperties properties) {
        this(builder, geminiProperties, properties, false);
    }

    public GeminiMultimodalAudioTranscriptionClient(
            RestClient.Builder builder,
            GeminiAiProperties geminiProperties,
            AudioTranscriptionProperties properties,
            boolean configureTimeout) {
        this.properties = properties;
        RestClient.Builder clientBuilder =
                builder.baseUrl(BASE_URL)
                        .defaultHeader("x-goog-api-key", geminiProperties.getApiKey());
        if (configureTimeout) {
            clientBuilder.requestFactory(requestFactory(properties.getTimeoutSeconds()));
        }
        restClient = clientBuilder.build();
        debugResponseSaver = new GeminiTranscriptionDebugResponseSaver(properties);
    }

    @Override
    public AudioTranscriptionResult transcribe(AudioTranscriptionRequest request) {
        long startNanos = System.nanoTime();
        String fileName = null;
        try {
            UploadedFile uploaded = upload(request, startNanos);
            fileName = uploaded.name();
            LOGGER.info(
                    "gemini audio transcription request: model={} mimeType={} bytes={} timeoutSeconds={}",
                    properties.getModel(),
                    uploaded.mimeType(),
                    request.audioBytes().length,
                    properties.getTimeoutSeconds());
            ResponseEntity<String> response =
                    restClient
                            .post()
                            .uri("/v1beta/models/{model}:generateContent", properties.getModel())
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(requestBody(uploaded))
                            .retrieve()
                            .toEntity(String.class);
            String responseBody = response.getBody();
            debugResponseSaver.save(request, responseBody);
            ResponseDetails details = inspectResponse(parseResponse(responseBody));
            LOGGER.info(
                    "gemini audio transcription completed: model={} httpStatus={} characters={} inputTokens={} outputTokens={} elapsedMs={}",
                    properties.getModel(),
                    response.getStatusCode().value(),
                    details.text() == null ? null : details.text().length(),
                    details.inputTokens(),
                    details.outputTokens(),
                    elapsedMillis(startNanos));
            if (details.text() == null || details.text().isBlank()) {
                throw new AudioTranscriptionException(
                        AudioTranscriptionException.Reason.EMPTY_TRANSCRIPT,
                        "Gemini returned an empty transcript",
                        null);
            }
            return new AudioTranscriptionResult(details.text().trim(), null, null);
        } catch (AudioTranscriptionException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            throw providerError(exception, request, startNanos, "GENERATE_CONTENT");
        } catch (ResourceAccessException exception) {
            AudioTranscriptionException.Reason reason =
                    hasTimeoutCause(exception)
                            ? AudioTranscriptionException.Reason.TIMEOUT
                            : AudioTranscriptionException.Reason.PROVIDER_ERROR;
            LOGGER.warn(
                    "gemini audio transcription failed: model={} reason={} elapsedMs={}",
                    properties.getModel(),
                    reason,
                    elapsedMillis(startNanos));
            throw new AudioTranscriptionException(
                    reason, "Gemini transcription could not be reached", exception);
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "gemini audio transcription failed: model={} reason=INVALID_RESPONSE elapsedMs={}",
                    properties.getModel(),
                    elapsedMillis(startNanos));
            throw new AudioTranscriptionException(
                    AudioTranscriptionException.Reason.INVALID_RESPONSE,
                    "Gemini returned an invalid transcription response",
                    exception);
        } finally {
            deleteFile(fileName);
        }
    }

    @Override
    public String providerName() {
        return "gemini";
    }

    @Override
    public String modelName() {
        return properties.getModel();
    }

    private UploadedFile upload(AudioTranscriptionRequest request, long startNanos) {
        byte[] audio = request.audioBytes();
        String mimeType = request.mimeType();
        ResponseEntity<Void> start = startUpload(audio, mimeType, request, startNanos);
        String uploadUrl = start.getHeaders().getFirst("X-Goog-Upload-URL");
        if (uploadUrl == null || uploadUrl.isBlank()) {
            throw new AudioTranscriptionException(
                    AudioTranscriptionException.Reason.INVALID_RESPONSE,
                    "Gemini did not provide an upload URL",
                    null);
        }
        ResponseEntity<JsonNode> finalized =
                finalizeUpload(uploadUrl, audio, mimeType, request, startNanos);
        JsonNode file = finalized.getBody() == null ? null : finalized.getBody().path("file");
        String name = textValue(file, "name");
        String uri = textValue(file, "uri");
        String returnedMimeType = textValue(file, "mimeType");
        LOGGER.info(
                "gemini audio upload completed: httpStatus={} fileNamePresent={} fileUriPresent={} fileUriLength={} mimeTypeSent={} mimeTypeReturned={} fileSize={} fileState={} elapsedMs={}",
                finalized.getStatusCode().value(),
                name != null && !name.isBlank(),
                uri != null && !uri.isBlank(),
                uri == null ? null : uri.length(),
                mimeType,
                returnedMimeType,
                longValue(file, "sizeBytes"),
                textValue(file, "state"),
                elapsedMillis(startNanos));
        if (name == null || name.isBlank() || uri == null || uri.isBlank()) {
            throw new AudioTranscriptionException(
                    AudioTranscriptionException.Reason.INVALID_RESPONSE,
                    "Gemini did not return uploaded file metadata",
                    null);
        }
        return new UploadedFile(name, uri, firstNonBlank(returnedMimeType, mimeType));
    }

    private ResponseEntity<Void> startUpload(
            byte[] audio, String mimeType, AudioTranscriptionRequest request, long startNanos) {
        try {
            return restClient
                    .post()
                    .uri(FILES_UPLOAD_ENDPOINT)
                    .header("X-Goog-Upload-Protocol", "resumable")
                    .header("X-Goog-Upload-Command", "start")
                    .header("X-Goog-Upload-Header-Content-Length", String.valueOf(audio.length))
                    .header("X-Goog-Upload-Header-Content-Type", mimeType)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("file", Map.of("display_name", "telegram-voice")))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            throw providerError(exception, request, startNanos, "FILES_UPLOAD_START");
        }
    }

    private ResponseEntity<JsonNode> finalizeUpload(
            String uploadUrl,
            byte[] audio,
            String mimeType,
            AudioTranscriptionRequest request,
            long startNanos) {
        try {
            return restClient
                    .post()
                    .uri(uploadUrl)
                    .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(audio.length))
                    .header("X-Goog-Upload-Offset", "0")
                    .header("X-Goog-Upload-Command", "upload, finalize")
                    .contentType(MediaType.parseMediaType(mimeType))
                    .body(audio)
                    .retrieve()
                    .toEntity(JsonNode.class);
        } catch (RestClientResponseException exception) {
            throw providerError(exception, request, startNanos, "FILES_UPLOAD_FINALIZE");
        }
    }

    private Map<String, Object> requestBody(UploadedFile file) {
        return Map.of(
                "contents",
                List.of(
                        Map.of(
                                "parts",
                                List.of(
                                        Map.of("text", PROMPT),
                                        Map.of(
                                                "file_data",
                                                Map.of(
                                                        "mime_type", file.mimeType(),
                                                        "file_uri", file.uri()))))));
    }

    static ResponseDetails inspectResponse(JsonNode response) {
        List<String> textParts = new ArrayList<>();
        JsonNode parts =
                response == null
                        ? null
                        : response.path("candidates").path(0).path("content").path("parts");
        if (parts != null && parts.isArray()) {
            for (JsonNode part : parts) {
                String text = textValue(part, "text");
                if (text != null && !text.isBlank()) {
                    textParts.add(text);
                }
            }
        }
        JsonNode usage = response == null ? null : response.path("usageMetadata");
        return new ResponseDetails(
                String.join("\n", textParts),
                longValue(usage, "promptTokenCount"),
                longValue(usage, "candidatesTokenCount"));
    }

    private JsonNode parseResponse(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(responseBody);
        } catch (Exception exception) {
            throw new AudioTranscriptionException(
                    AudioTranscriptionException.Reason.INVALID_RESPONSE,
                    "Gemini returned a non-JSON transcription response",
                    exception);
        }
    }

    private AudioTranscriptionException providerError(
            RestClientResponseException exception,
            AudioTranscriptionRequest request,
            long startNanos,
            String stage) {
        String responseBody = exception.getResponseBodyAsString();
        debugResponseSaver.save(request, responseBody);
        JsonNode error = parseError(responseBody);
        LOGGER.warn(
                "gemini audio transcription failed: stage={} model={} httpStatus={} apiErrorCode={} apiErrorStatus={} apiErrorMessage={} elapsedMs={}",
                stage,
                properties.getModel(),
                exception.getStatusCode().value(),
                longValue(error, "code"),
                textValue(error, "status"),
                textValue(error, "message"),
                elapsedMillis(startNanos));
        return new AudioTranscriptionException(
                AudioTranscriptionException.Reason.PROVIDER_ERROR,
                "Gemini rejected the transcription request",
                exception);
    }

    private JsonNode parseError(String responseBody) {
        try {
            JsonNode root = parseResponse(responseBody);
            return root == null ? null : root.path("error");
        } catch (AudioTranscriptionException exception) {
            return null;
        }
    }

    private void deleteFile(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return;
        }
        try {
            restClient.delete().uri("/v1beta/" + fileName).retrieve().toBodilessEntity();
        } catch (RuntimeException exception) {
            LOGGER.warn("Gemini uploaded audio cleanup failed: fileName={}", fileName);
        }
    }

    private SimpleClientHttpRequestFactory requestFactory(int timeoutSeconds) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(timeoutSeconds));
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        return factory;
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

    private static String textValue(JsonNode node, String field) {
        return node == null ? null : node.path(field).asText(null);
    }

    private static Long longValue(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.path(field);
        if (value == null || value.isMissingNode() || value.isNull()) {
            return null;
        }
        return value.canConvertToLong() ? value.asLong() : null;
    }

    private String firstNonBlank(String preferred, String fallback) {
        return preferred == null || preferred.isBlank() ? fallback : preferred;
    }

    private long elapsedMillis(long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
    }

    private record UploadedFile(String name, String uri, String mimeType) {}

    record ResponseDetails(String text, Long inputTokens, Long outputTokens) {}
}
