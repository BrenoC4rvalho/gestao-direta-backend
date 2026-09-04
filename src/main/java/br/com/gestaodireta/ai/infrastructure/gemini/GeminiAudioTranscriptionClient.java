package br.com.gestaodireta.ai.infrastructure.gemini;

import br.com.gestaodireta.ai.transcription.AudioTranscriptionClient;
import br.com.gestaodireta.ai.transcription.AudioTranscriptionException;
import br.com.gestaodireta.ai.transcription.AudioTranscriptionProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
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

public class GeminiAudioTranscriptionClient implements AudioTranscriptionClient {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(GeminiAudioTranscriptionClient.class);
    private static final String BASE_URL = "https://generativelanguage.googleapis.com";

    private final RestClient restClient;
    private final AudioTranscriptionProperties properties;

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
        this.restClient = clientBuilder.build();
    }

    @Override
    public String transcribe(byte[] audio, String mimeType) {
        long startNanos = System.nanoTime();
        String fileName = null;
        try {
            UploadedFile uploaded = upload(audio, mimeType);
            fileName = uploaded.name();
            String transcript = createInteraction(uploaded.uri(), mimeType);
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
            throw providerError(exception, startNanos);
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

    private UploadedFile upload(byte[] audio, String mimeType) {
        ResponseEntity<Void> startResponse =
                restClient
                        .post()
                        .uri("/upload/v1beta/files")
                        .header("X-Goog-Upload-Protocol", "resumable")
                        .header("X-Goog-Upload-Command", "start")
                        .header("X-Goog-Upload-Header-Content-Length", String.valueOf(audio.length))
                        .header("X-Goog-Upload-Header-Content-Type", mimeType)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("file", Map.of("display_name", "telegram-voice")))
                        .retrieve()
                        .toBodilessEntity();
        String uploadUrl = startResponse.getHeaders().getFirst("X-Goog-Upload-URL");
        if (uploadUrl == null || uploadUrl.isBlank()) {
            throw new AudioTranscriptionException(
                    AudioTranscriptionException.Reason.INVALID_RESPONSE,
                    "Gemini did not provide an upload URL",
                    null);
        }
        JsonNode response =
                restClient
                        .post()
                        .uri(uploadUrl)
                        .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(audio.length))
                        .header("X-Goog-Upload-Offset", "0")
                        .header("X-Goog-Upload-Command", "upload, finalize")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .body(audio)
                        .retrieve()
                        .body(JsonNode.class);
        JsonNode file = response == null ? null : response.path("file");
        String name = file == null ? null : file.path("name").asText(null);
        String uri = file == null ? null : file.path("uri").asText(null);
        if (name == null || name.isBlank() || uri == null || uri.isBlank()) {
            throw new AudioTranscriptionException(
                    AudioTranscriptionException.Reason.INVALID_RESPONSE,
                    "Gemini did not return uploaded file metadata",
                    null);
        }
        return new UploadedFile(name, uri);
    }

    private String createInteraction(String uri, String mimeType) {
        Map<String, Object> transcriptionConfig =
                properties.getLanguage() == null || properties.getLanguage().isBlank()
                        ? Map.of("mode", properties.getMode(), "language_codes", List.of())
                        : Map.of(
                                "mode",
                                properties.getMode(),
                                "language_codes",
                                List.of(properties.getLanguage().trim()));
        JsonNode response =
                restClient
                        .post()
                        .uri("/v1beta/interactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(
                                Map.of(
                                        "model",
                                        properties.getModel(),
                                        "input",
                                        List.of(
                                                Map.of(
                                                        "type",
                                                        "audio",
                                                        "uri",
                                                        uri,
                                                        "mime_type",
                                                        mimeType)),
                                        "generation_config",
                                        Map.of("transcription_config", transcriptionConfig)))
                        .retrieve()
                        .body(JsonNode.class);
        String outputText = response == null ? null : response.path("output_text").asText(null);
        if (outputText != null && !outputText.isBlank()) {
            return outputText;
        }
        if (response != null && response.path("outputs").isArray()) {
            for (JsonNode output : response.path("outputs")) {
                if ("text".equals(output.path("type").asText())) {
                    String text = output.path("text").asText(null);
                    if (text != null && !text.isBlank()) {
                        return text;
                    }
                }
            }
        }
        return null;
    }

    private void deleteFile(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return;
        }
        try {
            restClient.delete().uri("/v1beta/" + fileName).retrieve().toBodilessEntity();
        } catch (RuntimeException exception) {
            LOGGER.warn("Gemini uploaded audio cleanup failed: provider=gemini");
        }
    }

    private AudioTranscriptionException providerError(
            RestClientResponseException exception, long startNanos) {
        LOGGER.warn(
                "audio transcription failed: provider=gemini model={} status={} elapsedMs={}",
                properties.getModel(),
                exception.getStatusCode().value(),
                elapsedMillis(startNanos));
        return new AudioTranscriptionException(
                AudioTranscriptionException.Reason.PROVIDER_ERROR,
                "Gemini rejected the transcription request",
                exception);
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

    private long elapsedMillis(long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
    }

    private record UploadedFile(String name, String uri) {}
}
