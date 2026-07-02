package br.com.gestaodireta.harvest.mapper;

import br.com.gestaodireta.harvest.dto.ProductionActivityResponse;
import br.com.gestaodireta.harvest.entity.ProductionActivity;
import org.springframework.stereotype.Component;

@Component
public class ProductionActivityMapper {

    public ProductionActivityResponse toResponse(ProductionActivity productionActivity) {
        return new ProductionActivityResponse(
                productionActivity.getId(),
                productionActivity.getName(),
                productionActivity.getDescription(),
                productionActivity.getStatus(),
                productionActivity.getCreatedAt(),
                productionActivity.getUpdatedAt());
    }
}
