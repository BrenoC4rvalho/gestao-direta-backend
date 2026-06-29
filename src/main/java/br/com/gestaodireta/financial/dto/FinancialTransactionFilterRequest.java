package br.com.gestaodireta.financial.dto;

import br.com.gestaodireta.financial.enumeration.FinancialRecordStatus;
import br.com.gestaodireta.financial.enumeration.PaymentMethod;
import br.com.gestaodireta.financial.enumeration.PaymentStatus;
import br.com.gestaodireta.financial.enumeration.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;

public record FinancialTransactionFilterRequest(
        Long farmId,
        LocalDate transactionDateStart,
        LocalDate transactionDateEnd,
        LocalDate paidAtStart,
        LocalDate paidAtEnd,
        TransactionType type,
        Long categoryId,
        PaymentStatus paymentStatus,
        PaymentMethod paymentMethod,
        FinancialRecordStatus recordStatus,
        String description,
        Long createdByUserId,
        BigDecimal minAmount,
        BigDecimal maxAmount) {}
