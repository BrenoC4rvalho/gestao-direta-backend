package br.com.gestaodireta.financial.service;

import br.com.gestaodireta.financial.dto.FinancialSummaryResponse;
import br.com.gestaodireta.financial.dto.UpcomingBillResponse;
import br.com.gestaodireta.financial.entity.FinancialCategory;
import br.com.gestaodireta.financial.entity.FinancialTransaction;
import br.com.gestaodireta.financial.enumeration.FinancialRecordStatus;
import br.com.gestaodireta.financial.enumeration.PaymentStatus;
import br.com.gestaodireta.financial.enumeration.TransactionType;
import br.com.gestaodireta.financial.repository.FinancialSummaryProjection;
import br.com.gestaodireta.financial.repository.FinancialTransactionRepository;
import br.com.gestaodireta.shared.pagination.PaginationParams;
import br.com.gestaodireta.shared.response.PageResponse;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FinancialSummaryService {

    private final FinancialTransactionRepository financialTransactionRepository;

    private final Clock clock;

    public FinancialSummaryService(
            FinancialTransactionRepository financialTransactionRepository, Clock clock) {
        this.financialTransactionRepository = financialTransactionRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public FinancialSummaryResponse summarize(Long farmId) {
        LocalDate today = LocalDate.now(clock);
        LocalDate next30Days = today.plusDays(30);
        FinancialSummaryProjection summary =
                financialTransactionRepository.summarizeFinancialDashboard(
                        farmId, today, next30Days);
        BigDecimal paidIncome = zeroIfNull(summary.getPaidIncome());
        BigDecimal paidExpense = zeroIfNull(summary.getPaidExpense());
        BigDecimal expectedIncome = zeroIfNull(summary.getExpectedIncome());
        BigDecimal expectedExpense = zeroIfNull(summary.getExpectedExpense());
        BigDecimal payableNext30Days = zeroIfNull(summary.getPayableNext30Days());
        BigDecimal overdueExpenses = zeroIfNull(summary.getOverdueExpenses());
        BigDecimal receivableNext30Days =
                zeroIfNull(summary.getOverdueIncome())
                        .add(zeroIfNull(summary.getReceivablePendingNext30Days()));
        BigDecimal currentBalance = paidIncome.subtract(paidExpense);
        BigDecimal projectedBalance = currentBalance.add(expectedIncome).subtract(expectedExpense);
        BigDecimal cashFlowNext30Days =
                receivableNext30Days.subtract(payableNext30Days).subtract(overdueExpenses);

        return new FinancialSummaryResponse(
                farmId,
                currentBalance,
                expectedIncome,
                expectedExpense,
                projectedBalance,
                payableNext30Days,
                overdueExpenses,
                receivableNext30Days,
                cashFlowNext30Days);
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

    private BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
