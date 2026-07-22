package br.com.gestaodireta.messaging.telegram.config;

import br.com.gestaodireta.shared.exception.BusinessException;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "messaging.telegram")
public class TelegramProperties {
    private boolean enabled;
    private String botToken;
    private String webhookSecret;
    private String apiBaseUrl;

    @PostConstruct
    void validate() {
        if (enabled && (isBlank(botToken) || isBlank(webhookSecret)))
            throw new BusinessException("Telegram configuration is incomplete");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean value) {
        enabled = value;
    }

    public String getBotToken() {
        return botToken;
    }

    public void setBotToken(String value) {
        botToken = value;
    }

    public String getWebhookSecret() {
        return webhookSecret;
    }

    public void setWebhookSecret(String value) {
        webhookSecret = value;
    }

    public String getApiBaseUrl() {
        return apiBaseUrl;
    }

    public void setApiBaseUrl(String value) {
        apiBaseUrl = value;
    }
}
