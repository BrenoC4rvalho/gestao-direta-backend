package br.com.gestaodireta.ai.transcription;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.ai.audio-transcription")
public class AudioTranscriptionProperties {

    private boolean enabled;
    private String strategy = "gemini-transcribe";
    private String model = "gemini-3.5-transcribe";
    private String language;
    private String mode;
    private int maxDurationSeconds = 60;
    private int maxSizeMb = 10;
    private int timeoutSeconds = 30;
    private boolean debugResponse;
    private String debugResponseDirectory = "telegram-audio-debug";

    public boolean isEnabled() {
        return enabled;
    }

    public String getStrategy() {
        return strategy;
    }

    public void setStrategy(String value) {
        strategy = value;
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

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String value) {
        language = value;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String value) {
        mode = value;
    }

    public int getMaxDurationSeconds() {
        return maxDurationSeconds;
    }

    public void setMaxDurationSeconds(int value) {
        maxDurationSeconds = value;
    }

    public int getMaxSizeMb() {
        return maxSizeMb;
    }

    public void setMaxSizeMb(int value) {
        maxSizeMb = value;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int value) {
        timeoutSeconds = value;
    }

    public boolean isDebugResponse() {
        return debugResponse;
    }

    public void setDebugResponse(boolean value) {
        debugResponse = value;
    }

    public String getDebugResponseDirectory() {
        return debugResponseDirectory;
    }

    public void setDebugResponseDirectory(String value) {
        debugResponseDirectory = value;
    }
}
