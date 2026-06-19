package br.com.gestaodireta.farm.dto;

import br.com.gestaodireta.farm.enumeration.FarmUserRole;
import jakarta.validation.constraints.NotNull;

public record FarmUserRequest(@NotNull Long userId, @NotNull FarmUserRole role) {}
