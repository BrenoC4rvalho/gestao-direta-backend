package br.com.gestaodireta.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.ai")
public class AiProviderProperties {

    private String provider = "ollama";

    public String getProvider() {
        return provider;
    }

    public void setProvider(String value) {
        provider = value;
    }
}
