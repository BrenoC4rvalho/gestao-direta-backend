package br.com.gestaodireta.harvest.dto;

import br.com.gestaodireta.harvest.enumeration.ComparisonSemantic;
import br.com.gestaodireta.harvest.enumeration.HarvestSeasonComparisonMetric;
import java.math.BigDecimal;

public record HarvestSeasonComparisonDifferenceResponse(
        HarvestSeasonComparisonMetric metric,
        BigDecimal difference,
        BigDecimal percentageDifference,
        ComparisonSemantic semantic) {}
