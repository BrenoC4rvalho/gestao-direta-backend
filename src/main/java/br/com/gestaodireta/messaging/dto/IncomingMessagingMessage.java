package br.com.gestaodireta.messaging.dto;

import br.com.gestaodireta.messaging.enumeration.*;
import java.time.Instant;

public record IncomingMessagingMessage(
        MessagingChannel channel,
        String providerUpdateId,
        String providerMessageId,
        String externalUserId,
        String externalChatId,
        String username,
        String displayName,
        MessagingMessageType messageType,
        String content,
        IncomingAudioAttachment audio,
        Instant receivedAt,
        String rawPayload) {}
