package br.com.gestaodireta.financial.dto;

import br.com.gestaodireta.financial.enumeration.PendingFinancialTransactionStatus;
import br.com.gestaodireta.financial.enumeration.TransactionType;
import java.time.LocalDate;

public record PendingFinancialTransactionFilterRequest(
        Long farmId,
        PendingFinancialTransactionStatus status,
        TransactionType type,
        LocalDate startDate,
        LocalDate endDate,
        Long requestedByUserId) {}
