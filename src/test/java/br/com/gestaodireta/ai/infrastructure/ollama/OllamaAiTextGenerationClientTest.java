package br.com.gestaodireta.ai.infrastructure.ollama;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import br.com.gestaodireta.ai.service.provider.AiGenerationRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class OllamaAiTextGenerationClientTest {

    @Test
    void shouldCallOllamaGenerateEndpointAndReturnResponseText() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        OllamaAiTextGenerationClient client =
                new OllamaAiTextGenerationClient(
                        restClientBuilder, "http://ollama.example", "llama3.1:8b");

        server.expect(once(), requestTo("http://ollama.example/api/generate"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(
                        content()
                                .json(
                                        """
                        {
                          "model": "llama3.1:8b",
                          "prompt": "parse this",
                          "stream": false
                        }
                        """))
                .andRespond(
                        withSuccess(
                                "{\"response\":\"{\\\"type\\\":\\\"EXPENSE\\\"}\"}",
                                MediaType.APPLICATION_JSON));

        String response = client.generate(new AiGenerationRequest("parse this"));

        assertThat(response).isEqualTo("{\"type\":\"EXPENSE\"}");
        server.verify();
    }
}
