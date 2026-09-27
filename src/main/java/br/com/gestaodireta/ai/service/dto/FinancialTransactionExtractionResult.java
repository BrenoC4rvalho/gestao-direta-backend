package br.com.gestaodireta.ai.service.dto;

import br.com.gestaodireta.financial.enumeration.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record FinancialTransactionExtractionResult(
        FinancialTransactionExtractionStatus status,
        boolean isFinancialTransaction,
        TransactionType type,
        BigDecimal amount,
        LocalDate transactionDate,
        String description,
        String categoryName,
        BigDecimal confidence,
        List<String> missingFields) {

    public FinancialTransactionExtractionResult(
            boolean isFinancialTransaction,
            TransactionType type,
            BigDecimal amount,
            LocalDate transactionDate,
            String description,
            String categoryName,
            BigDecimal confidence,
            List<String> missingFields) {
        this(
                isFinancialTransaction
                        ? FinancialTransactionExtractionStatus.VALID
                        : FinancialTransactionExtractionStatus.INVALID,
                isFinancialTransaction,
                type,
                amount,
                transactionDate,
                description,
                categoryName,
                confidence,
                missingFields);
    }
}
