package br.com.gestaodireta.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.ai.financial-extraction")
public class FinancialExtractionProperties {
    private boolean enabled;
    private String model;
    private int timeoutSeconds = 20;
    private double minimumConfidence = 0.60;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean value) {
        enabled = value;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String value) {
        model = value;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int value) {
        timeoutSeconds = value;
    }

    public double getMinimumConfidence() {
        return minimumConfidence;
    }

    public void setMinimumConfidence(double value) {
        minimumConfidence = value;
    }
}
