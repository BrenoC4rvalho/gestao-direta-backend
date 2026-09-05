package br.com.gestaodireta.messaging.telegram.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.telegram.audio-debug")
public class TelegramAudioDebugProperties {
    private boolean saveEnabled;
    private String directory = "telegram-audio-debug";

    public boolean isSaveEnabled() {
        return saveEnabled;
    }

    public void setSaveEnabled(boolean value) {
        saveEnabled = value;
    }

    public String getDirectory() {
        return directory;
    }

    public void setDirectory(String value) {
        directory = value;
    }
}
