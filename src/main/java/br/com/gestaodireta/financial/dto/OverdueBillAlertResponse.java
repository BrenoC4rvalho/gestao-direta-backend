package br.com.gestaodireta.financial.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record OverdueBillAlertResponse(
        Long transactionId,
        String description,
        String categoryName,
        BigDecimal amount,
        LocalDate dueDate,
        Long daysOverdue) {}
