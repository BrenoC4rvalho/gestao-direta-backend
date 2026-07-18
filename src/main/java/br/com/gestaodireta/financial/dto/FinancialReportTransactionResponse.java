package br.com.gestaodireta.financial.dto;

import br.com.gestaodireta.financial.enumeration.PaymentStatus;
import br.com.gestaodireta.financial.enumeration.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;

public record FinancialReportTransactionResponse(
        Long id,
        String description,
        TransactionType type,
        BigDecimal amount,
        PaymentStatus paymentStatus,
        LocalDate transactionDate,
        LocalDate dueDate,
        LocalDate paidAt,
        LocalDate referenceDate,
        Long categoryId,
        String categoryName,
        Long harvestSeasonId,
        String harvestSeasonName) {}
