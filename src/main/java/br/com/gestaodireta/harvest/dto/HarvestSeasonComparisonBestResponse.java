package br.com.gestaodireta.harvest.dto;

import br.com.gestaodireta.harvest.enumeration.HarvestSeasonComparisonMetric;
import java.util.List;

public record HarvestSeasonComparisonBestResponse(
        HarvestSeasonComparisonMetric metric, List<Long> harvestSeasonIds) {}
