package br.com.gestaodireta.user.dto;

import br.com.gestaodireta.messaging.enumeration.*;
import java.time.LocalDateTime;

public record UserContactMessagingAccountResponse(
        Long id,
        MessagingChannel channel,
        String username,
        String displayName,
        MessagingAccountStatus status,
        LocalDateTime verifiedAt,
        LocalDateTime createdAt) {}
