package br.com.gestaodireta.financial.dto;

import br.com.gestaodireta.financial.enumeration.FinancialReportBasis;
import java.time.LocalDate;
import java.util.List;

public record FinancialReportResponse(
        Long farmId,
        LocalDate startDate,
        LocalDate endDate,
        FinancialReportBasis basis,
        FinancialReportSummaryResponse summary,
        FinancialReportCommitmentsResponse commitments,
        List<FinancialEvolutionPointResponse> evolution,
        List<FinancialCategorySummaryGroupResponse> categories,
        List<FinancialHarvestSummaryResponse> harvests,
        FinancialReportIndicatorsResponse indicators,
        FinancialReportUnallocatedResponse unallocated) {}
