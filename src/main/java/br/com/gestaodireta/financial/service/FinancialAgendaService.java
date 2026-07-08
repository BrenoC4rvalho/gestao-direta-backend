package br.com.gestaodireta.financial.service;

import br.com.gestaodireta.farm.entity.Farm;
import br.com.gestaodireta.farm.service.FarmService;
import br.com.gestaodireta.financial.dto.FinancialAgendaAmountSummary;
import br.com.gestaodireta.financial.dto.FinancialAgendaFilter;
import br.com.gestaodireta.financial.dto.FinancialAgendaItemResponse;
import br.com.gestaodireta.financial.dto.FinancialAgendaSummaryResponse;
import br.com.gestaodireta.financial.entity.FinancialCategory;
import br.com.gestaodireta.financial.entity.FinancialTransaction;
import br.com.gestaodireta.financial.enumeration.FinancialAgendaStatus;
import br.com.gestaodireta.financial.enumeration.FinancialAgendaStatusFilter;
import br.com.gestaodireta.financial.enumeration.FinancialAgendaType;
import br.com.gestaodireta.financial.enumeration.FinancialAgendaTypeFilter;
import br.com.gestaodireta.financial.enumeration.PaymentStatus;
import br.com.gestaodireta.financial.enumeration.TransactionType;
import br.com.gestaodireta.financial.repository.FinancialTransactionRepository;
import br.com.gestaodireta.harvest.entity.HarvestSeason;
import br.com.gestaodireta.harvest.repository.HarvestSeasonRepository;
import br.com.gestaodireta.shared.exception.BusinessException;
import br.com.gestaodireta.shared.exception.ResourceNotFoundException;
import br.com.gestaodireta.shared.exception.ValidationException;
import br.com.gestaodireta.shared.response.PageResponse;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FinancialAgendaService {

    private final FinancialTransactionRepository financialTransactionRepository;

    private final FarmService farmService;

    private final HarvestSeasonRepository harvestSeasonRepository;

    private final Clock clock;

    public FinancialAgendaService(
            FinancialTransactionRepository financialTransactionRepository,
            FarmService farmService,
            HarvestSeasonRepository harvestSeasonRepository,
            Clock clock) {
        this.financialTransactionRepository = financialTransactionRepository;
        this.farmService = farmService;
        this.harvestSeasonRepository = harvestSeasonRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public FinancialAgendaSummaryResponse summarize(FinancialAgendaFilter filter) {
        FinancialAgendaFilter normalizedFilter = normalizeFilter(filter);
        validateFilter(normalizedFilter);
        LocalDate today = LocalDate.now(clock);
        LocalDate endDate = endDate(normalizedFilter, today);

        List<FinancialTransaction> transactions =
                financialTransactionRepository.findAgendaTransactions(
                        normalizedFilter.farmId(),
                        transactionType(normalizedFilter.type()),
                        includeAll(normalizedFilter),
                        includePending(normalizedFilter),
                        includeOverdue(normalizedFilter),
                        normalizedFilter.periodDays(),
                        today,
                        endDate,
                        hasHarvestSeasonIds(normalizedFilter),
                        harvestSeasonIdsOrPlaceholder(normalizedFilter));

        AgendaSummaryAccumulator accumulator = new AgendaSummaryAccumulator();
        transactions.forEach(transaction -> accumulator.add(transaction, today));

        return accumulator.toResponse(normalizedFilter.farmId());
    }

    @Transactional(readOnly = true)
    public PageResponse<FinancialAgendaItemResponse> findAll(FinancialAgendaFilter filter) {
        FinancialAgendaFilter normalizedFilter = normalizeFilter(filter);
        validateFilter(normalizedFilter);
        LocalDate today = LocalDate.now(clock);
        LocalDate endDate = endDate(normalizedFilter, today);

        Page<FinancialAgendaItemResponse> transactions =
                financialTransactionRepository
                        .findAgendaTransactions(
                                normalizedFilter.farmId(),
                                transactionType(normalizedFilter.type()),
                                includeAll(normalizedFilter),
                                includePending(normalizedFilter),
                                includeOverdue(normalizedFilter),
                                normalizedFilter.periodDays(),
                                today,
                                endDate,
                                hasHarvestSeasonIds(normalizedFilter),
                                harvestSeasonIdsOrPlaceholder(normalizedFilter),
                                normalizedFilter.pageable())
                        .map(transaction -> toItemResponse(transaction, today));

        return PageResponse.from(transactions);
    }

    private FinancialAgendaFilter normalizeFilter(FinancialAgendaFilter filter) {
        if (filter == null) {
            return new FinancialAgendaFilter(
                    null,
                    FinancialAgendaStatusFilter.ALL,
                    FinancialAgendaTypeFilter.ALL,
                    null,
                    null,
                    null);
        }

        return new FinancialAgendaFilter(
                filter.farmId(),
                filter.status() == null ? FinancialAgendaStatusFilter.ALL : filter.status(),
                filter.type() == null ? FinancialAgendaTypeFilter.ALL : filter.type(),
                filter.periodDays(),
                normalizeList(filter.harvestSeasonIds()),
                toAgendaPageable(filter.pageable()));
    }

    private Pageable toAgendaPageable(Pageable pageable) {
        if (pageable == null) {
            return PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "dueDate"));
        }

        return PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(Sort.Direction.ASC, "dueDate"));
    }

    private void validateFilter(FinancialAgendaFilter filter) {
        Farm farm = farmService.findEntityById(filter.farmId());

        if (filter.periodDays() != null && filter.periodDays() <= 0) {
            throw new ValidationException("Period days must be greater than zero");
        }

        validateHarvestSeasonFilter(filter, farm);
    }

    private void validateHarvestSeasonFilter(FinancialAgendaFilter filter, Farm farm) {
        if (filter.harvestSeasonIds() == null) {
            return;
        }

        List<HarvestSeason> harvestSeasons =
                harvestSeasonRepository.findAllById(filter.harvestSeasonIds());

        if (harvestSeasons.size() != filter.harvestSeasonIds().size()) {
            throw new ResourceNotFoundException("Harvest season not found");
        }

        boolean hasSeasonFromAnotherFarm =
                harvestSeasons.stream()
                        .map(HarvestSeason::getFarm)
                        .map(Farm::getId)
                        .anyMatch(farmId -> !farmId.equals(farm.getId()));

        if (hasSeasonFromAnotherFarm) {
            throw new BusinessException("Harvest season does not belong to farm");
        }
    }

    private boolean includeAll(FinancialAgendaFilter filter) {
        return FinancialAgendaStatusFilter.ALL.equals(filter.status());
    }

    private boolean includePending(FinancialAgendaFilter filter) {
        return FinancialAgendaStatusFilter.PENDING.equals(filter.status());
    }

    private boolean includeOverdue(FinancialAgendaFilter filter) {
        return FinancialAgendaStatusFilter.OVERDUE.equals(filter.status());
    }

    private LocalDate endDate(FinancialAgendaFilter filter, LocalDate today) {
        if (filter.periodDays() == null) {
            return today;
        }

        return today.plusDays(filter.periodDays());
    }

    private TransactionType transactionType(FinancialAgendaTypeFilter type) {
        if (FinancialAgendaTypeFilter.RECEIVABLE.equals(type)) {
            return TransactionType.INCOME;
        }

        if (FinancialAgendaTypeFilter.PAYABLE.equals(type)) {
            return TransactionType.EXPENSE;
        }

        return null;
    }

    private boolean hasHarvestSeasonIds(FinancialAgendaFilter filter) {
        return filter.harvestSeasonIds() != null;
    }

    private List<Long> harvestSeasonIdsOrPlaceholder(FinancialAgendaFilter filter) {
        if (hasHarvestSeasonIds(filter)) {
            return filter.harvestSeasonIds();
        }

        return List.of(-1L);
    }

    private List<Long> normalizeList(List<Long> values) {
        if (values == null) {
            return null;
        }

        List<Long> normalizedValues = values.stream().filter(Objects::nonNull).distinct().toList();

        if (normalizedValues.isEmpty()) {
            return null;
        }

        return normalizedValues;
    }

    private FinancialAgendaItemResponse toItemResponse(
            FinancialTransaction transaction, LocalDate today) {
        FinancialCategory category = transaction.getCategory();
        HarvestSeason harvestSeason = transaction.getHarvestSeason();
        FinancialAgendaStatus agendaStatus = agendaStatus(transaction, today);

        return new FinancialAgendaItemResponse(
                transaction.getId(),
                transaction.getFarm().getId(),
                transaction.getDescription(),
                agendaType(transaction),
                transaction.getType(),
                agendaStatus,
                transaction.getStatus(),
                transaction.getAmount(),
                transaction.getDueDate(),
                daysOverdue(transaction, today, agendaStatus),
                daysUntilDue(transaction, today, agendaStatus),
                category == null ? null : category.getId(),
                category == null ? null : category.getName(),
                harvestSeason == null ? null : harvestSeason.getId(),
                harvestSeason == null ? null : harvestSeason.getName());
    }

    private FinancialAgendaType agendaType(FinancialTransaction transaction) {
        if (TransactionType.INCOME.equals(transaction.getType())) {
            return FinancialAgendaType.RECEIVABLE;
        }

        return FinancialAgendaType.PAYABLE;
    }

    private FinancialAgendaStatus agendaStatus(FinancialTransaction transaction, LocalDate today) {
        if (isAgendaOverdue(transaction, today)) {
            return FinancialAgendaStatus.OVERDUE;
        }

        return FinancialAgendaStatus.PENDING;
    }

    private Long daysOverdue(
            FinancialTransaction transaction, LocalDate today, FinancialAgendaStatus agendaStatus) {
        if (!FinancialAgendaStatus.OVERDUE.equals(agendaStatus)) {
            return null;
        }

        return ChronoUnit.DAYS.between(transaction.getDueDate(), today);
    }

    private Long daysUntilDue(
            FinancialTransaction transaction, LocalDate today, FinancialAgendaStatus agendaStatus) {
        if (!FinancialAgendaStatus.PENDING.equals(agendaStatus)) {
            return null;
        }

        return ChronoUnit.DAYS.between(today, transaction.getDueDate());
    }

    private boolean isAgendaOverdue(FinancialTransaction transaction, LocalDate today) {
        return PaymentStatus.OVERDUE.equals(transaction.getStatus())
                || (PaymentStatus.PENDING.equals(transaction.getStatus())
                        && transaction.getDueDate().isBefore(today));
    }

    private boolean isAgendaPending(FinancialTransaction transaction, LocalDate today) {
        return PaymentStatus.PENDING.equals(transaction.getStatus())
                && !transaction.getDueDate().isBefore(today);
    }

    private class AgendaSummaryAccumulator {

        private long overdueReceivableCount;

        private BigDecimal overdueReceivableTotal = BigDecimal.ZERO;

        private long overduePayableCount;

        private BigDecimal overduePayableTotal = BigDecimal.ZERO;

        private long pendingReceivableCount;

        private BigDecimal pendingReceivableTotal = BigDecimal.ZERO;

        private long pendingPayableCount;

        private BigDecimal pendingPayableTotal = BigDecimal.ZERO;

        private void add(FinancialTransaction transaction, LocalDate today) {
            if (TransactionType.INCOME.equals(transaction.getType())) {
                addReceivable(transaction, today);
                return;
            }

            addPayable(transaction, today);
        }

        private void addReceivable(FinancialTransaction transaction, LocalDate today) {
            if (isAgendaOverdue(transaction, today)) {
                overdueReceivableCount += 1;
                overdueReceivableTotal = overdueReceivableTotal.add(transaction.getAmount());
                return;
            }

            if (isAgendaPending(transaction, today)) {
                pendingReceivableCount += 1;
                pendingReceivableTotal = pendingReceivableTotal.add(transaction.getAmount());
            }
        }

        private void addPayable(FinancialTransaction transaction, LocalDate today) {
            if (isAgendaOverdue(transaction, today)) {
                overduePayableCount += 1;
                overduePayableTotal = overduePayableTotal.add(transaction.getAmount());
                return;
            }

            if (isAgendaPending(transaction, today)) {
                pendingPayableCount += 1;
                pendingPayableTotal = pendingPayableTotal.add(transaction.getAmount());
            }
        }

        private FinancialAgendaSummaryResponse toResponse(Long farmId) {
            FinancialAgendaAmountSummary overdueReceivable =
                    new FinancialAgendaAmountSummary(
                            overdueReceivableCount, overdueReceivableTotal);
            FinancialAgendaAmountSummary overduePayable =
                    new FinancialAgendaAmountSummary(overduePayableCount, overduePayableTotal);
            FinancialAgendaAmountSummary pendingReceivable =
                    new FinancialAgendaAmountSummary(
                            pendingReceivableCount, pendingReceivableTotal);
            FinancialAgendaAmountSummary pendingPayable =
                    new FinancialAgendaAmountSummary(pendingPayableCount, pendingPayableTotal);

            return new FinancialAgendaSummaryResponse(
                    farmId,
                    overdueReceivable,
                    overduePayable,
                    pendingReceivable,
                    pendingPayable,
                    new FinancialAgendaAmountSummary(
                            overdueReceivableCount + pendingReceivableCount,
                            overdueReceivableTotal.add(pendingReceivableTotal)),
                    new FinancialAgendaAmountSummary(
                            overduePayableCount + pendingPayableCount,
                            overduePayableTotal.add(pendingPayableTotal)));
        }
    }
}
