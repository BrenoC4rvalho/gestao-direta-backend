package br.com.gestaodireta.ai.infrastructure.whisper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import br.com.gestaodireta.ai.transcription.AudioTranscriptionException;
import br.com.gestaodireta.ai.transcription.AudioTranscriptionProviderProperties;
import br.com.gestaodireta.ai.transcription.AudioTranscriptionRequest;
import java.net.SocketTimeoutException;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

class WhisperAudioTranscriptionClientTest {

    @Test
    void shouldSendOggAsMultipartAndReturnTranscription() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        WhisperAudioTranscriptionClient client = client(builder);
        server.expect(once(), requestTo("http://localhost:8090/transcribe"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.MULTIPART_FORM_DATA))
                .andExpect(
                        content()
                                .string(org.hamcrest.Matchers.containsString("telegram-voice.ogg")))
                .andRespond(
                        withSuccess(
                                "{\"text\":\"Gastei R$ 850 com diesel\",\"language\":\"pt\",\"durationSeconds\":4.8}",
                                MediaType.APPLICATION_JSON));

        assertThat(client.transcribe(request()).text()).isEqualTo("Gastei R$ 850 com diesel");
        server.verify();
    }

    @Test
    void shouldRejectEmptyOrInvalidResponse() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        WhisperAudioTranscriptionClient client = client(builder);
        server.expect(once(), requestTo("http://localhost:8090/transcribe"))
                .andRespond(withSuccess("{\"text\":\"\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.transcribe(request()))
                .isInstanceOfSatisfying(
                        AudioTranscriptionException.class,
                        exception ->
                                assertThat(exception.getReason())
                                        .isEqualTo(
                                                AudioTranscriptionException.Reason
                                                        .EMPTY_TRANSCRIPT));
    }

    @Test
    void shouldTranslateHttpAndTimeoutFailures() {
        RestClient.Builder errorBuilder = RestClient.builder();
        MockRestServiceServer errorServer = MockRestServiceServer.bindTo(errorBuilder).build();
        WhisperAudioTranscriptionClient errorClient = client(errorBuilder);
        errorServer
                .expect(once(), requestTo("http://localhost:8090/transcribe"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertReason(errorClient, AudioTranscriptionException.Reason.PROVIDER_ERROR);

        RestClient.Builder timeoutBuilder = RestClient.builder();
        MockRestServiceServer timeoutServer = MockRestServiceServer.bindTo(timeoutBuilder).build();
        WhisperAudioTranscriptionClient timeoutClient = client(timeoutBuilder);
        timeoutServer
                .expect(once(), requestTo("http://localhost:8090/transcribe"))
                .andRespond(
                        request -> {
                            throw new ResourceAccessException(
                                    "timed out", new SocketTimeoutException());
                        });

        assertReason(timeoutClient, AudioTranscriptionException.Reason.TIMEOUT);
    }

    @Test
    void shouldProbeWhisperHealthEndpoint() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        WhisperAudioTranscriptionClient client = client(builder);
        server.expect(once(), requestTo("http://localhost:8090/health"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(
                        withSuccess(
                                "{\"status\":\"UP\",\"model\":\"small\"}",
                                MediaType.APPLICATION_JSON));

        client.probe();
        server.verify();
    }

    private void assertReason(
            WhisperAudioTranscriptionClient client, AudioTranscriptionException.Reason reason) {
        assertThatThrownBy(() -> client.transcribe(request()))
                .isInstanceOfSatisfying(
                        AudioTranscriptionException.class,
                        exception -> assertThat(exception.getReason()).isEqualTo(reason));
    }

    private WhisperAudioTranscriptionClient client(RestClient.Builder builder) {
        AudioTranscriptionProviderProperties.Whisper properties =
                new AudioTranscriptionProviderProperties.Whisper();
        properties.setBaseUrl("http://localhost:8090");
        properties.setTimeout(Duration.ofSeconds(2));
        return new WhisperAudioTranscriptionClient(builder, properties, false);
    }

    private AudioTranscriptionRequest request() {
        return new AudioTranscriptionRequest(new byte[] {1, 2, 3}, "audio/ogg", "1", "2");
    }
}
