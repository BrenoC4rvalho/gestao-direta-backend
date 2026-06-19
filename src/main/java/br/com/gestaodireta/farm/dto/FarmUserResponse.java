package br.com.gestaodireta.farm.dto;

import br.com.gestaodireta.farm.enumeration.FarmUserRole;
import java.time.LocalDateTime;

public record FarmUserResponse(
        Long id,
        Long farmId,
        String farmName,
        Long userId,
        String userName,
        String userEmail,
        FarmUserRole role,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {}
