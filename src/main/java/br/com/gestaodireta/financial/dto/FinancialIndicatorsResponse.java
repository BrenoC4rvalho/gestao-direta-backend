package br.com.gestaodireta.financial.dto;

public record FinancialIndicatorsResponse(
        FinancialResultIndicatorsResponse result,
        FinancialLiquidityIndicatorsResponse liquidity,
        FinancialEfficiencyIndicatorsResponse efficiency,
        FinancialRuralManagementIndicatorsResponse ruralManagement,
        FinancialPlanningIndicatorsResponse planning) {}
