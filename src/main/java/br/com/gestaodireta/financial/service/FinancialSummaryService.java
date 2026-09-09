package br.com.gestaodireta.financial.service;

import br.com.gestaodireta.farm.repository.FarmRepository;
import br.com.gestaodireta.financial.dto.CashFlowPointResponse;
import br.com.gestaodireta.financial.dto.CashFlowResponse;
import br.com.gestaodireta.financial.dto.FinancialCoverageResponse;
import br.com.gestaodireta.financial.dto.FinancialSummaryResponse;
import br.com.gestaodireta.financial.dto.UpcomingBillResponse;
import br.com.gestaodireta.financial.entity.FinancialCategory;
import br.com.gestaodireta.financial.entity.FinancialTransaction;
import br.com.gestaodireta.financial.enumeration.FinancialCoverageStatus;
import br.com.gestaodireta.financial.enumeration.FinancialRecordStatus;
import br.com.gestaodireta.financial.enumeration.PaymentStatus;
import br.com.gestaodireta.financial.enumeration.TransactionType;
import br.com.gestaodireta.financial.repository.CashFlowAmountProjection;
import br.com.gestaodireta.financial.repository.FinancialSummaryProjection;
import br.com.gestaodireta.financial.repository.FinancialTransactionRepository;
import br.com.gestaodireta.financial.repository.MonthlyCashFlowProjection;
import br.com.gestaodireta.shared.exception.BusinessException;
import br.com.gestaodireta.shared.exception.ResourceNotFoundException;
import br.com.gestaodireta.shared.pagination.PaginationParams;
import br.com.gestaodireta.shared.response.PageResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Year;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FinancialSummaryService {

    private final FinancialTransactionRepository financialTransactionRepository;

    private final FarmRepository farmRepository;

    private final Clock clock;

    public FinancialSummaryService(
            FinancialTransactionRepository financialTransactionRepository,
            FarmRepository farmRepository,
            Clock clock) {
        this.financialTransactionRepository = financialTransactionRepository;
        this.farmRepository = farmRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public FinancialSummaryResponse summarize(Long farmId, int horizonDays) {
        if (horizonDays != 30 && horizonDays != 90 && horizonDays != 180) {
            throw new BusinessException("O horizonte financeiro deve ser 30, 90 ou 180 dias.");
        }

        LocalDate today = LocalDate.now(clock);
        FinancialSummaryProjection summary =
                financialTransactionRepository.summarizeFinancialDashboard(
                        farmId, today, today.plusDays(horizonDays));
        BigDecimal currentBalance =
                zeroIfNull(summary.getPaidIncome()).subtract(zeroIfNull(summary.getPaidExpense()));
        BigDecimal receivable = zeroIfNull(summary.getReceivableInHorizon());
        BigDecimal payable = zeroIfNull(summary.getPayableInHorizon());
        BigDecimal overdue = zeroIfNull(summary.getOverdueExpenses());
        BigDecimal resources = currentBalance.add(receivable);
        BigDecimal obligations = overdue.add(payable);

        return new FinancialSummaryResponse(
                farmId,
                currentBalance,
                zeroIfNull(summary.getExpectedIncome()),
                zeroIfNull(summary.getExpectedExpense()),
                overdue,
                horizonDays,
                receivable,
                payable,
                resources.subtract(obligations),
                calculateCoverage(resources, obligations));
    }

    private FinancialCoverageResponse calculateCoverage(
            BigDecimal resources, BigDecimal obligations) {
        if (obligations.signum() == 0) {
            return new FinancialCoverageResponse(null, FinancialCoverageStatus.NO_OBLIGATIONS);
        }

        BigDecimal percentage =
                resources
                        .multiply(BigDecimal.valueOf(100))
                        .divide(obligations, 2, RoundingMode.HALF_UP);
        FinancialCoverageStatus status =
                resources.compareTo(obligations) >= 0
                        ? FinancialCoverageStatus.SUFFICIENT
                        : FinancialCoverageStatus.INSUFFICIENT;
        return new FinancialCoverageResponse(percentage, status);
    }

    @Transactional(readOnly = true)
    public CashFlowResponse getCashFlow(Long farmId, Integer requestedYear) {
        validateFarmExists(farmId);

        validateYear(requestedYear);
        int year = requestedYear;

        LocalDate startDate = Year.of(year).atDay(1);
        LocalDate endDate = startDate.plusYears(1);
        CashFlowAmountProjection openingAmounts =
                financialTransactionRepository.summarizeCashFlowOpeningBalance(farmId, startDate);
        BigDecimal openingBalance = calculateBalance(openingAmounts);
        Map<Integer, CashFlowAmount> amountsByMonth =
                toMonthlyAmounts(
                        financialTransactionRepository.summarizeMonthlyCashFlow(
                                farmId, startDate, endDate));
        addToJanuary(
                amountsByMonth,
                financialTransactionRepository.summarizeOpenCashFlowBeforeYear(farmId, startDate));

        List<CashFlowPointResponse> points = new ArrayList<>();
        BigDecimal balance = openingBalance;
        for (int month = 1; month <= 12; month++) {
            CashFlowAmount amount = amountsByMonth.getOrDefault(month, CashFlowAmount.ZERO);
            BigDecimal netFlow = amount.income().subtract(amount.expense());
            balance = balance.add(netFlow);
            points.add(
                    new CashFlowPointResponse(
                            month,
                            monthLabel(month),
                            amount.income(),
                            amount.expense(),
                            netFlow,
                            balance));
        }

        return new CashFlowResponse(farmId, year, openingBalance, balance, points);
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

    private void validateFarmExists(Long farmId) {
        if (!farmRepository.existsById(farmId)) {
            throw new ResourceNotFoundException("Farm not found");
        }
    }

    private void validateYear(Integer year) {
        if (year == null || year <= 0) {
            throw new BusinessException("O ano deve ser informado e deve ser maior que zero.");
        }
    }

    private BigDecimal calculateBalance(CashFlowAmountProjection amounts) {
        return zeroIfNull(amounts.getIncome()).subtract(zeroIfNull(amounts.getExpense()));
    }

    private Map<Integer, CashFlowAmount> toMonthlyAmounts(
            List<MonthlyCashFlowProjection> projections) {
        Map<Integer, CashFlowAmount> amountsByMonth = new HashMap<>();
        for (MonthlyCashFlowProjection projection : projections) {
            amountsByMonth.put(
                    projection.getMonth(),
                    new CashFlowAmount(
                            zeroIfNull(projection.getIncome()),
                            zeroIfNull(projection.getExpense())));
        }
        return amountsByMonth;
    }

    private void addToJanuary(
            Map<Integer, CashFlowAmount> amountsByMonth, CashFlowAmountProjection amounts) {
        CashFlowAmount january = amountsByMonth.getOrDefault(1, CashFlowAmount.ZERO);
        amountsByMonth.put(
                1,
                new CashFlowAmount(
                        january.income().add(zeroIfNull(amounts.getIncome())),
                        january.expense().add(zeroIfNull(amounts.getExpense()))));
    }

    private String monthLabel(int month) {
        return switch (month) {
            case 1 -> "Jan";
            case 2 -> "Fev";
            case 3 -> "Mar";
            case 4 -> "Abr";
            case 5 -> "Mai";
            case 6 -> "Jun";
            case 7 -> "Jul";
            case 8 -> "Ago";
            case 9 -> "Set";
            case 10 -> "Out";
            case 11 -> "Nov";
            case 12 -> "Dez";
            default -> throw new IllegalArgumentException("Invalid month");
        };
    }

    private BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private record CashFlowAmount(BigDecimal income, BigDecimal expense) {

        private static final CashFlowAmount ZERO =
                new CashFlowAmount(BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
