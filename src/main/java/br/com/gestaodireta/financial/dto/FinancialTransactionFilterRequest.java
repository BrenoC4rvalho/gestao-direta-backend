package br.com.gestaodireta.financial.dto;

import br.com.gestaodireta.financial.enumeration.FinancialRecordStatus;
import br.com.gestaodireta.financial.enumeration.PaymentMethod;
import br.com.gestaodireta.financial.enumeration.PaymentStatus;
import br.com.gestaodireta.financial.enumeration.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record FinancialTransactionFilterRequest(
        Long farmId,
        LocalDate transactionDateStart,
        LocalDate transactionDateEnd,
        LocalDate paidAtStart,
        LocalDate paidAtEnd,
        TransactionType type,
        Long categoryId,
        List<Long> categoryIds,
        Long harvestSeasonId,
        PaymentStatus paymentStatus,
        List<PaymentStatus> paymentStatuses,
        PaymentMethod paymentMethod,
        List<PaymentMethod> paymentMethods,
        FinancialRecordStatus recordStatus,
        String description,
        Long createdByUserId,
        BigDecimal minAmount,
        BigDecimal maxAmount) {

    public FinancialTransactionFilterRequest(
            Long farmId,
            LocalDate transactionDateStart,
            LocalDate transactionDateEnd,
            LocalDate paidAtStart,
            LocalDate paidAtEnd,
            TransactionType type,
            Long categoryId,
            Long harvestSeasonId,
            PaymentStatus paymentStatus,
            PaymentMethod paymentMethod,
            FinancialRecordStatus recordStatus,
            String description,
            Long createdByUserId,
            BigDecimal minAmount,
            BigDecimal maxAmount) {
        this(
                farmId,
                transactionDateStart,
                transactionDateEnd,
                paidAtStart,
                paidAtEnd,
                type,
                categoryId,
                null,
                harvestSeasonId,
                paymentStatus,
                null,
                paymentMethod,
                null,
                recordStatus,
                description,
                createdByUserId,
                minAmount,
                maxAmount);
    }
}
