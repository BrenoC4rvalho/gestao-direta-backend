package br.com.gestaodireta.harvest.service;

import br.com.gestaodireta.farm.entity.Farm;
import br.com.gestaodireta.farm.enumeration.FarmStatus;
import br.com.gestaodireta.farm.service.FarmService;
import br.com.gestaodireta.financial.entity.FinancialTransaction;
import br.com.gestaodireta.financial.enumeration.PaymentStatus;
import br.com.gestaodireta.financial.enumeration.TransactionType;
import br.com.gestaodireta.financial.repository.FinancialTransactionRepository;
import br.com.gestaodireta.financial.repository.HarvestSeasonFinancialTotalsProjection;
import br.com.gestaodireta.harvest.dto.DashboardHarvestFinancialValuesResponse;
import br.com.gestaodireta.harvest.dto.DashboardHarvestSeasonResponse;
import br.com.gestaodireta.harvest.dto.FinancialAmountCountResponse;
import br.com.gestaodireta.harvest.dto.HarvestPlanningSummaryResponse;
import br.com.gestaodireta.harvest.dto.HarvestProjectionSummaryResponse;
import br.com.gestaodireta.harvest.dto.HarvestRealizedSummaryResponse;
import br.com.gestaodireta.harvest.dto.HarvestSeasonDetailSummaryResponse;
import br.com.gestaodireta.harvest.dto.HarvestSeasonFinancialSummaryResponse;
import br.com.gestaodireta.harvest.dto.HarvestSeasonRequest;
import br.com.gestaodireta.harvest.dto.HarvestSeasonResponse;
import br.com.gestaodireta.harvest.dto.HarvestSeasonStatusUpdateRequest;
import br.com.gestaodireta.harvest.dto.HarvestSeasonSummaryListResponse;
import br.com.gestaodireta.harvest.dto.HarvestSeasonUpdateRequest;
import br.com.gestaodireta.harvest.entity.HarvestSeason;
import br.com.gestaodireta.harvest.entity.ProductionActivity;
import br.com.gestaodireta.harvest.enumeration.HarvestSeasonStatus;
import br.com.gestaodireta.harvest.enumeration.ProductionActivityStatus;
import br.com.gestaodireta.harvest.mapper.HarvestSeasonMapper;
import br.com.gestaodireta.harvest.repository.HarvestSeasonBudgetItemRepository;
import br.com.gestaodireta.harvest.repository.HarvestSeasonRepository;
import br.com.gestaodireta.harvest.repository.HarvestSeasonSummaryListProjection;
import br.com.gestaodireta.shared.exception.BusinessException;
import br.com.gestaodireta.shared.exception.ResourceNotFoundException;
import br.com.gestaodireta.shared.pagination.PaginationParams;
import br.com.gestaodireta.shared.response.PageResponse;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HarvestSeasonService {

    private final HarvestSeasonRepository harvestSeasonRepository;

    private final HarvestSeasonBudgetItemRepository harvestSeasonBudgetItemRepository;

    private final FarmService farmService;

    private final FinancialTransactionRepository financialTransactionRepository;

    private final ProductionActivityService productionActivityService;

    private final HarvestSeasonMapper harvestSeasonMapper;

    private final HarvestFinancialSummaryCalculator harvestFinancialSummaryCalculator;

    private final Clock clock;

    public HarvestSeasonService(
            HarvestSeasonRepository harvestSeasonRepository,
            HarvestSeasonBudgetItemRepository harvestSeasonBudgetItemRepository,
            FarmService farmService,
            FinancialTransactionRepository financialTransactionRepository,
            ProductionActivityService productionActivityService,
            HarvestSeasonMapper harvestSeasonMapper,
            HarvestFinancialSummaryCalculator harvestFinancialSummaryCalculator,
            Clock clock) {
        this.harvestSeasonRepository = harvestSeasonRepository;
        this.harvestSeasonBudgetItemRepository = harvestSeasonBudgetItemRepository;
        this.farmService = farmService;
        this.financialTransactionRepository = financialTransactionRepository;
        this.productionActivityService = productionActivityService;
        this.harvestSeasonMapper = harvestSeasonMapper;
        this.harvestFinancialSummaryCalculator = harvestFinancialSummaryCalculator;
        this.clock = clock;
    }

    @Transactional
    public HarvestSeasonResponse create(HarvestSeasonRequest request) {
        Farm farm = farmService.findEntityById(request.farmId());
        ensureFarmIsActive(farm);

        HarvestSeason harvestSeason = new HarvestSeason();
        harvestSeason.setFarm(farm);
        applyRequest(
                harvestSeason,
                request.productionActivityId(),
                request.name(),
                request.description(),
                request.startDate(),
                request.endDate(),
                request.areaHectares(),
                null);
        harvestSeason.setStatus(HarvestSeasonStatus.PLANNED);

        return harvestSeasonMapper.toResponse(harvestSeasonRepository.save(harvestSeason));
    }

    @Transactional(readOnly = true)
    public PageResponse<HarvestSeasonResponse> findAll(
            Long farmId,
            List<HarvestSeasonStatus> statuses,
            Long productionActivityId,
            List<Long> productionActivityIds,
            LocalDate periodStart,
            LocalDate periodEnd,
            boolean includeInactive,
            PaginationParams paginationParams) {
        farmService.findEntityById(farmId);
        validatePeriod(periodStart, periodEnd);
        List<HarvestSeasonStatus> resolvedStatuses = resolveStatuses(statuses);
        List<Long> resolvedProductionActivityIds =
                resolveProductionActivityIds(productionActivityId, productionActivityIds);
        boolean filterStatuses = !resolvedStatuses.isEmpty();
        boolean filterProductionActivityIds = !resolvedProductionActivityIds.isEmpty();
        Page<HarvestSeason> seasons =
                harvestSeasonRepository.findByFarmId(
                        farmId,
                        includeInactive,
                        filterStatuses,
                        statusesForQuery(resolvedStatuses),
                        filterProductionActivityIds,
                        idsForQuery(resolvedProductionActivityIds),
                        periodStart != null,
                        periodStart,
                        periodEnd != null,
                        periodEnd,
                        paginationParams.toPageable());

        return PageResponse.from(seasons.map(harvestSeasonMapper::toResponse));
    }

    @Transactional(readOnly = true)
    public PageResponse<HarvestSeasonSummaryListResponse> findSummaryList(
            Long farmId,
            List<HarvestSeasonStatus> statuses,
            Long productionActivityId,
            List<Long> productionActivityIds,
            LocalDate periodStart,
            LocalDate periodEnd,
            String search,
            PaginationParams paginationParams) {
        farmService.findEntityById(farmId);
        validatePeriod(periodStart, periodEnd);
        List<HarvestSeasonStatus> resolvedStatuses = resolveStatuses(statuses);
        List<Long> resolvedProductionActivityIds =
                resolveProductionActivityIds(productionActivityId, productionActivityIds);
        boolean filterStatuses = !resolvedStatuses.isEmpty();
        boolean filterProductionActivityIds = !resolvedProductionActivityIds.isEmpty();
        Page<HarvestSeasonSummaryListProjection> seasons =
                harvestSeasonRepository.findSummaryList(
                        farmId,
                        filterStatuses,
                        statusesForQuery(resolvedStatuses),
                        filterProductionActivityIds,
                        idsForQuery(resolvedProductionActivityIds),
                        periodStart != null,
                        periodStart,
                        periodEnd != null,
                        periodEnd,
                        normalizeSearch(search),
                        paginationParams.toPageable());

        return PageResponse.from(seasons.map(this::toSummaryListResponse));
    }

    @Transactional(readOnly = true)
    public HarvestSeasonFinancialSummaryResponse getFinancialSummary(
            Long farmId,
            List<HarvestSeasonStatus> statuses,
            Long productionActivityId,
            List<Long> productionActivityIds,
            LocalDate periodStart,
            LocalDate periodEnd,
            String search) {
        farmService.findEntityById(farmId);
        validatePeriod(periodStart, periodEnd);
        List<HarvestSeasonStatus> resolvedStatuses = resolveStatuses(statuses);
        List<Long> activityIds =
                resolveProductionActivityIds(productionActivityId, productionActivityIds);
        validateProductionActivitiesBelongToFarm(activityIds, farmId);
        List<HarvestSeason> seasons =
                harvestSeasonRepository.findAllForFinancialSummary(
                        farmId,
                        !resolvedStatuses.isEmpty(),
                        statusesForQuery(resolvedStatuses),
                        !activityIds.isEmpty(),
                        idsForQuery(activityIds),
                        periodStart != null,
                        periodStart,
                        periodEnd != null,
                        periodEnd,
                        normalizeSearch(search));
        HarvestPlanningSummaryResponse planning =
                harvestFinancialSummaryCalculator.planning(
                        plannedAmount(seasons, TransactionType.EXPENSE),
                        plannedAmount(seasons, TransactionType.INCOME));
        HarvestSeasonFinancialTotalsProjection totals =
                financialTransactionRepository.summarizeHarvestSeasonFinancialTotals(
                        farmId,
                        seasons.stream().map(HarvestSeason::getId).toList(),
                        LocalDate.now(clock));
        HarvestRealizedSummaryResponse realized =
                harvestFinancialSummaryCalculator.realized(totals);
        HarvestProjectionSummaryResponse projection =
                harvestFinancialSummaryCalculator.projection(realized, totals);
        return new HarvestSeasonFinancialSummaryResponse(
                farmId,
                seasons.stream().filter(this::isActiveHarvestSeason).count(),
                planning,
                realized,
                projection,
                harvestFinancialSummaryCalculator.comparison(planning, projection),
                harvestFinancialSummaryCalculator.openAmounts(totals));
    }

    @Transactional(readOnly = true)
    public List<DashboardHarvestSeasonResponse> findDashboardSeasons(Long farmId) {
        farmService.findEntityById(farmId);

        LocalDate today = LocalDate.now(clock);
        LocalDate next7Days = today.plusDays(7);
        List<HarvestSeason> seasons =
                harvestSeasonRepository.findDashboardInProgressByFarmId(
                        farmId, PageRequest.of(0, 3));

        return seasons.stream()
                .map(season -> toDashboardResponse(season, today, next7Days))
                .toList();
    }

    @Transactional(readOnly = true)
    public HarvestSeasonResponse findById(Long id) {
        return harvestSeasonMapper.toResponse(findEntityById(id));
    }

    @Transactional(readOnly = true)
    public HarvestSeasonDetailSummaryResponse getSummary(Long id) {
        HarvestSeason harvestSeason = findEntityById(id);
        HarvestSeasonFinancialTotalsProjection totals =
                financialTransactionRepository.summarizeHarvestSeasonFinancialTotals(
                        harvestSeason.getFarm().getId(), List.of(id), LocalDate.now(clock));
        HarvestPlanningSummaryResponse planning =
                harvestFinancialSummaryCalculator.planning(
                        plannedAmount(List.of(harvestSeason), TransactionType.EXPENSE),
                        plannedAmount(List.of(harvestSeason), TransactionType.INCOME));
        HarvestRealizedSummaryResponse realized =
                harvestFinancialSummaryCalculator.realized(totals);
        HarvestProjectionSummaryResponse projection =
                harvestFinancialSummaryCalculator.projection(realized, totals);
        return new HarvestSeasonDetailSummaryResponse(
                harvestSeason.getId(),
                harvestSeason.getName(),
                harvestSeason.getProductionActivity().getId(),
                harvestSeason.getProductionActivity().getName(),
                harvestSeason.getFarm().getId(),
                harvestSeason.getFarm().getName(),
                harvestSeason.getAreaHectares(),
                planning,
                realized,
                projection,
                harvestFinancialSummaryCalculator.comparison(planning, projection),
                harvestFinancialSummaryCalculator.openAmounts(totals),
                zeroIfNull(totals.getTransactionCount()),
                zeroIfNull(totals.getIncomeCount()),
                zeroIfNull(totals.getExpenseCount()));
    }

    @Transactional
    public HarvestSeasonResponse update(Long id, HarvestSeasonUpdateRequest request) {
        HarvestSeason harvestSeason = findEntityById(id);

        if (HarvestSeasonStatus.INACTIVE.equals(harvestSeason.getStatus())) {
            throw new BusinessException("Inactive harvest season cannot be edited.");
        }

        applyRequest(
                harvestSeason,
                request.productionActivityId(),
                request.name(),
                request.description(),
                request.startDate(),
                request.endDate(),
                request.areaHectares(),
                id);

        return harvestSeasonMapper.toResponse(harvestSeasonRepository.save(harvestSeason));
    }

    @Transactional
    public HarvestSeasonResponse updateStatus(Long id, HarvestSeasonStatusUpdateRequest request) {
        HarvestSeason harvestSeason = findEntityById(id);
        ensureFarmIsActive(harvestSeason.getFarm());
        validateStatusTransition(harvestSeason.getStatus(), request.status());
        harvestSeason.setStatus(request.status());

        return harvestSeasonMapper.toResponse(harvestSeasonRepository.save(harvestSeason));
    }

    @Transactional
    public HarvestSeasonResponse activate(Long id) {
        HarvestSeason harvestSeason = findEntityById(id);
        ensureFarmIsActive(harvestSeason.getFarm());

        if (!HarvestSeasonStatus.INACTIVE.equals(harvestSeason.getStatus())) {
            throw new BusinessException("Only inactive harvest seasons can be activated.");
        }

        harvestSeason.setStatus(HarvestSeasonStatus.PLANNED);

        return harvestSeasonMapper.toResponse(harvestSeasonRepository.save(harvestSeason));
    }

    @Transactional
    public void inactivate(Long id) {
        HarvestSeason harvestSeason = findEntityById(id);
        validateStatusTransition(harvestSeason.getStatus(), HarvestSeasonStatus.INACTIVE);
        harvestSeason.setStatus(HarvestSeasonStatus.INACTIVE);
        harvestSeasonRepository.save(harvestSeason);
    }

    public HarvestSeason findEntityById(Long id) {
        return harvestSeasonRepository
                .findByIdWithRelations(id)
                .orElseThrow(() -> new ResourceNotFoundException("Harvest season not found"));
    }

    private void applyRequest(
            HarvestSeason harvestSeason,
            Long productionActivityId,
            String name,
            String description,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal areaHectares,
            Long ignoredSeasonId) {
        String sanitizedName = sanitizeName(name);
        validateDates(startDate, endDate);
        validateUniqueName(harvestSeason.getFarm().getId(), sanitizedName, ignoredSeasonId);

        ProductionActivity productionActivity =
                productionActivityService.findEntityById(productionActivityId);
        ensureProductionActivityBelongsToFarm(productionActivity, harvestSeason.getFarm().getId());
        ensureProductionActivityIsActive(productionActivity);

        harvestSeason.setProductionActivity(productionActivity);
        harvestSeason.setName(sanitizedName);
        harvestSeason.setDescription(description);
        harvestSeason.setStartDate(startDate);
        harvestSeason.setEndDate(endDate);
        harvestSeason.setAreaHectares(areaHectares);
    }

    private String sanitizeName(String name) {
        String sanitizedName = name == null ? "" : name.trim();

        if (sanitizedName.isEmpty()) {
            throw new BusinessException("Harvest season name cannot be blank.");
        }

        return sanitizedName;
    }

    private void validateDates(LocalDate startDate, LocalDate endDate) {
        if (endDate != null && endDate.isBefore(startDate)) {
            throw new BusinessException("End date cannot be before start date.");
        }
    }

    private void validateUniqueName(Long farmId, String name, Long ignoredSeasonId) {
        String normalizedName = name.toLowerCase(Locale.ROOT);
        boolean duplicateExists =
                ignoredSeasonId == null
                        ? harvestSeasonRepository.existsByFarmIdAndNormalizedName(
                                farmId, normalizedName)
                        : harvestSeasonRepository.existsByFarmIdAndNormalizedNameAndIdNot(
                                farmId, ignoredSeasonId, normalizedName);

        if (duplicateExists) {
            throw new BusinessException(
                    "A harvest season with this name already exists for this farm.");
        }
    }

    private void validateStatusTransition(
            HarvestSeasonStatus currentStatus, HarvestSeasonStatus targetStatus) {
        boolean allowed =
                switch (currentStatus) {
                    case PLANNED ->
                            HarvestSeasonStatus.IN_PROGRESS.equals(targetStatus)
                                    || HarvestSeasonStatus.INACTIVE.equals(targetStatus);
                    case IN_PROGRESS ->
                            HarvestSeasonStatus.FINISHED.equals(targetStatus)
                                    || HarvestSeasonStatus.INACTIVE.equals(targetStatus);
                    case FINISHED ->
                            HarvestSeasonStatus.IN_PROGRESS.equals(targetStatus)
                                    || HarvestSeasonStatus.INACTIVE.equals(targetStatus);
                    case INACTIVE -> false;
                };

        if (!allowed) {
            throw new BusinessException(
                    "Invalid harvest season status transition from "
                            + currentStatus
                            + " to "
                            + targetStatus
                            + ".");
        }
    }

    private void ensureFarmIsActive(Farm farm) {
        if (!FarmStatus.ACTIVE.equals(farm.getStatus())) {
            throw new BusinessException("Inactive farm cannot receive harvest seasons.");
        }
    }

    private void ensureProductionActivityBelongsToFarm(
            ProductionActivity productionActivity, Long farmId) {
        if (!productionActivity.getFarm().getId().equals(farmId)) {
            throw new BusinessException(
                    "Production activity must belong to the same farm as the harvest season.");
        }
    }

    private void ensureProductionActivityIsActive(ProductionActivity productionActivity) {
        if (!ProductionActivityStatus.ACTIVE.equals(productionActivity.getStatus())) {
            throw new BusinessException(
                    "Inactive production activity cannot be used in a harvest season.");
        }
    }

    private DashboardHarvestSeasonResponse toDashboardResponse(
            HarvestSeason season, LocalDate today, LocalDate next7Days) {
        List<FinancialTransaction> transactions =
                financialTransactionRepository.findDashboardTransactionsByFarmIdAndHarvestSeasonId(
                        season.getFarm().getId(), season.getId());
        HarvestRealizedSummaryResponse realized =
                harvestFinancialSummaryCalculator.realized(
                        sumAmounts(transactions, this::isPaidExpense),
                        sumAmounts(transactions, this::isPaidIncome));
        HarvestProjectionSummaryResponse projection =
                harvestFinancialSummaryCalculator.projection(
                        realized,
                        sumAmounts(transactions, this::isOpenExpense),
                        sumAmounts(transactions, this::isOpenIncome));

        return new DashboardHarvestSeasonResponse(
                season.getId(),
                season.getFarm().getId(),
                season.getName(),
                season.getStatus(),
                season.getProductionActivity().getId(),
                season.getProductionActivity().getName(),
                new DashboardHarvestFinancialValuesResponse(
                        realized.realizedCost(),
                        realized.realizedRevenue(),
                        realized.realizedProfit()),
                new DashboardHarvestFinancialValuesResponse(
                        projection.projectedCost(),
                        projection.projectedRevenue(),
                        projection.projectedProfit()),
                toAmountCountResponse(
                        transactions, transaction -> isDueNext7Days(transaction, today, next7Days)),
                toAmountCountResponse(transactions, transaction -> isOverdue(transaction, today)));
    }

    private FinancialAmountCountResponse toAmountCountResponse(
            List<FinancialTransaction> transactions, Predicate<FinancialTransaction> filter) {
        return new FinancialAmountCountResponse(
                transactions.stream().filter(filter).count(), sumAmounts(transactions, filter));
    }

    private BigDecimal sumAmounts(
            List<FinancialTransaction> transactions, Predicate<FinancialTransaction> filter) {
        return transactions.stream()
                .filter(filter)
                .map(FinancialTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal plannedAmount(List<HarvestSeason> seasons, TransactionType type) {
        if (seasons.isEmpty()) {
            return BigDecimal.ZERO;
        }

        return harvestSeasonBudgetItemRepository
                .findAllByHarvestSeasonIdIn(seasons.stream().map(HarvestSeason::getId).toList())
                .stream()
                .filter(item -> type.equals(item.getType()))
                .map(item -> item.getPlannedAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private boolean isPaidExpense(FinancialTransaction transaction) {
        return TransactionType.EXPENSE.equals(transaction.getType())
                && PaymentStatus.PAID.equals(transaction.getStatus());
    }

    private boolean isPaidIncome(FinancialTransaction transaction) {
        return TransactionType.INCOME.equals(transaction.getType())
                && PaymentStatus.PAID.equals(transaction.getStatus());
    }

    private boolean isOpenExpense(FinancialTransaction transaction) {
        return TransactionType.EXPENSE.equals(transaction.getType()) && isOpen(transaction);
    }

    private boolean isOpenIncome(FinancialTransaction transaction) {
        return TransactionType.INCOME.equals(transaction.getType()) && isOpen(transaction);
    }

    private boolean isOpen(FinancialTransaction transaction) {
        return PaymentStatus.PENDING.equals(transaction.getStatus())
                || PaymentStatus.OVERDUE.equals(transaction.getStatus());
    }

    private boolean isDueNext7Days(
            FinancialTransaction transaction, LocalDate today, LocalDate next7Days) {
        LocalDate dueDate = transaction.getDueDate();

        return TransactionType.EXPENSE.equals(transaction.getType())
                && PaymentStatus.PENDING.equals(transaction.getStatus())
                && dueDate != null
                && !dueDate.isBefore(today)
                && !dueDate.isAfter(next7Days);
    }

    private boolean isOverdue(FinancialTransaction transaction, LocalDate today) {
        LocalDate dueDate = transaction.getDueDate();

        return TransactionType.EXPENSE.equals(transaction.getType())
                && dueDate != null
                && (PaymentStatus.OVERDUE.equals(transaction.getStatus())
                        || (PaymentStatus.PENDING.equals(transaction.getStatus())
                                && dueDate.isBefore(today)));
    }

    private HarvestSeasonSummaryListResponse toSummaryListResponse(
            HarvestSeasonSummaryListProjection projection) {
        BigDecimal expectedCost = zeroIfNull(projection.getExpectedCost());
        BigDecimal expectedRevenue = zeroIfNull(projection.getExpectedRevenue());
        BigDecimal realizedCost = zeroIfNull(projection.getRealizedCost());
        BigDecimal realizedRevenue = zeroIfNull(projection.getRealizedRevenue());

        return new HarvestSeasonSummaryListResponse(
                projection.getId(),
                projection.getFarmId(),
                projection.getFarmName(),
                projection.getProductionActivityId(),
                projection.getProductionActivityName(),
                projection.getName(),
                projection.getDescription(),
                projection.getStartDate(),
                projection.getEndDate(),
                expectedCost,
                expectedRevenue,
                expectedRevenue.subtract(expectedCost),
                projection.getAreaHectares(),
                projection.getStatus(),
                realizedCost,
                realizedRevenue,
                realizedRevenue.subtract(realizedCost),
                zeroIfNull(projection.getPendingExpenses()),
                zeroIfNull(projection.getOverdueExpenses()),
                zeroIfNull(projection.getPendingRevenue()),
                zeroIfNull(projection.getTransactionCount()),
                zeroIfNull(projection.getIncomeCount()),
                zeroIfNull(projection.getExpenseCount()),
                projection.getCreatedAt(),
                projection.getUpdatedAt());
    }

    List<HarvestSeasonStatus> resolveStatuses(List<HarvestSeasonStatus> statuses) {
        return statuses == null
                ? List.of()
                : statuses.stream().filter(Objects::nonNull).distinct().toList();
    }

    List<Long> resolveProductionActivityIds(
            Long productionActivityId, List<Long> productionActivityIds) {
        List<Long> normalizedIds =
                productionActivityIds == null
                        ? List.of()
                        : productionActivityIds.stream().filter(Objects::nonNull).toList();

        if (!normalizedIds.isEmpty()) {
            return normalizedIds;
        }

        if (productionActivityId != null) {
            return List.of(productionActivityId);
        }

        return List.of();
    }

    private void validatePeriod(LocalDate periodStart, LocalDate periodEnd) {
        if (periodStart != null && periodEnd != null && periodStart.isAfter(periodEnd)) {
            throw new BusinessException(
                    "A data inicial do período não pode ser posterior à data final.");
        }
    }

    private List<Long> idsForQuery(List<Long> ids) {
        if (!ids.isEmpty()) {
            return ids;
        }

        return List.of(-1L);
    }

    private List<HarvestSeasonStatus> statusesForQuery(List<HarvestSeasonStatus> statuses) {
        if (!statuses.isEmpty()) {
            return statuses;
        }

        return Arrays.asList(HarvestSeasonStatus.values());
    }

    private String normalizeSearch(String search) {
        if (search == null) {
            return null;
        }

        String normalizedSearch = search.trim().toLowerCase(Locale.ROOT);

        return normalizedSearch.isEmpty() ? null : normalizedSearch;
    }

    private void validateProductionActivitiesBelongToFarm(
            List<Long> productionActivityIds, Long farmId) {
        for (Long productionActivityId : productionActivityIds) {
            ProductionActivity productionActivity =
                    productionActivityService.findEntityById(productionActivityId);
            ensureProductionActivityBelongsToFarm(productionActivity, farmId);
        }
    }

    private boolean isActiveHarvestSeason(HarvestSeason harvestSeason) {
        return HarvestSeasonStatus.PLANNED.equals(harvestSeason.getStatus())
                || HarvestSeasonStatus.IN_PROGRESS.equals(harvestSeason.getStatus());
    }

    private BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private Long zeroIfNull(Long value) {
        return value == null ? 0L : value;
    }
}
