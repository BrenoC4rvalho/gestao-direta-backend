package br.com.gestaodireta.messaging.dto;

import br.com.gestaodireta.messaging.enumeration.MessagingDirection;
import br.com.gestaodireta.messaging.enumeration.MessagingMessageStatus;

public record MessagingMessageFilterRequest(
        Long messagingConversationId,
        MessagingDirection messageDirection,
        MessagingMessageStatus status) {}
