package br.com.gestaodireta.ai.service.dto;

import br.com.gestaodireta.financial.enumeration.PaymentMethod;
import br.com.gestaodireta.financial.enumeration.PaymentStatus;
import br.com.gestaodireta.financial.enumeration.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ParsedTransactionResponse(
        Long farmId,
        TransactionType type,
        BigDecimal amount,
        String description,
        LocalDate transactionDate,
        LocalDate dueDate,
        PaymentStatus paymentStatus,
        PaymentMethod paymentMethod,
        String categoryName,
        String harvestSeasonName,
        BigDecimal confidence,
        List<String> missingFields,
        List<String> warnings) {}
