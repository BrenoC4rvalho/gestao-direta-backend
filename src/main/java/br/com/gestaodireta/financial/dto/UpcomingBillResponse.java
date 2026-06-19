package br.com.gestaodireta.financial.dto;

import br.com.gestaodireta.financial.enumeration.PaymentStatus;
import java.math.BigDecimal;
import java.time.LocalDate;

public record UpcomingBillResponse(
        Long id,
        String description,
        BigDecimal amount,
        PaymentStatus status,
        LocalDate dueDate,
        Long farmId,
        Long categoryId,
        String categoryName) {}
