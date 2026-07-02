package br.com.gestaodireta.harvest.dto;

import br.com.gestaodireta.harvest.enumeration.HarvestSeasonStatus;
import jakarta.validation.constraints.NotNull;

public record HarvestSeasonStatusUpdateRequest(@NotNull HarvestSeasonStatus status) {}
