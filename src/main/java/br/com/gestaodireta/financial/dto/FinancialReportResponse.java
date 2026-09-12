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
        FinancialReportComparisonResponse comparison,
        FinancialReportCommitmentsResponse commitments,
        List<FinancialEvolutionPointResponse> evolution,
        List<FinancialCumulativeEvolutionPointResponse> realizedCumulativeEvolution,
        FinancialCashFlowResponse cashFlow,
        List<FinancialCategorySummaryGroupResponse> categories,
        List<FinancialHarvestSummaryResponse> harvests,
        FinancialIndicatorsResponse financialIndicators,
        FinancialReportIndicatorsResponse indicators,
        FinancialReportUnallocatedResponse unallocated) {}
