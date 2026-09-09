package br.com.gestaodireta.financial.dto;

import br.com.gestaodireta.financial.enumeration.FinancialCoverageStatus;
import java.math.BigDecimal;

public record FinancialCoverageResponse(
        BigDecimal coveragePercentage, FinancialCoverageStatus status) {}
