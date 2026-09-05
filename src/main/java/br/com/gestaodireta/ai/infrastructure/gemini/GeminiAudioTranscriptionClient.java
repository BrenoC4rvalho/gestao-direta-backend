package br.com.gestaodireta.ai.infrastructure.gemini;

import br.com.gestaodireta.ai.transcription.AudioTranscriptionClient;
import br.com.gestaodireta.ai.transcription.AudioTranscriptionException;
import br.com.gestaodireta.ai.transcription.AudioTranscriptionProperties;
import br.com.gestaodireta.ai.transcription.AudioTranscriptionRequest;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

public class GeminiAudioTranscriptionClient implements AudioTranscriptionClient {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(GeminiAudioTranscriptionClient.class);
    private static final String BASE_URL = "https://generativelanguage.googleapis.com";
    private static final String FILES_UPLOAD_ENDPOINT = "/upload/v1beta/files";
    private static final String INTERACTIONS_ENDPOINT = "/v1beta/interactions";
    private static final String STRATEGY = "gemini-transcribe";

    private final RestClient restClient;
    private final AudioTranscriptionProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final GeminiTranscriptionDebugResponseSaver debugResponseSaver;

    public GeminiAudioTranscriptionClient(
            RestClient.Builder builder,
            GeminiAiProperties geminiProperties,
            AudioTranscriptionProperties properties) {
        this(builder, geminiProperties, properties, false);
    }

