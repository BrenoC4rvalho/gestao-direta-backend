package br.com.gestaodireta.farm.dto;

import br.com.gestaodireta.farm.enumeration.FarmStatus;
import br.com.gestaodireta.farm.enumeration.ProductionType;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record FarmResponse(
        Long id,
        String name,
        String document,
        String city,
        String state,
        BigDecimal totalArea,
        ProductionType productionType,
        FarmStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {}
