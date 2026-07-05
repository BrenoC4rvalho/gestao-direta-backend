package br.com.gestaodireta.harvest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProductionActivityCreateRequest(
        @NotNull Long farmId,
        @NotBlank @Size(max = 120) String name,
        @Size(max = 500) String description) {}
