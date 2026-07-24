package br.com.gestaodireta.messaging.dto;

import br.com.gestaodireta.messaging.enumeration.MessagingChannel;
import java.time.Instant;

public record MessagingLinkCodeResponse(String code, MessagingChannel channel, Instant expiresAt) {}
