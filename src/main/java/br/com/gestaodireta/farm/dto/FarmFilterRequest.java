package br.com.gestaodireta.farm.dto;

import br.com.gestaodireta.farm.enumeration.FarmStatus;
import br.com.gestaodireta.farm.enumeration.ProductionType;

public record FarmFilterRequest(
        String search, String document, ProductionType productionType, FarmStatus status) {}
