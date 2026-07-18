package br.com.gestaodireta.financial.dto;

import br.com.gestaodireta.financial.enumeration.FinancialReportBasis;
import java.time.LocalDate;
import java.util.List;

public record FinancialReportFilter(
        Long farmId,
        LocalDate startDate,
        LocalDate endDate,
        FinancialReportBasis basis,
        List<Long> harvestSeasonIds,
        List<Long> categoryIds) {}
