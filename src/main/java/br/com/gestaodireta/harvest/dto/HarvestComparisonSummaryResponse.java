package br.com.gestaodireta.harvest.dto;

import br.com.gestaodireta.harvest.enumeration.CostVarianceStatus;
import br.com.gestaodireta.harvest.enumeration.ProfitPerformanceStatus;
import java.math.BigDecimal;

public record HarvestComparisonSummaryResponse(
        BigDecimal profitPerformanceAmount,
        BigDecimal profitPerformancePercentage,
        ProfitPerformanceStatus profitPerformanceStatus,
        BigDecimal costVarianceAmount,
        BigDecimal costVariancePercentage,
        CostVarianceStatus costVarianceStatus) {}
