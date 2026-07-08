package br.com.gestaodireta.financial.dto;

import br.com.gestaodireta.financial.enumeration.FinancialAgendaStatus;
import br.com.gestaodireta.financial.enumeration.FinancialAgendaType;
import br.com.gestaodireta.financial.enumeration.PaymentStatus;
import br.com.gestaodireta.financial.enumeration.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;

public record FinancialAgendaItemResponse(
        Long id,
        Long farmId,
        String description,
        FinancialAgendaType agendaType,
        TransactionType transactionType,
        FinancialAgendaStatus agendaStatus,
        PaymentStatus paymentStatus,
        BigDecimal amount,
        LocalDate dueDate,
        Long daysOverdue,
        Long daysUntilDue,
        Long categoryId,
        String categoryName,
        Long harvestSeasonId,
        String harvestSeasonName) {}
