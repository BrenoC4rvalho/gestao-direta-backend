package br.com.gestaodireta.harvest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProductionActivityRequest(
        @NotBlank @Size(max = 120) String name, @Size(max = 500) String description) {}
