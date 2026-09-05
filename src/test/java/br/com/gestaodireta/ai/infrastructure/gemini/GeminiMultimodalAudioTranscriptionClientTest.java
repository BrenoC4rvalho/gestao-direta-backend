package br.com.gestaodireta.ai.infrastructure.gemini;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import br.com.gestaodireta.ai.transcription.AudioTranscriptionException;
import br.com.gestaodireta.ai.transcription.AudioTranscriptionProperties;
import br.com.gestaodireta.ai.transcription.AudioTranscriptionRequest;
import java.net.SocketTimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

class GeminiMultimodalAudioTranscriptionClientTest {

    @Test
    void shouldUploadOggCallGenerateContentAndReturnText() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GeminiMultimodalAudioTranscriptionClient client = client(builder);

        expectUpload(server);
        server.expect(
                        once(),
                        requestTo(
                                "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(
                        jsonPath("$.contents[0].parts[0].text")
                                .value(
                                        org.hamcrest.Matchers.containsString(
                                                "Transcreva o conteúdo falado")))
                .andExpect(
                        jsonPath("$.contents[0].parts[1].file_data.mime_type").value("audio/ogg"))
                .andExpect(
                        jsonPath("$.contents[0].parts[1].file_data.file_uri")
                                .value("gemini://voice-1"))
                .andExpect(jsonPath("$.generation_config").doesNotExist())
                .andRespond(
                        withSuccess(
                                response("Gastei R$ 850 com diesel hoje."),
                                MediaType.APPLICATION_JSON));
        expectCleanup(server);

        assertThat(client.transcribe(request())).isEqualTo("Gastei R$ 850 com diesel hoje.");
        server.verify();
    }

    @Test
    void shouldRejectEmptyMultimodalText() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GeminiMultimodalAudioTranscriptionClient client = client(builder);

        expectUpload(server);
        server.expect(once(), requestTo(org.hamcrest.Matchers.containsString("generateContent")))
                .andRespond(withSuccess("{\"candidates\":[]}", MediaType.APPLICATION_JSON));
        expectCleanup(server);

        assertThatThrownBy(() -> client.transcribe(request()))
                .isInstanceOfSatisfying(
                        AudioTranscriptionException.class,
                        exception ->
                                assertThat(exception.getReason())
                                        .isEqualTo(
                                                AudioTranscriptionException.Reason
                                                        .EMPTY_TRANSCRIPT));
        server.verify();
    }

    @Test
    void shouldClassifyGenerateContentHttpErrorAsProviderError() {
        assertProviderError(HttpStatus.BAD_REQUEST);
        assertProviderError(HttpStatus.SERVICE_UNAVAILABLE);
    }

    private void assertProviderError(HttpStatus status) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GeminiMultimodalAudioTranscriptionClient client = client(builder);

        expectUpload(server);
        server.expect(once(), requestTo(org.hamcrest.Matchers.containsString("generateContent")))
                .andRespond(withStatus(status));
        expectCleanup(server);

        assertThatThrownBy(() -> client.transcribe(request()))
                .isInstanceOfSatisfying(
                        AudioTranscriptionException.class,
                        exception ->
                                assertThat(exception.getReason())
                                        .isEqualTo(
                                                AudioTranscriptionException.Reason.PROVIDER_ERROR));
        server.verify();
    }

    @Test
    void shouldClassifyTimeout() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GeminiMultimodalAudioTranscriptionClient client = client(builder);

        expectUpload(server);
        server.expect(once(), requestTo(org.hamcrest.Matchers.containsString("generateContent")))
                .andRespond(
                        request -> {
                            throw new ResourceAccessException(
                                    "Timed out", new SocketTimeoutException());
                        });
        expectCleanup(server);

        assertThatThrownBy(() -> client.transcribe(request()))
                .isInstanceOfSatisfying(
                        AudioTranscriptionException.class,
                        exception ->
                                assertThat(exception.getReason())
                                        .isEqualTo(AudioTranscriptionException.Reason.TIMEOUT));
        server.verify();
    }

    private GeminiMultimodalAudioTranscriptionClient client(RestClient.Builder builder) {
        GeminiAiProperties geminiProperties = new GeminiAiProperties();
        geminiProperties.setApiKey("test-key");
        AudioTranscriptionProperties properties = new AudioTranscriptionProperties();
        properties.setModel("gemini-3.1-flash-lite");
        return new GeminiMultimodalAudioTranscriptionClient(builder, geminiProperties, properties);
    }

    private void expectUpload(MockRestServiceServer server) {
        server.expect(
                        once(),
                        requestTo("https://generativelanguage.googleapis.com/upload/v1beta/files"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "test-key"))
                .andExpect(header("X-Goog-Upload-Header-Content-Type", "audio/ogg"))
                .andRespond(
                        withStatus(HttpStatus.OK).header("X-Goog-Upload-URL", "/upload-session"));
        server.expect(once(), requestTo("https://generativelanguage.googleapis.com/upload-session"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.valueOf("audio/ogg")))
                .andExpect(content().bytes(new byte[] {1, 2, 3}))
                .andRespond(
                        withSuccess(
                                "{\"file\":{\"name\":\"files/voice-1\",\"uri\":\"gemini://voice-1\",\"mimeType\":\"audio/ogg\"}}",
                                MediaType.APPLICATION_JSON));
    }

    private void expectCleanup(MockRestServiceServer server) {
        server.expect(
                        once(),
                        requestTo("https://generativelanguage.googleapis.com/v1beta/files/voice-1"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withSuccess());
    }

    private AudioTranscriptionRequest request() {
        return new AudioTranscriptionRequest(new byte[] {1, 2, 3}, "audio/ogg", "519749095", "391");
    }

    private String response(String text) {
        return "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"%s\"}]}}],\"usageMetadata\":{\"promptTokenCount\":10,\"candidatesTokenCount\":9}}"
                .formatted(text);
    }
}
