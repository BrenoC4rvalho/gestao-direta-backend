package br.com.gestaodireta.financial.dto;

import br.com.gestaodireta.financial.enumeration.PaymentMethod;
import br.com.gestaodireta.financial.enumeration.PaymentStatus;
import br.com.gestaodireta.financial.enumeration.TransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record FinancialTransactionUpdateRequest(
        @NotBlank @Size(max = 160) String description,
        @NotNull @DecimalMin(value = "0.00", inclusive = false) BigDecimal amount,
        @NotNull TransactionType type,
        @NotNull PaymentStatus status,
        PaymentMethod paymentMethod,
        @NotNull LocalDate transactionDate,
        LocalDate dueDate,
        LocalDate paidAt,
        @Size(max = 500) String notes,
        Long categoryId) {}
