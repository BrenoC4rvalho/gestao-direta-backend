package br.com.gestaodireta.harvest.dto;

import br.com.gestaodireta.harvest.enumeration.ProductionActivityStatus;
import java.time.LocalDateTime;

public record ProductionActivityResponse(
        Long id,
        String name,
        String description,
        ProductionActivityStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {}
