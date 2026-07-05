package br.com.gestaodireta.financial.dto;

import br.com.gestaodireta.financial.enumeration.TransactionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record FinancialCategoryUpdateRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull TransactionType type,
        @Size(max = 20) String color,
        @Size(max = 60) String icon) {}
