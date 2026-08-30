package br.com.gestaodireta.ai.infrastructure.gemini;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import br.com.gestaodireta.ai.service.AiModelNotAvailableException;
import br.com.gestaodireta.ai.service.AiProviderException;
import br.com.gestaodireta.ai.service.FinancialExtractionResponseSchema;
import br.com.gestaodireta.ai.service.provider.AiGenerationRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GeminiAiTextGenerationClientTest {

    @Test
    void shouldCallGeminiWithStructuredOutputAndReturnJsonText() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GeminiAiTextGenerationClient client =
                new GeminiAiTextGenerationClient(builder, properties());

        server.expect(
                        once(),
                        requestTo(
                                "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "test-key"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("responseFormat")))
                .andExpect(
                        content()
                                .string(
                                        org.hamcrest.Matchers.containsString(
                                                "isFinancialTransaction")))
                .andRespond(
                        withSuccess(
                                response("{\"type\":\"EXPENSE\",\"amount\":350}"),
                                MediaType.APPLICATION_JSON));

        String result =
                client.generate(
                        new AiGenerationRequest(
                                "extract", new FinancialExtractionResponseSchema().schema()));

        assertThat(result).isEqualTo("{\"type\":\"EXPENSE\",\"amount\":350}");
        server.verify();
    }

    @Test
    void shouldClassifyAuthenticationRateLimitAndServerErrors() {
        assertHttpFailure(HttpStatus.UNAUTHORIZED, AiProviderException.Reason.UNAUTHORIZED);
        assertHttpFailure(HttpStatus.FORBIDDEN, AiProviderException.Reason.FORBIDDEN);
        assertHttpFailure(HttpStatus.TOO_MANY_REQUESTS, AiProviderException.Reason.RATE_LIMIT);
        assertHttpFailure(
                HttpStatus.SERVICE_UNAVAILABLE, AiProviderException.Reason.SERVICE_UNAVAILABLE);
    }

    @Test
    void shouldClassifyMissingModelAndInvalidResponses() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GeminiAiTextGenerationClient client =
                new GeminiAiTextGenerationClient(builder, properties());

        server.expect(once(), requestTo(org.hamcrest.Matchers.containsString("generateContent")))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> client.generate(new AiGenerationRequest("extract")))
                .isInstanceOf(AiModelNotAvailableException.class);
        server.verify();

        RestClient.Builder emptyBuilder = RestClient.builder();
        MockRestServiceServer emptyServer = MockRestServiceServer.bindTo(emptyBuilder).build();
        GeminiAiTextGenerationClient emptyClient =
                new GeminiAiTextGenerationClient(emptyBuilder, properties());
        emptyServer
                .expect(once(), requestTo(org.hamcrest.Matchers.containsString("generateContent")))
                .andRespond(withSuccess("{\"candidates\":[]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> emptyClient.generate(new AiGenerationRequest("extract")))
                .isInstanceOf(AiProviderException.class)
                .extracting("reason")
                .isEqualTo(AiProviderException.Reason.INVALID_RESPONSE);
        emptyServer.verify();
    }

    private void assertHttpFailure(HttpStatus status, AiProviderException.Reason expectedReason) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GeminiAiTextGenerationClient client =
                new GeminiAiTextGenerationClient(builder, properties());
        server.expect(once(), requestTo(org.hamcrest.Matchers.containsString("generateContent")))
                .andRespond(withStatus(status));

        assertThatThrownBy(() -> client.generate(new AiGenerationRequest("extract")))
                .isInstanceOf(AiProviderException.class)
                .extracting("reason")
                .isEqualTo(expectedReason);
        server.verify();
    }

    private String response(String text) {
        return """
                {"candidates":[{"content":{"parts":[{"text":"%s"}]},"finishReason":"STOP"}]}
                """
                .formatted(text.replace("\"", "\\\""));
    }

    private GeminiAiProperties properties() {
        GeminiAiProperties properties = new GeminiAiProperties();
        properties.setApiKey("test-key");
        properties.setModel("gemini-3.1-flash-lite");
        return properties;
    }
}
