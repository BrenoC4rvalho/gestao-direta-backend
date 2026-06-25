package br.com.gestaodireta.farm.dto;

import br.com.gestaodireta.farm.enumeration.FarmUserRole;
import br.com.gestaodireta.user.enumeration.UserType;

public record FarmAccessResponse(
        Long farmId,
        String farmName,
        Long userId,
        UserType userType,
        FarmUserRole role,
        FarmAccessPermissions permissions) {}
