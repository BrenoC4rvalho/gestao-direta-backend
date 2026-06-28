package br.com.gestaodireta.user.dto;

import br.com.gestaodireta.user.enumeration.UserStatus;
import br.com.gestaodireta.user.enumeration.UserType;

public record UserFilterRequest(String search, UserType userType, UserStatus status) {}
