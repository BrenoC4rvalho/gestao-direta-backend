package br.com.gestaodireta.financial.dto;

import br.com.gestaodireta.harvest.enumeration.ComparisonSemantic;
import java.math.BigDecimal;

public record FinancialHarvestPlanningComparisonResponse(
        BigDecimal planned,
        BigDecimal difference,
        BigDecimal percentageDifference,
        ComparisonSemantic semantic) {}
