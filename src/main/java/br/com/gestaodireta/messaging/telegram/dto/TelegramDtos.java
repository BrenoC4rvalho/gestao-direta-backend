package br.com.gestaodireta.messaging.telegram.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class TelegramDtos {
    private TelegramDtos() {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiResponse<T>(
            boolean ok,
            T result,
            @JsonProperty("error_code") Integer errorCode,
            String description) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Update(@JsonProperty("update_id") Long updateId, Message message) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(
            @JsonProperty("message_id") Long messageId,
            Long date,
            Chat chat,
            User from,
            String text,
            Voice voice) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Voice(
            @JsonProperty("file_id") String fileId,
            @JsonProperty("file_unique_id") String fileUniqueId,
            Integer duration,
            @JsonProperty("mime_type") String mimeType,
            @JsonProperty("file_size") Long fileSize) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record File(@JsonProperty("file_path") String filePath) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Chat(
            Long id,
            String type,
            String username,
            @JsonProperty("first_name") String firstName,
            @JsonProperty("last_name") String lastName,
            String title) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record User(
            Long id,
            @JsonProperty("is_bot") boolean bot,
            @JsonProperty("first_name") String firstName,
            @JsonProperty("last_name") String lastName,
            String username,
            @JsonProperty("language_code") String languageCode) {}
}
