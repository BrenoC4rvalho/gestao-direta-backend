package br.com.gestaodireta.harvest.dto;

import br.com.gestaodireta.harvest.enumeration.PlanningComparisonBasis;
import br.com.gestaodireta.harvest.enumeration.PlanningComparisonState;

public record HarvestPlanningComparisonResponse(
        PlanningComparisonState state,
        PlanningComparisonBasis basis,
        PlanningComparisonMetricResponse cost,
        PlanningComparisonMetricResponse revenue,
        PlanningComparisonMetricResponse profit,
        PlanningComparisonMetricResponse margin) {}
