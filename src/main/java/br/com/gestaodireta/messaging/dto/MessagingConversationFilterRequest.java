package br.com.gestaodireta.messaging.dto;

import br.com.gestaodireta.messaging.enumeration.MessagingConversationStatus;
import java.time.LocalDateTime;

public record MessagingConversationFilterRequest(
        Long messagingAccountId,
        Long userId,
        Long farmId,
        MessagingConversationStatus status,
        LocalDateTime startDate,
        LocalDateTime endDate) {}
