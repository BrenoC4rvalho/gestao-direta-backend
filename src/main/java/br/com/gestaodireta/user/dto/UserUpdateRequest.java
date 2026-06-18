package br.com.gestaodireta.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserUpdateRequest(
        @NotBlank @Size(max = 120) String name, @Size(max = 20) String document) {}
