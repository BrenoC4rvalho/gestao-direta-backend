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
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GeminiAudioTranscriptionClientTest {

    @TempDir Path tempDir;

    @Test
    void shouldUploadOggCreateMinimalInteractionAndReadOutputText() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GeminiAudioTranscriptionClient client =
                new GeminiAudioTranscriptionClient(
                        builder, geminiProperties(), minimalProperties());

        expectUpload(server);
        server.expect(
                        once(),
                        requestTo("https://generativelanguage.googleapis.com/v1beta/interactions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.model").value("gemini-3.5-transcribe"))
                .andExpect(jsonPath("$.input[0].type").value("audio"))
                .andExpect(jsonPath("$.input[0].uri").value("gemini://voice-1"))
                .andExpect(jsonPath("$.input[0].mime_type").value("audio/ogg"))
                .andExpect(jsonPath("$.generation_config").doesNotExist())
                .andRespond(
                        withSuccess(
                                """
                                {
                                  "id":"interaction-1",
                                  "status":"completed",
                                  "output_text":"Gastei R$ 850 com diesel hoje.",
                                  "usage":{"total_output_tokens":9}
                                }
                                """,
                                MediaType.APPLICATION_JSON));
        expectCleanup(server);

        String transcript = client.transcribe(request());

        assertThat(transcript).isEqualTo("Gastei R$ 850 com diesel hoje.");
        server.verify();
    }

    @Test
    void shouldSendSmartModeWithoutLanguageCodesWhenConfigured() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AudioTranscriptionProperties properties = minimalProperties();
        properties.setMode("smart");
        GeminiAudioTranscriptionClient client =
                new GeminiAudioTranscriptionClient(builder, geminiProperties(), properties);

        expectUpload(server);
        server.expect(
                        once(),
                        requestTo("https://generativelanguage.googleapis.com/v1beta/interactions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.generation_config.transcription_config.mode").value("smart"))
                .andExpect(
                        jsonPath("$.generation_config.transcription_config.language_codes")
                                .doesNotExist())
                .andRespond(
                        withSuccess(
                                "{\"output_text\":\"Gastei R$ 850 com diesel hoje.\"}",
                                MediaType.APPLICATION_JSON));
        expectCleanup(server);

        String transcript = client.transcribe(request());

        assertThat(transcript).isEqualTo("Gastei R$ 850 com diesel hoje.");
        server.verify();
    }

    @Test
    void shouldRejectMissingOrBlankOutputText() {
        assertEmptyTranscript("{}");
        assertEmptyTranscript("{\"output_text\":null}");
        assertEmptyTranscript("{\"output_text\":\"   \"}");
    }

    private void assertEmptyTranscript(String interactionResponse) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GeminiAudioTranscriptionClient client =
                new GeminiAudioTranscriptionClient(
                        builder, geminiProperties(), minimalProperties());

        expectUpload(server);
        server.expect(
                        once(),
                        requestTo("https://generativelanguage.googleapis.com/v1beta/interactions"))
                .andRespond(withSuccess(interactionResponse, MediaType.APPLICATION_JSON));
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
    void shouldClassifyPresentEmptyAndMissingOutputTextSeparately() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();

        GeminiAudioTranscriptionClient.InteractionResponseDetails present =
                GeminiAudioTranscriptionClient.inspectInteractionResponse(
                        objectMapper.readTree(
                                """
                                {"id":"example","output_text":"Gastei R$ 850 com diesel hoje."}
                                """));
        GeminiAudioTranscriptionClient.InteractionResponseDetails empty =
                GeminiAudioTranscriptionClient.inspectInteractionResponse(
                        objectMapper.readTree("{\"id\":\"example\",\"output_text\":\"\"}"));
        GeminiAudioTranscriptionClient.InteractionResponseDetails missing =
                GeminiAudioTranscriptionClient.inspectInteractionResponse(
                        objectMapper.readTree("{\"id\":\"example\"}"));

        assertThat(present.outputText()).isEqualTo("Gastei R$ 850 com diesel hoje.");
        assertThat(present.outputTextStatus())
                .isEqualTo(GeminiAudioTranscriptionClient.OutputTextStatus.PRESENT);
        assertThat(empty.outputTextStatus())
                .isEqualTo(GeminiAudioTranscriptionClient.OutputTextStatus.EMPTY);
        assertThat(missing.outputTextStatus())
                .isEqualTo(GeminiAudioTranscriptionClient.OutputTextStatus.MISSING);
    }

    @Test
    void shouldSaveRawInteractionResponseWhenDebugIsEnabled() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AudioTranscriptionProperties properties = minimalProperties();
        properties.setDebugResponse(true);
        properties.setDebugResponseDirectory(tempDir.toString());
        GeminiAudioTranscriptionClient client =
                new GeminiAudioTranscriptionClient(builder, geminiProperties(), properties);
        String response = "{\"id\":\"example\",\"output_text\":\"texto\"}";

        expectUpload(server);
        server.expect(
                        once(),
                        requestTo("https://generativelanguage.googleapis.com/v1beta/interactions"))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
        expectCleanup(server);

        client.transcribe(request());

        assertThat(
                        Files.readString(
                                tempDir.resolve(
                                        "gemini-response-519749095-391-gemini-transcribe.json")))
                .isEqualTo(response);
        server.verify();
    }

    @Test
    void shouldNotCreateDebugResponseFileWhenDisabled() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AudioTranscriptionProperties properties = minimalProperties();
        Path debugDirectory = tempDir.resolve("missing");
        properties.setDebugResponseDirectory(debugDirectory.toString());
        GeminiAudioTranscriptionClient client =
                new GeminiAudioTranscriptionClient(builder, geminiProperties(), properties);

        expectUpload(server);
        server.expect(
                        once(),
                        requestTo("https://generativelanguage.googleapis.com/v1beta/interactions"))
                .andRespond(withSuccess("{\"output_text\":\"texto\"}", MediaType.APPLICATION_JSON));
        expectCleanup(server);

        client.transcribe(request());

        assertThat(debugDirectory).doesNotExist();
        server.verify();
    }

    @Test
    void shouldTreatInteractionHttpErrorAsProviderError() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GeminiAudioTranscriptionClient client =
                new GeminiAudioTranscriptionClient(
                        builder, geminiProperties(), minimalProperties());

        expectUpload(server);
        server.expect(
                        once(),
                        requestTo("https://generativelanguage.googleapis.com/v1beta/interactions"))
                .andRespond(
                        withStatus(HttpStatus.BAD_REQUEST)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(
                                        """
                                        {"error":{"code":400,"status":"INVALID_ARGUMENT","message":"invalid input"}}
                                        """));
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

    private void expectUpload(MockRestServiceServer server) {
        server.expect(
                        once(),
                        requestTo("https://generativelanguage.googleapis.com/upload/v1beta/files"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "test-key"))
                .andExpect(header("X-Goog-Upload-Protocol", "resumable"))
                .andExpect(header("X-Goog-Upload-Header-Content-Type", "audio/ogg"))
                .andRespond(
                        withStatus(HttpStatus.OK).header("X-Goog-Upload-URL", "/upload-session"));
        server.expect(once(), requestTo("https://generativelanguage.googleapis.com/upload-session"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Goog-Upload-Command", "upload, finalize"))
                .andExpect(content().contentType(MediaType.valueOf("audio/ogg")))
                .andExpect(content().bytes(new byte[] {1, 2, 3}))
                .andRespond(
                        withSuccess(
                                "{\"file\":{\"name\":\"files/voice-1\",\"uri\":\"gemini://voice-1\",\"mimeType\":\"audio/ogg\",\"sizeBytes\":\"3\",\"state\":\"ACTIVE\"}}",
                                MediaType.APPLICATION_JSON));
    }

    private void expectCleanup(MockRestServiceServer server) {
        server.expect(
                        once(),
                        requestTo("https://generativelanguage.googleapis.com/v1beta/files/voice-1"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withSuccess());
    }

    private GeminiAiProperties geminiProperties() {
        GeminiAiProperties properties = new GeminiAiProperties();
        properties.setApiKey("test-key");
        return properties;
    }

    private AudioTranscriptionProperties minimalProperties() {
        return new AudioTranscriptionProperties();
    }

    private AudioTranscriptionRequest request() {
        return new AudioTranscriptionRequest(new byte[] {1, 2, 3}, "audio/ogg", "519749095", "391");
    }
}
