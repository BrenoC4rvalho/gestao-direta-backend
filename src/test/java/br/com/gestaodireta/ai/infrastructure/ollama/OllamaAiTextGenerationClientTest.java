package br.com.gestaodireta.ai.infrastructure.ollama;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import br.com.gestaodireta.ai.service.AiModelNotAvailableException;
import br.com.gestaodireta.ai.service.provider.AiGenerationRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class OllamaAiTextGenerationClientTest {

    @Test
    void shouldCallOllamaGenerateEndpointAndReturnResponseText() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        OllamaAiTextGenerationClient client =
                new OllamaAiTextGenerationClient(restClientBuilder, properties());

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

    @Test
    void shouldThrowControlledExceptionWhenConfiguredModelIsNotFound() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        OllamaAiTextGenerationClient client =
                new OllamaAiTextGenerationClient(restClientBuilder, properties());

        server.expect(once(), requestTo("http://ollama.example/api/generate"))
                .andRespond(
                        withStatus(HttpStatus.NOT_FOUND)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body("{\"error\":\"model 'llama3.1:8b' not found\"}"));

        assertThatThrownBy(() -> client.generate(new AiGenerationRequest("parse this")))
                .isInstanceOf(AiModelNotAvailableException.class)
                .hasMessage(
                        "Modelo de IA não encontrado no Ollama. "
                                + "Baixe o modelo configurado antes de usar a IA.")
                .extracting("model")
                .isEqualTo("llama3.1:8b");

        server.verify();
    }

    private OllamaAiProperties properties() {
        OllamaAiProperties properties = new OllamaAiProperties();
        properties.setBaseUrl("http://ollama.example");
        properties.setModel("llama3.1:8b");

        return properties;
    }
}
