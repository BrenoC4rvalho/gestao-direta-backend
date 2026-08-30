package br.com.gestaodireta.ai.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "app.ai.health")
public class AiHealthProperties {

    private boolean enabled = true;

    @Min(1)
    private int timeoutSeconds = 5;

    @Min(1)
    private int cacheSeconds = 60;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean value) {
        enabled = value;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int value) {
        timeoutSeconds = value;
    }

    public int getCacheSeconds() {
        return cacheSeconds;
    }

    public void setCacheSeconds(int value) {
        cacheSeconds = value;
    }
}
