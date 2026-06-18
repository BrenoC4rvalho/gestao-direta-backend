package br.com.gestaodireta.user.dto;

import br.com.gestaodireta.user.enumeration.UserStatus;
import br.com.gestaodireta.user.enumeration.UserType;
import java.time.LocalDateTime;

public record UserResponse(
        Long id,
        String name,
        String email,
        String document,
        UserType userType,
        UserStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {}
