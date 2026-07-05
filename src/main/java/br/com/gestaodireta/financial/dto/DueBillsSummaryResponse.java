package br.com.gestaodireta.financial.dto;

import java.math.BigDecimal;

public record DueBillsSummaryResponse(Integer count, BigDecimal totalAmount) {}
