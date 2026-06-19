package br.com.gestaodireta.financial.dto;

import br.com.gestaodireta.financial.enumeration.FinancialCategoryStatus;
import br.com.gestaodireta.financial.enumeration.TransactionType;
import java.time.LocalDateTime;

public record FinancialCategoryResponse(
        Long id,
        String name,
        TransactionType type,
        String color,
        String icon,
        Long farmId,
        String farmName,
        boolean isDefault,
        FinancialCategoryStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {}
