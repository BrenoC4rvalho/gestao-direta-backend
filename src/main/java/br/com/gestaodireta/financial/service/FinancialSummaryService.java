package br.com.gestaodireta.financial.service;

import br.com.gestaodireta.financial.dto.FinancialSummaryResponse;
import br.com.gestaodireta.financial.dto.UpcomingBillResponse;
import br.com.gestaodireta.financial.entity.FinancialCategory;
import br.com.gestaodireta.financial.entity.FinancialTransaction;
import br.com.gestaodireta.financial.enumeration.FinancialRecordStatus;
import br.com.gestaodireta.financial.enumeration.PaymentStatus;
import br.com.gestaodireta.financial.enumeration.TransactionType;
import br.com.gestaodireta.financial.repository.FinancialTransactionRepository;
import br.com.gestaodireta.shared.pagination.PaginationParams;
import br.com.gestaodireta.shared.response.PageResponse;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FinancialSummaryService {

    private final FinancialTransactionRepository financialTransactionRepository;

    public FinancialSummaryService(FinancialTransactionRepository financialTransactionRepository) {
        this.financialTransactionRepository = financialTransactionRepository;
    }

    @Transactional(readOnly = true)
    public FinancialSummaryResponse summarize(Long farmId) {
        BigDecimal incomeTotal =
                financialTransactionRepository.sumByFarmAndType(
                        farmId,
                        FinancialRecordStatus.ACTIVE,
                        PaymentStatus.CANCELED,
                        TransactionType.INCOME);
        BigDecimal expenseTotal =
                financialTransactionRepository.sumByFarmAndType(
                        farmId,
                        FinancialRecordStatus.ACTIVE,
                        PaymentStatus.CANCELED,
                        TransactionType.EXPENSE);
        BigDecimal pendingTotal =
                financialTransactionRepository.sumByFarmAndPaymentStatus(
                        farmId, FinancialRecordStatus.ACTIVE, PaymentStatus.PENDING);
        BigDecimal paidTotal =
                financialTransactionRepository.sumByFarmAndPaymentStatus(
                        farmId, FinancialRecordStatus.ACTIVE, PaymentStatus.PAID);
        BigDecimal overdueTotal =
                financialTransactionRepository.sumByFarmAndPaymentStatus(
                        farmId, FinancialRecordStatus.ACTIVE, PaymentStatus.OVERDUE);

        return new FinancialSummaryResponse(
                farmId,
                incomeTotal,
                expenseTotal,
                incomeTotal.subtract(expenseTotal),
                pendingTotal,
                paidTotal,
                overdueTotal);
    }

    @Transactional(readOnly = true)
    public PageResponse<UpcomingBillResponse> findUpcomingBills(
            Long farmId, PaginationParams paginationParams) {
        Page<UpcomingBillResponse> bills =
                financialTransactionRepository
                        .findByFarmIdAndTypeAndRecordStatusAndStatusInAndDueDateIsNotNull(
                                farmId,
                                TransactionType.EXPENSE,
                                FinancialRecordStatus.ACTIVE,
                                List.of(PaymentStatus.PENDING, PaymentStatus.OVERDUE),
                                paginationParams.toPageable())
                        .map(this::toUpcomingBillResponse);

        return PageResponse.from(bills);
    }

    private UpcomingBillResponse toUpcomingBillResponse(FinancialTransaction transaction) {
        FinancialCategory category = transaction.getCategory();

        return new UpcomingBillResponse(
                transaction.getId(),
                transaction.getDescription(),
                transaction.getAmount(),
                transaction.getStatus(),
                transaction.getDueDate(),
                transaction.getFarm().getId(),
                category == null ? null : category.getId(),
                category == null ? null : category.getName());
    }
}
