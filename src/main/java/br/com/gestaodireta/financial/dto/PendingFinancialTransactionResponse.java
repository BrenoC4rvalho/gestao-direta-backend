package br.com.gestaodireta.financial.dto;

import br.com.gestaodireta.financial.enumeration.PaymentMethod;
import br.com.gestaodireta.financial.enumeration.PendingFinancialTransactionStatus;
import br.com.gestaodireta.financial.enumeration.PendingTransactionSource;
import br.com.gestaodireta.financial.enumeration.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record PendingFinancialTransactionResponse(
        Long id,
        Long farmId,
        String farmName,
        TransactionType type,
        BigDecimal amount,
        LocalDate transactionDate,
        String description,
        Long categoryId,
        String categoryName,
        String rawCategoryName,
        Long harvestSeasonId,
        String harvestSeasonName,
        PaymentMethod paymentMethod,
        String notes,
        PendingFinancialTransactionStatus status,
        BigDecimal confidence,
        PendingTransactionSource sourceChannel,
        Long sourceMessageId,
        String sourceMessageContent,
        LocalDateTime sourceMessageReceivedAt,
        Long requestedByUserId,
        String requestedByUserName,
        Long reviewedByUserId,
        String reviewedByUserName,
        LocalDateTime reviewedAt,
        String rejectionReason,
        Long approvedFinancialTransactionId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {}
