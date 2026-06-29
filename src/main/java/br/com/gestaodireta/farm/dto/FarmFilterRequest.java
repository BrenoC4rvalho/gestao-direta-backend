package br.com.gestaodireta.farm.dto;

import br.com.gestaodireta.farm.enumeration.FarmStatus;
import br.com.gestaodireta.farm.enumeration.ProductionType;
import java.util.List;

public record FarmFilterRequest(
        String search,
        String document,
        ProductionType productionType,
        List<ProductionType> productionTypes,
        FarmStatus status) {

    public FarmFilterRequest(
            String search, String document, ProductionType productionType, FarmStatus status) {
        this(search, document, productionType, null, status);
    }
}
