package br.com.gestaodireta.financial.dto;

import br.com.gestaodireta.financial.enumeration.FinancialReportBasis;
import br.com.gestaodireta.financial.enumeration.FinancialReportGranularity;
import java.time.LocalDate;
import java.util.List;

public record FinancialReportFilter(
        Long farmId,
        LocalDate startDate,
        LocalDate endDate,
        FinancialReportBasis basis,
        List<Long> harvestSeasonIds,
        List<Long> categoryIds,
        FinancialReportGranularity granularity) {

    public FinancialReportFilter(
            Long farmId,
            LocalDate startDate,
            LocalDate endDate,
            FinancialReportBasis basis,
            List<Long> harvestSeasonIds,
            List<Long> categoryIds) {
        this(
                farmId,
                startDate,
                endDate,
                basis,
                harvestSeasonIds,
                categoryIds,
                FinancialReportGranularity.MONTHLY);
    }
}
