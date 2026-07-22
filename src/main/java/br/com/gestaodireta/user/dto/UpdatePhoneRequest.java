package br.com.gestaodireta.user.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdatePhoneRequest(@NotBlank String phoneNumber) {}
