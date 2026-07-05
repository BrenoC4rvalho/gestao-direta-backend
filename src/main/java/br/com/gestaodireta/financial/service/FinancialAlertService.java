package br.com.gestaodireta.financial.service;

import br.com.gestaodireta.financial.dto.DueBillsSummaryResponse;
import br.com.gestaodireta.financial.dto.FinancialAlertsResponse;
import br.com.gestaodireta.financial.dto.OverdueBillAlertResponse;
import br.com.gestaodireta.financial.repository.DueBillsSummaryProjection;
import br.com.gestaodireta.financial.repository.FinancialTransactionRepository;
import br.com.gestaodireta.financial.repository.OverdueBillAlertProjection;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FinancialAlertService {

    private static final int MAX_OVERDUE_BILLS = 20;

    private final FinancialTransactionRepository financialTransactionRepository;

    private final Clock clock;

    public FinancialAlertService(
            FinancialTransactionRepository financialTransactionRepository, Clock clock) {
        this.financialTransactionRepository = financialTransactionRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public FinancialAlertsResponse getAlerts(Long farmId) {
        LocalDate today = LocalDate.now(clock);
        LocalDate next7Days = today.plusDays(7);

        List<OverdueBillAlertResponse> overdueBills =
                financialTransactionRepository
                        .findOverdueBillAlerts(farmId, today, PageRequest.of(0, MAX_OVERDUE_BILLS))
                        .stream()
                        .map(projection -> toOverdueBillAlertResponse(projection, today))
                        .toList();

        DueBillsSummaryResponse dueToday =
                toDueBillsSummaryResponse(
                        financialTransactionRepository.summarizeDueToday(farmId, today));
        DueBillsSummaryResponse dueNext7Days =
                toDueBillsSummaryResponse(
                        financialTransactionRepository.summarizeDueNext7Days(
                                farmId, today, next7Days));

        return new FinancialAlertsResponse(farmId, overdueBills, dueToday, dueNext7Days);
    }

    private OverdueBillAlertResponse toOverdueBillAlertResponse(
            OverdueBillAlertProjection projection, LocalDate today) {
        return new OverdueBillAlertResponse(
                projection.getTransactionId(),
                projection.getDescription(),
                projection.getCategoryName(),
                projection.getAmount(),
                projection.getDueDate(),
                ChronoUnit.DAYS.between(projection.getDueDate(), today));
    }

    private DueBillsSummaryResponse toDueBillsSummaryResponse(
            DueBillsSummaryProjection projection) {
        return new DueBillsSummaryResponse(
                toInteger(projection.getCount()), zeroIfNull(projection.getTotalAmount()));
    }

    private Integer toInteger(Long value) {
        if (value == null) {
            return 0;
        }

        return Math.toIntExact(value);
    }

    private BigDecimal zeroIfNull(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }

        return value;
    }
}
