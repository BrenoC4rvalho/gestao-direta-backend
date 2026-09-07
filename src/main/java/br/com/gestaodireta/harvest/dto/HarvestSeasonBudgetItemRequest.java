package br.com.gestaodireta.harvest.dto;

import br.com.gestaodireta.financial.enumeration.TransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record HarvestSeasonBudgetItemRequest(
        @NotNull Long categoryId,
        @NotNull TransactionType type,
        @NotBlank @Size(max = 500) String description,
        @NotNull @DecimalMin(value = "0.00", inclusive = false) @Digits(integer = 13, fraction = 2)
                BigDecimal plannedAmount) {}
