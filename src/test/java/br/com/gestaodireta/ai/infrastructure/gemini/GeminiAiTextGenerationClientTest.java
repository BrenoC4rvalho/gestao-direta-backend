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

import br.com.gestaodireta.ai.service.AiModelNotAvailableException;
import br.com.gestaodireta.ai.service.AiProviderException;
import br.com.gestaodireta.ai.service.FinancialExtractionResponseSchema;
import br.com.gestaodireta.ai.service.provider.AiGenerationRequest;
import java.net.SocketTimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
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
                .andExpect(
                        jsonPath("$.generationConfig.responseMimeType").value("application/json"))
                .andExpect(jsonPath("$.generationConfig.responseJsonSchema.type").value("object"))
                .andExpect(
                        jsonPath(
                                        "$.generationConfig.responseJsonSchema.properties.isFinancialTransaction.type")
                                .value("boolean"))
                .andExpect(jsonPath("$.generationConfig.responseFormat").doesNotExist())
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
    void shouldSendMinimalStructuredOutputSchemaUsingGenerateContentFields() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GeminiAiTextGenerationClient client =
                new GeminiAiTextGenerationClient(builder, properties());
        server.expect(once(), requestTo(org.hamcrest.Matchers.containsString("generateContent")))
                .andExpect(
                        jsonPath("$.generationConfig.responseMimeType").value("application/json"))
                .andExpect(
                        jsonPath("$.generationConfig.responseJsonSchema.required")
                                .value(
                                        org.hamcrest.Matchers.containsInAnyOrder(
                                                "type", "amount", "description")))
                .andExpect(
                        jsonPath("$.generationConfig.responseJsonSchema.properties.type.enum")
                                .value(org.hamcrest.Matchers.contains("INCOME", "EXPENSE")))
                .andExpect(jsonPath("$.generationConfig.responseFormat").doesNotExist())
                .andRespond(withSuccess(response("{}"), MediaType.APPLICATION_JSON));

        client.generate(new AiGenerationRequest("extract", minimalSchema()));

        server.verify();
    }

    @Test
    void shouldClassifyAuthenticationRateLimitAndServerErrors() {
        assertHttpFailure(HttpStatus.BAD_REQUEST, AiProviderException.Reason.INVALID_REQUEST);
        assertHttpFailure(HttpStatus.UNAUTHORIZED, AiProviderException.Reason.UNAUTHORIZED);
        assertHttpFailure(HttpStatus.FORBIDDEN, AiProviderException.Reason.FORBIDDEN);
        assertHttpFailure(HttpStatus.TOO_MANY_REQUESTS, AiProviderException.Reason.RATE_LIMIT);
        assertHttpFailure(
                HttpStatus.SERVICE_UNAVAILABLE, AiProviderException.Reason.SERVICE_UNAVAILABLE);
    }

    @Test
    void shouldPreserveSanitizedGeminiErrorDetails() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GeminiAiTextGenerationClient client =
                new GeminiAiTextGenerationClient(builder, properties());
        server.expect(once(), requestTo(org.hamcrest.Matchers.containsString("generateContent")))
                .andRespond(
                        withStatus(HttpStatus.BAD_REQUEST)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(
                                        """
                                        {"error":{"code":400,"status":"INVALID_ARGUMENT","message":"Invalid JSON payload received. Unknown name x-goog-api-key: secret-value"}}
                                        """));

        assertThatThrownBy(() -> client.generate(new AiGenerationRequest("extract")))
                .isInstanceOf(AiProviderException.class)
                .satisfies(
                        exception -> {
                            AiProviderException providerException = (AiProviderException) exception;
                            assertThat(providerException.getReason())
                                    .isEqualTo(AiProviderException.Reason.INVALID_REQUEST);
                            assertThat(providerException.getStatusCode()).isEqualTo(400);
                            assertThat(providerException.getProviderStatus())
                                    .isEqualTo("INVALID_ARGUMENT");
                            assertThat(providerException.getMessage())
                                    .contains("Invalid JSON payload received")
                                    .doesNotContain("secret-value");
                        });
        server.verify();
    }

    @Test
    void shouldProbeGeminiWithMinimalRequest() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GeminiAiTextGenerationClient client =
                new GeminiAiTextGenerationClient(builder, properties());
        server.expect(once(), requestTo(org.hamcrest.Matchers.containsString("generateContent")))
                .andExpect(
                        content()
                                .string(
                                        org.hamcrest.Matchers.containsString(
                                                "Reply only with OK.")))
                .andExpect(jsonPath("$.generationConfig").doesNotExist())
                .andExpect(jsonPath("$.responseJsonSchema").doesNotExist())
                .andRespond(withSuccess(response("Ready"), MediaType.APPLICATION_JSON));

        client.probe();

        server.verify();
    }

    @Test
    void shouldAcceptAnyNonBlankTextFromGeminiHealthProbe() {
        assertSuccessfulProbe("OK");
        assertSuccessfulProbe("OK\n");
        assertSuccessfulProbe("ok");
        assertSuccessfulProbe("Ready");
    }

    @Test
    void shouldAcceptTextFromAnyCandidatePartInGeminiHealthProbe() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GeminiAiTextGenerationClient client =
                new GeminiAiTextGenerationClient(builder, properties());
        server.expect(once(), requestTo(org.hamcrest.Matchers.containsString("generateContent")))
                .andRespond(
                        withSuccess(
                                """
                                {"candidates":[
                                  {"content":{"parts":[{}]},"finishReason":"STOP"},
                                  {"content":{"parts":[{"text":"Ready"}]},"finishReason":"STOP"}
                                ]}
                                """,
                                MediaType.APPLICATION_JSON));

        client.probe();

        server.verify();
    }

    @Test
    void shouldRejectInvalidGeminiHealthProbeResponses() {
        assertInvalidProbeResponse("{\"candidates\":[]}");
        assertInvalidProbeResponse("{\"candidates\":[{\"content\":{},\"finishReason\":\"STOP\"}]}");
        assertInvalidProbeResponse(
                """
                {"candidates":[{"content":{"parts":[{"text":"  "}]}}]}
                """);
        assertInvalidProbeResponse("{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}");
        assertInvalidProbeResponse("{invalid");
    }

    @Test
    void shouldClassifyGeminiHealthProbeHttpFailures() {
        assertProbeHttpFailure(HttpStatus.UNAUTHORIZED, AiProviderException.Reason.UNAUTHORIZED);
        assertProbeHttpFailure(HttpStatus.FORBIDDEN, AiProviderException.Reason.FORBIDDEN);
        assertProbeHttpFailure(HttpStatus.TOO_MANY_REQUESTS, AiProviderException.Reason.RATE_LIMIT);
        assertThatThrownBy(() -> probeWithStatus(HttpStatus.NOT_FOUND))
                .isInstanceOf(AiModelNotAvailableException.class);
    }

    @Test
    void shouldClassifyGeminiHealthProbeTimeout() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GeminiAiTextGenerationClient client =
                new GeminiAiTextGenerationClient(builder, properties());
        server.expect(once(), requestTo(org.hamcrest.Matchers.containsString("generateContent")))
                .andRespond(
                        request -> {
                            throw new ResourceAccessException(
                                    "Timed out", new SocketTimeoutException());
                        });

        assertThatThrownBy(client::probe)
                .isInstanceOf(AiProviderException.class)
                .extracting("reason")
                .isEqualTo(AiProviderException.Reason.TIMEOUT);
        server.verify();
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

        RestClient.Builder invalidJsonBuilder = RestClient.builder();
        MockRestServiceServer invalidJsonServer =
                MockRestServiceServer.bindTo(invalidJsonBuilder).build();
        GeminiAiTextGenerationClient invalidJsonClient =
                new GeminiAiTextGenerationClient(invalidJsonBuilder, properties());
        invalidJsonServer
                .expect(once(), requestTo(org.hamcrest.Matchers.containsString("generateContent")))
                .andRespond(withSuccess("{invalid", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> invalidJsonClient.generate(new AiGenerationRequest("extract")))
                .isInstanceOf(AiProviderException.class)
                .extracting("reason")
                .isEqualTo(AiProviderException.Reason.INVALID_RESPONSE);
        invalidJsonServer.verify();
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

    private void assertSuccessfulProbe(String text) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GeminiAiTextGenerationClient client =
                new GeminiAiTextGenerationClient(builder, properties());
        server.expect(once(), requestTo(org.hamcrest.Matchers.containsString("generateContent")))
                .andRespond(withSuccess(response(text), MediaType.APPLICATION_JSON));

        client.probe();

        server.verify();
    }

    private void assertInvalidProbeResponse(String responseBody) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GeminiAiTextGenerationClient client =
                new GeminiAiTextGenerationClient(builder, properties());
        server.expect(once(), requestTo(org.hamcrest.Matchers.containsString("generateContent")))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        assertThatThrownBy(client::probe)
                .isInstanceOf(AiProviderException.class)
                .extracting("reason")
                .isEqualTo(AiProviderException.Reason.INVALID_RESPONSE);
        server.verify();
    }

    private void assertProbeHttpFailure(
            HttpStatus status, AiProviderException.Reason expectedReason) {
        assertThatThrownBy(() -> probeWithStatus(status))
                .isInstanceOf(AiProviderException.class)
                .extracting("reason")
                .isEqualTo(expectedReason);
    }

    private void probeWithStatus(HttpStatus status) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GeminiAiTextGenerationClient client =
                new GeminiAiTextGenerationClient(builder, properties());
        server.expect(once(), requestTo(org.hamcrest.Matchers.containsString("generateContent")))
                .andRespond(withStatus(status));

        try {
            client.probe();
        } finally {
            server.verify();
        }
    }

    private String response(String text) {
        return """
                {"candidates":[{"content":{"parts":[{"text":"%s"}]},"finishReason":"STOP"}]}
                """
                .formatted(
                        text.replace("\\", "\\\\")
                                .replace("\"", "\\\"")
                                .replace("\n", "\\n")
                                .replace("\r", "\\r"));
    }

    private GeminiAiProperties properties() {
        GeminiAiProperties properties = new GeminiAiProperties();
        properties.setApiKey("test-key");
        properties.setModel("gemini-3.1-flash-lite");
        return properties;
    }

    private java.util.Map<String, Object> minimalSchema() {
        return java.util.Map.of(
                "type",
                "object",
                "properties",
                java.util.Map.of(
                        "type",
                        java.util.Map.of(
                                "type", "string", "enum", java.util.List.of("INCOME", "EXPENSE")),
                        "amount",
                        java.util.Map.of("type", "number"),
                        "description",
                        java.util.Map.of("type", "string")),
                "required",
                java.util.List.of("type", "amount", "description"));
    }
}
