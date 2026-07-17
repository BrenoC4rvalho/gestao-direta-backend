package br.com.gestaodireta.harvest.dto;

import java.math.BigDecimal;

public record FinancialAmountCountResponse(Long count, BigDecimal totalAmount) {}
