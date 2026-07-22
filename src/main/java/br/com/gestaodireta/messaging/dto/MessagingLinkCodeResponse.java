package br.com.gestaodireta.messaging.dto;

import java.time.LocalDateTime;

public record MessagingLinkCodeResponse(String code, LocalDateTime expiresAt, String command) {}
