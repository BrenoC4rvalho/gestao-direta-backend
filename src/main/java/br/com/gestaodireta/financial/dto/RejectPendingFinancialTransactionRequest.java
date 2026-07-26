package br.com.gestaodireta.financial.dto;

import jakarta.validation.constraints.Size;

public record RejectPendingFinancialTransactionRequest(@Size(max = 500) String reason) {}
