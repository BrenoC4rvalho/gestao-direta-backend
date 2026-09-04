package br.com.gestaodireta.messaging.telegram.client;

import br.com.gestaodireta.messaging.telegram.config.TelegramProperties;
import br.com.gestaodireta.messaging.telegram.dto.TelegramDtos.*;
import br.com.gestaodireta.shared.exception.BusinessException;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class RestClientTelegramBotClient implements TelegramBotClient {
    private final RestClient restClient;
    private final TelegramProperties properties;

    public RestClientTelegramBotClient(RestClient.Builder builder, TelegramProperties properties) {
        this.properties = properties;
        this.restClient = builder.baseUrl(properties.getApiBaseUrl()).build();
    }

    public TelegramBotIdentity getMe() {
        ensureEnabled();
        ApiResponse<User> response =
                call("getMe", null, new org.springframework.core.ParameterizedTypeReference<>() {});
        if (!response.ok() || response.result() == null)
            throw new BusinessException("Telegram API is unavailable");
        return new TelegramBotIdentity(
                String.valueOf(response.result().id()), response.result().username());
    }

    public TelegramSendMessageResult sendMessage(String chatId, String text) {
        ensureEnabled();
        ApiResponse<Message> response =
                call(
                        "sendMessage",
                        Map.of("chat_id", chatId, "text", text),
                        new org.springframework.core.ParameterizedTypeReference<>() {});
        if (!response.ok() || response.result() == null)
            throw new BusinessException("Telegram API rejected the message");
        return new TelegramSendMessageResult(String.valueOf(response.result().messageId()));
    }

    @Override
    public String getFilePath(String fileId) {
        ensureEnabled();
        ApiResponse<File> response =
                call(
                        "getFile",
                        Map.of("file_id", fileId),
                        new org.springframework.core.ParameterizedTypeReference<>() {});
        if (!response.ok() || response.result() == null || isBlank(response.result().filePath())) {
            throw new BusinessException("Telegram file lookup failed");
        }
        return response.result().filePath();
    }

    @Override
    public byte[] downloadFile(String filePath) {
        ensureEnabled();
        if (isBlank(filePath)) {
            throw new BusinessException("Telegram file path is missing");
        }
        try {
            byte[] content =
                    restClient
                            .get()
                            .uri("/file/bot" + properties.getBotToken() + "/" + filePath)
                            .retrieve()
                            .body(byte[].class);
            if (content == null) {
                throw new BusinessException("Telegram file download returned an empty response");
            }
            return content;
        } catch (RestClientException exception) {
            throw new BusinessException("Telegram file download failed");
        }
    }

    private <T> ApiResponse<T> call(
            String method,
            Object body,
            org.springframework.core.ParameterizedTypeReference<ApiResponse<T>> type) {
        try {
            RestClient.RequestBodySpec request =
                    restClient.post().uri("/bot" + properties.getBotToken() + "/" + method);
            if (body != null) request.body(body);
            ApiResponse<T> response = request.retrieve().body(type);
            if (response == null)
                throw new BusinessException("Telegram API returned an empty response");
            return response;
        } catch (RestClientException exception) {
            throw new BusinessException("Telegram API is unavailable");
        }
    }

    private void ensureEnabled() {
        if (!properties.isEnabled())
            throw new BusinessException("Telegram integration is disabled");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
