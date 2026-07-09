package br.com.gestaodireta.ai.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ParseTransactionTextRequest(
        @NotNull Long farmId, @NotBlank @Size(max = 2000) String text) {}
