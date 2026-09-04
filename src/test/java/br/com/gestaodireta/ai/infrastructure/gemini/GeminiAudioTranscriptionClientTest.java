package br.com.gestaodireta.ai.infrastructure.gemini;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import br.com.gestaodireta.ai.transcription.AudioTranscriptionProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GeminiAudioTranscriptionClientTest {

    @Test
    void shouldUploadOggCreateSmartInteractionAndCleanUpFile() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GeminiAudioTranscriptionClient client =
                new GeminiAudioTranscriptionClient(builder, geminiProperties(), properties());

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
                .andExpect(content().bytes(new byte[] {1, 2, 3}))
                .andRespond(
                        withSuccess(
                                "{\"file\":{\"name\":\"files/voice-1\",\"uri\":\"gemini://voice-1\"}}",
                                MediaType.APPLICATION_JSON));
        server.expect(
                        once(),
                        requestTo("https://generativelanguage.googleapis.com/v1beta/interactions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.model").value("gemini-3.5-transcribe"))
                .andExpect(jsonPath("$.input[0].type").value("audio"))
                .andExpect(jsonPath("$.input[0].uri").value("gemini://voice-1"))
                .andExpect(jsonPath("$.input[0].mime_type").value("audio/ogg"))
                .andExpect(jsonPath("$.generation_config.transcription_config.mode").value("smart"))
                .andExpect(
                        jsonPath("$.generation_config.transcription_config.language_codes[0]")
                                .value("pt-BR"))
                .andRespond(
                        withSuccess(
                                "{\"outputs\":[{\"type\":\"text\",\"text\":\"Gastei R$ 850 com diesel hoje.\"}]}",
                                MediaType.APPLICATION_JSON));
        server.expect(
                        once(),
                        requestTo("https://generativelanguage.googleapis.com/v1beta/files/voice-1"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withSuccess());

        String transcript = client.transcribe(new byte[] {1, 2, 3}, "audio/ogg");

        assertThat(transcript).isEqualTo("Gastei R$ 850 com diesel hoje.");
        server.verify();
    }

    private GeminiAiProperties geminiProperties() {
        GeminiAiProperties properties = new GeminiAiProperties();
        properties.setApiKey("test-key");
        return properties;
    }

    private AudioTranscriptionProperties properties() {
        AudioTranscriptionProperties properties = new AudioTranscriptionProperties();
        properties.setLanguage("pt-BR");
        properties.setMode("smart");
        return properties;
    }
}
