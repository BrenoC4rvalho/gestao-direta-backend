package br.com.gestaodireta.harvest.dto;

import java.math.BigDecimal;

public record HarvestSeasonPerHectareSummaryResponse(
        BigDecimal cost, BigDecimal revenue, BigDecimal result) {}
