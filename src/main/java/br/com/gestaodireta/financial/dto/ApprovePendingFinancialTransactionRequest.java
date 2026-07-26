package br.com.gestaodireta.financial.dto;

import br.com.gestaodireta.financial.enumeration.PaymentStatus;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record ApprovePendingFinancialTransactionRequest(
        @NotNull PaymentStatus status, LocalDate dueDate, LocalDate paidAt) {}
