package br.com.gestaodireta.user.dto;

import br.com.gestaodireta.user.enumeration.UserType;
import jakarta.validation.constraints.NotNull;

public record UserTypeUpdateRequest(@NotNull UserType userType) {}
