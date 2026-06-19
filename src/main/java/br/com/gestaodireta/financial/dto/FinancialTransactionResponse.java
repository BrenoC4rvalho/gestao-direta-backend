package br.com.gestaodireta.financial.dto;

import br.com.gestaodireta.financial.enumeration.FinancialRecordStatus;
import br.com.gestaodireta.financial.enumeration.PaymentMethod;
import br.com.gestaodireta.financial.enumeration.PaymentStatus;
import br.com.gestaodireta.financial.enumeration.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record FinancialTransactionResponse(
        Long id,
        String description,
        BigDecimal amount,
        TransactionType type,
        PaymentStatus status,
        PaymentMethod paymentMethod,
        LocalDate transactionDate,
        LocalDate dueDate,
        LocalDate paidAt,
        String notes,
        Long farmId,
        String farmName,
        Long categoryId,
        String categoryName,
        Long createdByUserId,
        String createdByUserName,
        Long updatedByUserId,
        String updatedByUserName,
        FinancialRecordStatus recordStatus,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {}
