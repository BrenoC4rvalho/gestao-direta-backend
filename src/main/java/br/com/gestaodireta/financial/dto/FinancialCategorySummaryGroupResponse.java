package br.com.gestaodireta.financial.dto;

import br.com.gestaodireta.financial.enumeration.TransactionType;
import java.math.BigDecimal;
import java.util.List;

public record FinancialCategorySummaryGroupResponse(
        TransactionType type,
        BigDecimal totalAmount,
        long totalTransactionCount,
        List<FinancialCategorySummaryResponse> items) {}
