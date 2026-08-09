package br.com.gestaodireta.auth.dto;

import java.time.Instant;

public record PasswordRecoveryVerifyResponse(String recoveryToken, Instant expiresAt) {}
