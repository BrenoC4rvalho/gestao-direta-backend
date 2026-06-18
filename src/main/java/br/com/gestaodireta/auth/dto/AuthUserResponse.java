package br.com.gestaodireta.auth.dto;

import br.com.gestaodireta.user.enumeration.UserStatus;
import br.com.gestaodireta.user.enumeration.UserType;

public record AuthUserResponse(
        Long id,
        String name,
        String email,
        String document,
        UserType userType,
        UserStatus status) {}
