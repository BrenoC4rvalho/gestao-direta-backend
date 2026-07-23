package br.com.gestaodireta.messaging.dto;

import br.com.gestaodireta.messaging.enumeration.*;
import java.time.LocalDateTime;

public record MessagingConversationResponse(
        Long id,
        Long messagingAccountId,
        MessagingChannel channel,
        String displayName,
        String username,
        Long userId,
        String userName,
        Long farmId,
        String farmName,
        MessagingConversationStatus status,
        MessagingConversationStep currentStep,
        LocalDateTime lastInteractionAt,
        LocalDateTime expiresAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {}
