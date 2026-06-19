package br.com.gestaodireta.farm.mapper;

import br.com.gestaodireta.farm.dto.FarmResponse;
import br.com.gestaodireta.farm.entity.Farm;
import org.springframework.stereotype.Component;

@Component
public class FarmMapper {

    public FarmResponse toResponse(Farm farm) {
        return new FarmResponse(
                farm.getId(),
                farm.getName(),
                farm.getDocument(),
                farm.getCity(),
                farm.getState(),
                farm.getTotalArea(),
                farm.getProductionType(),
                farm.getStatus(),
                farm.getCreatedAt(),
                farm.getUpdatedAt());
    }
}