    public GeminiAudioTranscriptionClient(
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
    public String transcribe(AudioTranscriptionRequest request) {
        long startNanos = System.nanoTime();
        String fileName = null;
        try {
            UploadedFile uploaded = upload(request, startNanos);
            fileName = uploaded.name();
            String transcript = createInteraction(request, uploaded, startNanos);
            if (transcript == null || transcript.isBlank()) {
                throw new AudioTranscriptionException(
                        AudioTranscriptionException.Reason.EMPTY_TRANSCRIPT,
                        "Gemini returned an empty transcript",
                        null);
            }
            LOGGER.info(
                    "audio transcription completed: provider=gemini model={} characters={} elapsedMs={}",
                    properties.getModel(),
                    transcript.length(),
                    elapsedMillis(startNanos));
            return transcript.trim();
        } catch (AudioTranscriptionException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            throw providerError(exception, startNanos, request, "UNKNOWN");
        } catch (ResourceAccessException exception) {
            AudioTranscriptionException.Reason reason =
                    hasTimeoutCause(exception)
                            ? AudioTranscriptionException.Reason.TIMEOUT
                            : AudioTranscriptionException.Reason.PROVIDER_ERROR;
            LOGGER.warn(
                    "audio transcription failed: provider=gemini model={} reason={} elapsedMs={}",
                    properties.getModel(),
                    reason,
                    elapsedMillis(startNanos));
            throw new AudioTranscriptionException(
                    reason, "Gemini transcription could not be reached", exception);
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "audio transcription failed: provider=gemini model={} reason=INVALID_RESPONSE elapsedMs={}",
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

    private UploadedFile upload(AudioTranscriptionRequest request, long startNanos) {
        byte[] audio = request.audioBytes();
        String mimeType = request.mimeType();
        ResponseEntity<Void> startResponse = startUpload(audio, mimeType, request, startNanos);
        LOGGER.info(
                "gemini transcription upload started: endpoint={} httpStatus={} mimeType={} bytes={}",
                FILES_UPLOAD_ENDPOINT,
                startResponse.getStatusCode().value(),
                mimeType,
                audio.length);
        String uploadUrl = startResponse.getHeaders().getFirst("X-Goog-Upload-URL");
        if (uploadUrl == null || uploadUrl.isBlank()) {
            throw new AudioTranscriptionException(
                    AudioTranscriptionException.Reason.INVALID_RESPONSE,
                    "Gemini did not provide an upload URL",
                    null);
        }
        ResponseEntity<JsonNode> finalizeResponse =
                finalizeUpload(uploadUrl, audio, mimeType, request, startNanos);
        JsonNode file =
                finalizeResponse.getBody() == null ? null : finalizeResponse.getBody().path("file");
        String name = textValue(file, "name");
        String uri = textValue(file, "uri");
        String returnedMimeType = textValue(file, "mimeType");
        Long fileSize = longValue(file, "sizeBytes");
        String fileState = textValue(file, "state");
        LOGGER.info(
                "gemini audio upload completed: httpStatus={} fileNamePresent={} fileUriPresent={} fileUriLength={} mimeTypeSent={} mimeTypeReturned={} fileSize={} fileState={} elapsedMs={}",
                finalizeResponse.getStatusCode().value(),
                name != null && !name.isBlank(),
                uri != null && !uri.isBlank(),
                uri == null ? null : uri.length(),
                mimeType,
                returnedMimeType,
                fileSize,
                fileState,
                elapsedMillis(startNanos));
        LOGGER.debug("gemini audio upload uri: fileUriPrefix={}", uriPrefix(uri));
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
                    .body(new UploadStartRequest(new FileMetadata("telegram-voice")))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            throw providerError(exception, startNanos, request, "FILES_UPLOAD_START");
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
            throw providerError(exception, startNanos, request, "FILES_UPLOAD_FINALIZE");
        }
    }

    private String createInteraction(
            AudioTranscriptionRequest transcriptionRequest,
            UploadedFile uploaded,
            long startNanos) {
        InteractionRequest request =
                new InteractionRequest(
                        properties.getModel(),
                        List.of(new AudioInput("audio", uploaded.uri(), uploaded.mimeType())),
                        generationConfig());
        logInteractionRequest(uploaded, request);
        ResponseEntity<String> responseEntity;
        try {
            responseEntity =
                    restClient
                            .post()
                            .uri(INTERACTIONS_ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(request)
                            .retrieve()
                            .toEntity(String.class);
        } catch (RestClientResponseException exception) {
            throw providerError(exception, startNanos, transcriptionRequest, "INTERACTIONS");
        }
        String responseBody = responseEntity.getBody();
        debugResponseSaver.save(transcriptionRequest, STRATEGY, responseBody);
        InteractionResponseDetails details =
                inspectInteractionResponse(parseResponse(responseBody));
        LOGGER.info(
                "gemini transcription response: httpStatus={} interactionId={} status={} state={} outputTextStatus={} outputTextPresent={} outputTextLength={} stepsCount={} usageInputTokens={} usageOutputTokens={} elapsedMs={}",
                responseEntity.getStatusCode().value(),
                details.interactionId(),
                details.status(),
                details.state(),
                details.outputTextStatus(),
                details.outputTextStatus() == OutputTextStatus.PRESENT,
                details.outputText() == null ? null : details.outputText().length(),
                details.stepsCount(),
                details.usageInputTokens(),
                details.usageOutputTokens(),
                elapsedMillis(startNanos));
        return details.outputText();
    }

    private void logInteractionRequest(UploadedFile uploaded, InteractionRequest request) {
        String mode = trimToNull(properties.getMode());
        String language = trimToNull(properties.getLanguage());
        LOGGER.info(
                "gemini transcription request: model={} endpoint=interactions fileUriPresent={} mimeType={} mode={} language={} timeoutSeconds={}",
                request.model(),
                uploaded.uri() != null && !uploaded.uri().isBlank(),
                uploaded.mimeType(),
                mode == null ? "omitted" : mode,
                language == null ? "auto" : language,
                properties.getTimeoutSeconds());
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

    static InteractionResponseDetails inspectInteractionResponse(JsonNode response) {
        JsonNode outputText = response == null ? null : response.get("output_text");
        String value = outputText == null || outputText.isNull() ? null : outputText.asText(null);
        OutputTextStatus outputTextStatus =
                outputText == null
                        ? OutputTextStatus.MISSING
                        : value == null || value.isBlank()
                                ? OutputTextStatus.EMPTY
                                : OutputTextStatus.PRESENT;
        return new InteractionResponseDetails(
                textValue(response, "id"),
                textValue(response, "status"),
                textValue(response, "state"),
                value,
                outputTextStatus,
                arraySize(response, "steps"),
                longValue(response, "usage", "total_input_tokens"),
                longValue(response, "usage", "total_output_tokens"));
    }

    private GenerationConfig generationConfig() {
        String mode = trimToNull(properties.getMode());
        String language = trimToNull(properties.getLanguage());
        if (mode == null && language == null) {
            return null;
        }
        List<String> languageCodes = language == null ? null : List.of(language);
        return new GenerationConfig(new TranscriptionConfig(mode, languageCodes));
    }

    private void deleteFile(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return;
        }
        try {
            ResponseEntity<Void> response =
                    restClient.delete().uri("/v1beta/" + fileName).retrieve().toBodilessEntity();
            LOGGER.info(
                    "gemini transcription upload cleanup completed: httpStatus={} fileName={}",
                    response.getStatusCode().value(),
                    fileName);
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Gemini uploaded audio cleanup failed: provider=gemini fileName={}", fileName);
        }
    }

    private AudioTranscriptionException providerError(
            RestClientResponseException exception,
            long startNanos,
            AudioTranscriptionRequest request,
            String stage) {
        String responseBody = exception.getResponseBodyAsString();
        debugResponseSaver.save(request, STRATEGY, responseBody);
        JsonNode error = parseError(responseBody);
        LOGGER.warn(
                "gemini transcription http error: stage={} httpStatus={} apiErrorCode={} apiErrorStatus={} apiErrorMessage={} elapsedMs={}",
                stage,
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

    private static int arraySize(JsonNode response, String field) {
        JsonNode value = response == null ? null : response.path(field);
        return value != null && value.isArray() ? value.size() : 0;
    }

    private static Long longValue(JsonNode response, String field) {
        JsonNode value = response == null ? null : response.path(field);
        if (value == null || value.isMissingNode() || value.isNull()) {
            return null;
        }
        if (value.canConvertToLong()) {
            return value.asLong();
        }
        try {
            return Long.valueOf(value.asText());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static Long longValue(JsonNode response, String parent, String field) {
        return longValue(response == null ? null : response.path(parent), field);
    }

    private static String textValue(JsonNode response, String field) {
        return response == null ? null : response.path(field).asText(null);
    }

    private String firstNonBlank(String preferred, String fallback) {
        return preferred == null || preferred.isBlank() ? fallback : preferred;
    }

    private String uriPrefix(String uri) {
        if (uri == null || uri.isBlank()) {
            return null;
        }
        return uri.substring(0, Math.min(uri.length(), 32));
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private long elapsedMillis(long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record UploadStartRequest(FileMetadata file) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record FileMetadata(@JsonProperty("display_name") String displayName) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record InteractionRequest(
            String model,
            List<AudioInput> input,
            @JsonProperty("generation_config") GenerationConfig generationConfig) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record AudioInput(
            String type, String uri, @JsonProperty("mime_type") String mimeType) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record GenerationConfig(
            @JsonProperty("transcription_config") TranscriptionConfig transcriptionConfig) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record TranscriptionConfig(
            String mode, @JsonProperty("language_codes") List<String> languageCodes) {}

    private record UploadedFile(String name, String uri, String mimeType) {}

    enum OutputTextStatus {
        PRESENT,
        EMPTY,
        MISSING
    }

    record InteractionResponseDetails(
            String interactionId,
            String status,
            String state,
            String outputText,
            OutputTextStatus outputTextStatus,
            int stepsCount,
            Long usageInputTokens,
            Long usageOutputTokens) {}
}
