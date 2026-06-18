package br.com.gestaodireta.user.dto;

import br.com.gestaodireta.user.enumeration.UserStatus;
import jakarta.validation.constraints.NotNull;

public record UserStatusUpdateRequest(@NotNull UserStatus status) {}
