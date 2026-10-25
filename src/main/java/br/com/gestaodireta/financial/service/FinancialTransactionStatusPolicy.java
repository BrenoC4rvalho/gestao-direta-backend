package br.com.gestaodireta.financial.service;

import br.com.gestaodireta.financial.enumeration.PaymentStatus;
import br.com.gestaodireta.shared.exception.BusinessException;
import java.time.LocalDate;

final class FinancialTransactionStatusPolicy {

    void validateUpdate(PaymentStatus current, PaymentStatus requested) {
        requireStatus(requested);

        if (current == PaymentStatus.CANCELED) {
            throw new BusinessException("Canceled transactions cannot be updated");
        }
        if (current == PaymentStatus.PAID && requested != PaymentStatus.PAID) {
            throw new BusinessException("Use the reopen or cancel action for a paid transaction");
        }
        if (requested == PaymentStatus.CANCELED) {
            throw new BusinessException("Use the cancel action to cancel a transaction");
        }
    }

    void validatePay(PaymentStatus current) {
        if (current != PaymentStatus.PENDING && current != PaymentStatus.OVERDUE) {
            throw new BusinessException("Only pending or overdue transactions can be paid");
        }
    }

    void validateCancel(PaymentStatus current) {
        if (current == PaymentStatus.CANCELED) {
            throw new BusinessException("Transaction is already canceled");
        }
    }

    void validateReopen(PaymentStatus current) {
        if (current != PaymentStatus.PAID) {
            throw new BusinessException("Only paid transactions can be reopened");
        }
    }

    PaymentStatus reopenedStatus(LocalDate dueDate, LocalDate today) {
        return dueDate != null && dueDate.isBefore(today)
                ? PaymentStatus.OVERDUE
                : PaymentStatus.PENDING;
    }

    private void requireStatus(PaymentStatus status) {
        if (status == null) {
            throw new BusinessException("Transaction status is required");
        }
    }
}
