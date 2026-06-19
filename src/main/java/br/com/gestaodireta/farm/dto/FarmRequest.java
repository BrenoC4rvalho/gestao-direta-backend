package br.com.gestaodireta.farm.dto;

import br.com.gestaodireta.farm.enumeration.ProductionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record FarmRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 30) String document,
        @Size(max = 100) String city,
        @Size(max = 2) String state,
        BigDecimal totalArea,
        ProductionType productionType) {}
