package br.com.gestaodireta.messaging.dto;

import br.com.gestaodireta.messaging.enumeration.*;
import java.time.LocalDateTime;

public record MessagingAccountResponse(
        Long id,
        MessagingChannel channel,
        String externalUserId,
        String externalChatId,
        String username,
        String displayName,
        MessagingAccountStatus status,
        LocalDateTime lastInteractionAt,
        LocalDateTime createdAt) {}
