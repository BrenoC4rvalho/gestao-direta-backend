package br.com.gestaodireta.financial.dto;

import java.math.BigDecimal;

public record FinancialReportUnallocatedResponse(
        BigDecimal income, BigDecimal expense, long transactionCount) {}
