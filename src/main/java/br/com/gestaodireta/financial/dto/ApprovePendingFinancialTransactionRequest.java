package br.com.gestaodireta.financial.dto;

import br.com.gestaodireta.financial.enumeration.PaymentMethod;
import br.com.gestaodireta.financial.enumeration.PaymentStatus;
import br.com.gestaodireta.financial.enumeration.TransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ApprovePendingFinancialTransactionRequest(
        TransactionType type,
        @DecimalMin(value = "0.00", inclusive = false) BigDecimal amount,
        @Size(max = 160) String description,
        Long categoryId,
        Long harvestSeasonId,
        @NotNull PaymentStatus status,
        PaymentMethod paymentMethod,
        LocalDate transactionDate,
        LocalDate dueDate,
        LocalDate paidAt,
        @Size(max = 500) String notes) {}
