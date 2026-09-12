package br.com.gestaodireta.harvest.dto;

import java.math.BigDecimal;

public record HarvestSeasonCommitmentsResponse(
        BigDecimal receivableAmount, BigDecimal payableAmount, BigDecimal overduePayableAmount) {}
