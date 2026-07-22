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
            String text) {}

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
