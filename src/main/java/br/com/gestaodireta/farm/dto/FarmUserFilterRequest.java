package br.com.gestaodireta.farm.dto;

import br.com.gestaodireta.farm.enumeration.FarmUserRole;

public record FarmUserFilterRequest(String search, FarmUserRole role) {}
