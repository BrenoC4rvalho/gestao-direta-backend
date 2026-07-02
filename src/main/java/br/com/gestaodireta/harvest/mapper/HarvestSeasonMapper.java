package br.com.gestaodireta.harvest.mapper;

import br.com.gestaodireta.harvest.dto.HarvestSeasonResponse;
import br.com.gestaodireta.harvest.entity.HarvestSeason;
import org.springframework.stereotype.Component;

@Component
public class HarvestSeasonMapper {

    public HarvestSeasonResponse toResponse(HarvestSeason harvestSeason) {
        return new HarvestSeasonResponse(
                harvestSeason.getId(),
                harvestSeason.getFarm().getId(),
                harvestSeason.getFarm().getName(),
                harvestSeason.getProductionActivity().getId(),
                harvestSeason.getProductionActivity().getName(),
                harvestSeason.getName(),
                harvestSeason.getDescription(),
                harvestSeason.getStartDate(),
                harvestSeason.getEndDate(),
                harvestSeason.getExpectedRevenue(),
                harvestSeason.getExpectedCost(),
                harvestSeason.getAreaHectares(),
                harvestSeason.getStatus(),
                harvestSeason.getCreatedAt(),
                harvestSeason.getUpdatedAt());
    }
}
