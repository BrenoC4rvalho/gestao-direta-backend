package br.com.gestaodireta.ai.service.dto;

import br.com.gestaodireta.financial.enumeration.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record FinancialTransactionExtractionResult(
        boolean isFinancialTransaction,
        TransactionType type,
        BigDecimal amount,
        LocalDate transactionDate,
        String description,
        String categoryName,
        BigDecimal confidence,
        List<String> missingFields) {}
