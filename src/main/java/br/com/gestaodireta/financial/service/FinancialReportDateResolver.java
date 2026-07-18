package br.com.gestaodireta.financial.service;

import br.com.gestaodireta.financial.entity.FinancialTransaction;
import br.com.gestaodireta.financial.enumeration.FinancialReportBasis;
import br.com.gestaodireta.financial.enumeration.PaymentStatus;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

@Component
public class FinancialReportDateResolver {

    public LocalDate resolve(FinancialTransaction transaction, FinancialReportBasis basis) {
        if (FinancialReportBasis.ACCRUAL.equals(basis)) {
            return transaction.getTransactionDate();
        }

        if (PaymentStatus.PAID.equals(transaction.getStatus())) {
            return transaction.getPaidAt() == null
                    ? transaction.getTransactionDate()
                    : transaction.getPaidAt();
        }

        return transaction.getDueDate();
    }
}
