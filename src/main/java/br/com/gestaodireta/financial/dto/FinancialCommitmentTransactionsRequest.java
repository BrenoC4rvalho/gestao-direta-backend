package br.com.gestaodireta.financial.dto;

import br.com.gestaodireta.financial.enumeration.FinancialCommitmentSegment;
import java.time.LocalDate;
import java.util.List;

public record FinancialCommitmentTransactionsRequest(
        Long farmId,
        FinancialCommitmentSegment segment,
        LocalDate startDate,
        LocalDate endDate,
        List<Long> categoryIds,
        Long harvestSeasonId) {}
