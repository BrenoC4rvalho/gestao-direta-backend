package br.com.gestaodireta.harvest.dto;

import br.com.gestaodireta.harvest.enumeration.ComparisonSemantic;
import br.com.gestaodireta.harvest.enumeration.PlanningComparisonDifferenceUnit;
import br.com.gestaodireta.harvest.enumeration.PlanningComparisonPosition;
import java.math.BigDecimal;

public record PlanningComparisonMetricResponse(
        BigDecimal planned,
        BigDecimal current,
        BigDecimal difference,
        BigDecimal percentageDifference,
        PlanningComparisonPosition position,
        ComparisonSemantic semantic,
        PlanningComparisonDifferenceUnit differenceUnit) {}
