package br.com.gestaodireta.harvest.dto;

import java.util.List;

public record HarvestSeasonComparisonResponse(
        HarvestSeasonComparisonHarvestResponse harvestA,
        HarvestSeasonComparisonHarvestResponse harvestB,
        List<HarvestSeasonComparisonDifferenceResponse> differences,
        List<HarvestSeasonComparisonDifferenceResponse> highlights) {}
