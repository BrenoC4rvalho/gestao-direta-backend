package br.com.gestaodireta.ai.infrastructure.gemini;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.ai.gemini")
public class GeminiAiProperties {

    private String apiKey;

    private String model = "gemini-3.1-flash-lite";

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String value) {
        apiKey = value;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String value) {
        model = value;
    }
}
