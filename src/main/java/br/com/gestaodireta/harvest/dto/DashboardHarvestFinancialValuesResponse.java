package br.com.gestaodireta.harvest.dto;

import java.math.BigDecimal;

public record DashboardHarvestFinancialValuesResponse(
        BigDecimal cost, BigDecimal revenue, BigDecimal profit) {}
