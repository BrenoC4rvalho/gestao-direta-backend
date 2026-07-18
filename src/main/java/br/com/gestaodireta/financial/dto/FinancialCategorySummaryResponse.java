package br.com.gestaodireta.financial.dto;

import br.com.gestaodireta.financial.enumeration.TransactionType;
import java.math.BigDecimal;

public record FinancialCategorySummaryResponse(
        Long categoryId,
        String categoryName,
        TransactionType type,
        BigDecimal amount,
        BigDecimal percentage,
        long transactionCount) {}
