package br.com.gestaodireta.financial.dto;

import br.com.gestaodireta.financial.enumeration.PaymentMethod;
import java.time.LocalDate;

public record PayTransactionRequest(LocalDate paidAt, PaymentMethod paymentMethod) {}
