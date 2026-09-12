package br.com.gestaodireta.financial.dto;

import br.com.gestaodireta.harvest.enumeration.ComparisonSemantic;
import java.math.BigDecimal;

public record FinancialReportComparisonMetricResponse(
        BigDecimal currentValue,
        BigDecimal previousValue,
        BigDecimal difference,
        BigDecimal percentageDifference,
        ComparisonSemantic semantic) {}
