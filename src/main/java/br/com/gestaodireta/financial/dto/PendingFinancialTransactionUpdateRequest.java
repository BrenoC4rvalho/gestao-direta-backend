package br.com.gestaodireta.financial.dto;

import br.com.gestaodireta.financial.enumeration.PaymentMethod;
import br.com.gestaodireta.financial.enumeration.TransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record PendingFinancialTransactionUpdateRequest(
        @NotNull TransactionType type,
        @NotNull @DecimalMin(value = "0.00", inclusive = false) BigDecimal amount,
        @NotNull LocalDate transactionDate,
        @NotBlank @Size(max = 160) String description,
        Long categoryId,
        Long harvestSeasonId,
        PaymentMethod paymentMethod,
        @Size(max = 500) String notes) {}
