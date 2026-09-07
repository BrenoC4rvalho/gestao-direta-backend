package br.com.gestaodireta.ai.infrastructure.whisper;

import br.com.gestaodireta.ai.transcription.AudioTranscriptionClient;
import br.com.gestaodireta.ai.transcription.AudioTranscriptionException;
import br.com.gestaodireta.ai.transcription.AudioTranscriptionProviderProperties;
import br.com.gestaodireta.ai.transcription.AudioTranscriptionRequest;
import br.com.gestaodireta.ai.transcription.AudioTranscriptionResult;
import java.net.SocketTimeoutException;
import java.time.Duration;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

public class WhisperAudioTranscriptionClient implements AudioTranscriptionClient {

    private static final String PROVIDER = "whisper";

    private final RestClient restClient;
    private final RestClient healthRestClient;
    private final String baseUrl;

    public WhisperAudioTranscriptionClient(
            RestClient.Builder builder, AudioTranscriptionProviderProperties.Whisper properties) {
        this(builder, properties, true);
    }

    public WhisperAudioTranscriptionClient(
            RestClient.Builder builder,
            AudioTranscriptionProviderProperties.Whisper properties,
            boolean configureTimeout) {
        baseUrl = properties.getBaseUrl();
        RestClient.Builder clientBuilder = builder.baseUrl(baseUrl);
        if (configureTimeout) {
            clientBuilder.requestFactory(requestFactory(properties.getTimeout()));
        }
        restClient = clientBuilder.build();
        healthRestClient = restClient;
    }

    @Override
    public AudioTranscriptionResult transcribe(AudioTranscriptionRequest request) {
        try {
            WhisperTranscriptionResponse response =
                    restClient
                            .post()
                            .uri("/transcribe")
                            .contentType(MediaType.MULTIPART_FORM_DATA)
                            .body(multipartBody(request))
                            .retrieve()
                            .body(WhisperTranscriptionResponse.class);
            if (response == null || response.text() == null || response.text().isBlank()) {
                throw new AudioTranscriptionException(
                        AudioTranscriptionException.Reason.EMPTY_TRANSCRIPT,
                        "Whisper returned an empty transcript",
                        null);
            }
            return new AudioTranscriptionResult(
                    response.text().trim(), response.language(), response.durationSeconds());
        } catch (AudioTranscriptionException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            throw new AudioTranscriptionException(
                    AudioTranscriptionException.Reason.PROVIDER_ERROR,
                    "Whisper rejected the transcription request",
                    exception);
        } catch (ResourceAccessException exception) {
            throw new AudioTranscriptionException(
                    hasTimeoutCause(exception)
                            ? AudioTranscriptionException.Reason.TIMEOUT
                            : AudioTranscriptionException.Reason.PROVIDER_ERROR,
                    "Whisper transcription service could not be reached",
                    exception);
        } catch (RuntimeException exception) {
            throw new AudioTranscriptionException(
                    AudioTranscriptionException.Reason.INVALID_RESPONSE,
                    "Whisper returned an invalid transcription response",
                    exception);
        }
    }

    @Override
    public String providerName() {
        return PROVIDER;
    }

    @Override
    public String modelName() {
        return "local";
    }

    @Override
    public void probe() {
        try {
            WhisperHealthResponse response =
                    healthRestClient
                            .get()
                            .uri("/health")
                            .retrieve()
                            .body(WhisperHealthResponse.class);
            if (response == null || !"UP".equalsIgnoreCase(response.status())) {
                throw new AudioTranscriptionException(
                        AudioTranscriptionException.Reason.PROVIDER_ERROR,
                        "Whisper health check returned an unavailable status",
                        null);
            }
        } catch (AudioTranscriptionException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            throw new AudioTranscriptionException(
                    AudioTranscriptionException.Reason.PROVIDER_ERROR,
                    "Whisper health check failed",
                    exception);
        } catch (ResourceAccessException exception) {
            throw new AudioTranscriptionException(
                    hasTimeoutCause(exception)
                            ? AudioTranscriptionException.Reason.TIMEOUT
                            : AudioTranscriptionException.Reason.PROVIDER_ERROR,
                    "Whisper health service could not be reached",
                    exception);
        }
    }

    private MultiValueMap<String, Object> multipartBody(AudioTranscriptionRequest request) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new AudioResource(request.audioBytes(), extension(request.mimeType())));
        return body;
    }

    private String extension(String mimeType) {
        return "audio/opus".equalsIgnoreCase(mimeType) ? "opus" : "ogg";
    }

    private SimpleClientHttpRequestFactory requestFactory(Duration timeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        return factory;
    }

    private boolean hasTimeoutCause(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof SocketTimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static class AudioResource extends ByteArrayResource {

        private final String filename;

        private AudioResource(byte[] bytes, String extension) {
            super(bytes);
            filename = "telegram-voice." + extension;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }

    record WhisperTranscriptionResponse(String text, String language, Double durationSeconds) {}

    record WhisperHealthResponse(String status, String model) {}
}
