package br.com.gestaodireta.farm.dto;

import br.com.gestaodireta.farm.enumeration.FarmStatus;
import jakarta.validation.constraints.NotNull;

public record FarmStatusUpdateRequest(@NotNull FarmStatus status) {}
