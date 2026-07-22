package br.com.gestaodireta.messaging.dto;

import jakarta.validation.constraints.*;

public record SendTelegramMessageRequest(
        @NotNull Long messagingAccountId, @NotBlank @Size(max = 4096) String content) {}
