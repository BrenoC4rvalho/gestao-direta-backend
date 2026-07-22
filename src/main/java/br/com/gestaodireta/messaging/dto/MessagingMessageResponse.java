package br.com.gestaodireta.messaging.dto;

import br.com.gestaodireta.messaging.enumeration.*;
import java.time.LocalDateTime;

public record MessagingMessageResponse(
        Long id,
        Long messagingAccountId,
        MessagingChannel channel,
        MessagingDirection direction,
        MessagingMessageType messageType,
        String content,
        MessagingMessageStatus status,
        String providerMessageId,
        LocalDateTime receivedAt,
        LocalDateTime sentAt,
        LocalDateTime processedAt,
        String errorMessage,
        LocalDateTime createdAt) {}
