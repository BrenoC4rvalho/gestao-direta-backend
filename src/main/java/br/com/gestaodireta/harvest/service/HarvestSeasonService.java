package br.com.gestaodireta.harvest.service;

import br.com.gestaodireta.farm.entity.Farm;
import br.com.gestaodireta.farm.enumeration.FarmStatus;
import br.com.gestaodireta.farm.service.FarmService;
import br.com.gestaodireta.financial.entity.FinancialTransaction;
import br.com.gestaodireta.financial.enumeration.PaymentStatus;
import br.com.gestaodireta.financial.enumeration.TransactionType;
import br.com.gestaodireta.financial.repository.FinancialTransactionRepository;
import br.com.gestaodireta.financial.repository.HarvestSeasonCategoryAmountProjection;
import br.com.gestaodireta.financial.repository.HarvestSeasonFinancialTotalsProjection;
import br.com.gestaodireta.harvest.dto.DashboardHarvestFinancialValuesResponse;
import br.com.gestaodireta.harvest.dto.DashboardHarvestSeasonResponse;
import br.com.gestaodireta.harvest.dto.FinancialAmountCountResponse;
import br.com.gestaodireta.harvest.dto.HarvestCategoryAmountResponse;
import br.com.gestaodireta.harvest.dto.HarvestCategoryBreakdownResponse;
import br.com.gestaodireta.harvest.dto.HarvestCategoryComparisonBreakdownResponse;
import br.com.gestaodireta.harvest.dto.HarvestCategoryComparisonCategoryResponse;
import br.com.gestaodireta.harvest.dto.HarvestCategoryComparisonResponse;
import br.com.gestaodireta.harvest.dto.HarvestCategoryMovementsResponse;
import br.com.gestaodireta.harvest.dto.HarvestPlanningSummaryResponse;
import br.com.gestaodireta.harvest.dto.HarvestProjectionSummaryResponse;
import br.com.gestaodireta.harvest.dto.HarvestRealizedSummaryResponse;
import br.com.gestaodireta.harvest.dto.HarvestSeasonComparisonBestResponse;
import br.com.gestaodireta.harvest.dto.HarvestSeasonComparisonDifferenceResponse;
import br.com.gestaodireta.harvest.dto.HarvestSeasonComparisonHarvestResponse;
import br.com.gestaodireta.harvest.dto.HarvestSeasonComparisonResponse;
import br.com.gestaodireta.harvest.dto.HarvestSeasonDetailSummaryResponse;
import br.com.gestaodireta.harvest.dto.HarvestSeasonFinancialSummaryResponse;
import br.com.gestaodireta.harvest.dto.HarvestSeasonPerHectareComparisonResponse;
import br.com.gestaodireta.harvest.dto.HarvestSeasonRequest;
import br.com.gestaodireta.harvest.dto.HarvestSeasonResponse;
import br.com.gestaodireta.harvest.dto.HarvestSeasonStatusUpdateRequest;
import br.com.gestaodireta.harvest.dto.HarvestSeasonSummaryListResponse;
import br.com.gestaodireta.harvest.dto.HarvestSeasonUpdateRequest;
import br.com.gestaodireta.harvest.entity.HarvestSeason;
import br.com.gestaodireta.harvest.entity.ProductionActivity;
import br.com.gestaodireta.harvest.enumeration.ComparisonSemantic;
import br.com.gestaodireta.harvest.enumeration.HarvestCategoryComparisonStatus;
import br.com.gestaodireta.harvest.enumeration.HarvestSeasonComparisonDirection;
import br.com.gestaodireta.harvest.enumeration.HarvestSeasonComparisonMetric;
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
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
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
        return buildDetailSummary(findEntityById(id));
    }

    @Transactional(readOnly = true)
    public HarvestCategoryMovementsResponse getCategoryBreakdown(Long id) {
        HarvestSeason harvestSeason = findEntityById(id);
        Long farmId = harvestSeason.getFarm().getId();

        return new HarvestCategoryMovementsResponse(
                categoryBreakdown(farmId, id, TransactionType.EXPENSE),
                categoryBreakdown(farmId, id, TransactionType.INCOME));
    }

    @Transactional(readOnly = true)
    public HarvestCategoryComparisonResponse getCategoryComparison(Long id) {
        HarvestSeason harvestSeason = findEntityById(id);
        Long farmId = harvestSeason.getFarm().getId();

        return new HarvestCategoryComparisonResponse(
                categoryComparison(farmId, harvestSeason, TransactionType.EXPENSE),
                categoryComparison(farmId, harvestSeason, TransactionType.INCOME));
    }

    @Transactional(readOnly = true)
    public HarvestSeasonComparisonResponse compare(
            Long farmId, Long harvestSeasonIdA, Long harvestSeasonIdB) {
        if (harvestSeasonIdA.equals(harvestSeasonIdB)) {
            throw new BusinessException("Harvest seasons must be different for comparison.");
        }

        Farm farm = farmService.findEntityById(farmId);
        HarvestSeason harvestSeasonA = findEntityById(harvestSeasonIdA);
        HarvestSeason harvestSeasonB = findEntityById(harvestSeasonIdB);
        ensureHarvestSeasonBelongsToFarm(harvestSeasonA, farm);
        ensureHarvestSeasonBelongsToFarm(harvestSeasonB, farm);

        HarvestSeasonComparisonHarvestResponse harvestA = toComparisonHarvest(harvestSeasonA);
        HarvestSeasonComparisonHarvestResponse harvestB = toComparisonHarvest(harvestSeasonB);
        List<HarvestSeasonComparisonDifferenceResponse> differences =
                comparisonDifferences(harvestA, harvestB);

        return new HarvestSeasonComparisonResponse(
                harvestA,
                harvestB,
                differences,
                differences.stream()
                        .filter(this::isHighlightCandidate)
                        .filter(difference -> difference.percentageDifference() != null)
                        .sorted(
                                Comparator.comparing(
                                                HarvestSeasonComparisonDifferenceResponse
                                                        ::percentageDifference,
                                                Comparator.comparing(BigDecimal::abs))
                                        .reversed())
                        .limit(3)
                        .toList(),
                bestMetrics(harvestSeasonA, harvestSeasonB, harvestA, harvestB, differences));
    }

    private HarvestSeasonDetailSummaryResponse buildDetailSummary(HarvestSeason harvestSeason) {
        HarvestSeasonFinancialTotalsProjection totals =
                financialTransactionRepository.summarizeHarvestSeasonFinancialTotals(
                        harvestSeason.getFarm().getId(),
                        List.of(harvestSeason.getId()),
                        LocalDate.now(clock));
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
                harvestFinancialSummaryCalculator.planningComparison(
                        hasPlanning(harvestSeason),
                        hasCurrentData(totals),
                        harvestSeason.getStatus() == HarvestSeasonStatus.PLANNED,
                        harvestSeason.getStatus() == HarvestSeasonStatus.IN_PROGRESS,
                        planning,
                        realized,
                        projection),
                harvestFinancialSummaryCalculator.openAmounts(totals),
                zeroIfNull(totals.getTransactionCount()),
                zeroIfNull(totals.getIncomeCount()),
                zeroIfNull(totals.getExpenseCount()));
    }

    private HarvestCategoryBreakdownResponse categoryBreakdown(
            Long farmId, Long harvestSeasonId, TransactionType type) {
        List<HarvestSeasonCategoryAmountProjection> projections =
                financialTransactionRepository.summarizeHarvestSeasonAmountsByCategory(
                        farmId, harvestSeasonId, type);
        BigDecimal total =
                projections.stream()
                        .map(HarvestSeasonCategoryAmountProjection::getAmount)
                        .map(this::zeroIfNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<HarvestCategoryAmountResponse> categories =
                projections.stream()
                        .map(
                                projection ->
                                        new HarvestCategoryAmountResponse(
                                                projection.getCategoryId(),
                                                projection.getCategoryName() == null
                                                        ? "Sem categoria"
                                                        : projection.getCategoryName(),
                                                zeroIfNull(projection.getAmount()),
                                                percentageOf(
                                                        zeroIfNull(projection.getAmount()), total)))
                        .toList();

        return new HarvestCategoryBreakdownResponse(total, categories);
    }

    private HarvestCategoryComparisonBreakdownResponse categoryComparison(
            Long farmId, HarvestSeason harvestSeason, TransactionType type) {
        Map<Long, CategoryComparisonAmount> plannedAmounts =
                plannedAmountsByCategory(harvestSeason, type);
        Map<Long, CategoryComparisonAmount> realizedAmounts =
                realizedAmountsByCategory(farmId, harvestSeason.getId(), type);
        Map<Long, CategoryComparisonAmount> categoriesById = new HashMap<>(plannedAmounts);
        realizedAmounts.forEach(
                (categoryId, realized) ->
                        categoriesById.merge(
                                categoryId, realized, CategoryComparisonAmount::withRealized));

        List<HarvestCategoryComparisonCategoryResponse> categories =
                categoriesById.values().stream()
                        .map(amount -> categoryComparisonCategory(amount, type))
                        .sorted(
                                Comparator.comparing(
                                                HarvestCategoryComparisonCategoryResponse
                                                        ::realizedAmount)
                                        .reversed()
                                        .thenComparing(
                                                HarvestCategoryComparisonCategoryResponse
                                                        ::plannedAmount,
                                                Comparator.nullsLast(Comparator.reverseOrder()))
                                        .thenComparing(
                                                HarvestCategoryComparisonCategoryResponse
                                                        ::categoryName,
                                                String.CASE_INSENSITIVE_ORDER))
                        .toList();
        BigDecimal plannedTotal =
                categories.stream()
                        .filter(HarvestCategoryComparisonCategoryResponse::planned)
                        .map(HarvestCategoryComparisonCategoryResponse::plannedAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal realizedTotal =
                categories.stream()
                        .map(HarvestCategoryComparisonCategoryResponse::realizedAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new HarvestCategoryComparisonBreakdownResponse(
                plannedTotal, realizedTotal, realizedTotal.subtract(plannedTotal), categories);
    }

    private Map<Long, CategoryComparisonAmount> plannedAmountsByCategory(
            HarvestSeason harvestSeason, TransactionType type) {
        Map<Long, CategoryComparisonAmount> amounts = new HashMap<>();
        harvestSeasonBudgetItemRepository.findAllByHarvestSeasonId(harvestSeason.getId()).stream()
                .filter(item -> type.equals(item.getType()))
                .forEach(
                        item -> {
                            Long categoryId =
                                    item.getCategory() == null ? null : item.getCategory().getId();
                            String categoryName =
                                    item.getCategory() == null
                                            ? "Sem categoria"
                                            : item.getCategory().getName();
                            amounts.merge(
                                    categoryId,
                                    CategoryComparisonAmount.planned(
                                            categoryId, categoryName, item.getPlannedAmount()),
                                    CategoryComparisonAmount::withPlanned);
                        });
        return amounts;
    }

    private Map<Long, CategoryComparisonAmount> realizedAmountsByCategory(
            Long farmId, Long harvestSeasonId, TransactionType type) {
        Map<Long, CategoryComparisonAmount> amounts = new HashMap<>();
        financialTransactionRepository
                .summarizeHarvestSeasonAmountsByCategory(farmId, harvestSeasonId, type)
                .forEach(
                        projection ->
                                amounts.put(
                                        projection.getCategoryId(),
                                        CategoryComparisonAmount.realized(
                                                projection.getCategoryId(),
                                                projection.getCategoryName() == null
                                                        ? "Sem categoria"
                                                        : projection.getCategoryName(),
                                                zeroIfNull(projection.getAmount()))));
        return amounts;
    }

    private HarvestCategoryComparisonCategoryResponse categoryComparisonCategory(
            CategoryComparisonAmount amount, TransactionType type) {
        BigDecimal realizedAmount = zeroIfNull(amount.realizedAmount());
        if (!amount.planned()) {
            return new HarvestCategoryComparisonCategoryResponse(
                    amount.categoryId(),
                    amount.categoryName(),
                    false,
                    null,
                    realizedAmount,
                    realizedAmount,
                    null,
                    HarvestCategoryComparisonStatus.UNPLANNED,
                    type == TransactionType.EXPENSE
                            ? ComparisonSemantic.WORSE
                            : ComparisonSemantic.BETTER);
        }

        BigDecimal plannedAmount = zeroIfNull(amount.plannedAmount());
        BigDecimal difference = realizedAmount.subtract(plannedAmount);
        int comparison = realizedAmount.compareTo(plannedAmount);
        HarvestCategoryComparisonStatus status =
                categoryComparisonStatus(plannedAmount, realizedAmount, comparison);
        ComparisonSemantic semantic =
                status == HarvestCategoryComparisonStatus.NO_MOVEMENT
                        ? ComparisonSemantic.NEUTRAL
                        : comparisonSemantic(difference, TransactionType.EXPENSE.equals(type));

        return new HarvestCategoryComparisonCategoryResponse(
                amount.categoryId(),
                amount.categoryName(),
                true,
                plannedAmount,
                realizedAmount,
                difference,
                percentageDifference(difference, plannedAmount),
                status,
                semantic);
    }

    private HarvestCategoryComparisonStatus categoryComparisonStatus(
            BigDecimal plannedAmount, BigDecimal realizedAmount, int comparison) {
        if (plannedAmount.signum() > 0 && realizedAmount.signum() == 0) {
            return HarvestCategoryComparisonStatus.NO_MOVEMENT;
        }
        if (comparison > 0) {
            return HarvestCategoryComparisonStatus.ABOVE_PLAN;
        }
        if (comparison < 0) {
            return HarvestCategoryComparisonStatus.BELOW_PLAN;
        }
        return HarvestCategoryComparisonStatus.ON_PLAN;
    }

    private BigDecimal percentageOf(BigDecimal amount, BigDecimal total) {
        if (total.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(2);
        }

        return amount.multiply(BigDecimal.valueOf(100)).divide(total, 2, RoundingMode.HALF_UP);
    }

    private record CategoryComparisonAmount(
            Long categoryId,
            String categoryName,
            boolean planned,
            BigDecimal plannedAmount,
            BigDecimal realizedAmount) {

        static CategoryComparisonAmount planned(
                Long categoryId, String categoryName, BigDecimal plannedAmount) {
            return new CategoryComparisonAmount(
                    categoryId, categoryName, true, plannedAmount, BigDecimal.ZERO);
        }

        static CategoryComparisonAmount realized(
                Long categoryId, String categoryName, BigDecimal realizedAmount) {
            return new CategoryComparisonAmount(
                    categoryId, categoryName, false, null, realizedAmount);
        }

        CategoryComparisonAmount withPlanned(CategoryComparisonAmount other) {
            return new CategoryComparisonAmount(
                    categoryId,
                    categoryName,
                    true,
                    valueOrZero(plannedAmount).add(valueOrZero(other.plannedAmount)),
                    valueOrZero(realizedAmount).add(valueOrZero(other.realizedAmount)));
        }

        CategoryComparisonAmount withRealized(CategoryComparisonAmount other) {
            return new CategoryComparisonAmount(
                    categoryId,
                    categoryName,
                    planned || other.planned,
                    planned ? plannedAmount : other.plannedAmount,
                    valueOrZero(realizedAmount).add(valueOrZero(other.realizedAmount)));
        }

        private static BigDecimal valueOrZero(BigDecimal value) {
            return value == null ? BigDecimal.ZERO : value;
        }
    }

    private boolean hasPlanning(HarvestSeason harvestSeason) {
        return !harvestSeasonBudgetItemRepository
                .findAllByHarvestSeasonId(harvestSeason.getId())
                .isEmpty();
    }

    private boolean hasCurrentData(HarvestSeasonFinancialTotalsProjection totals) {
        return zeroIfNull(totals.getTransactionCount()) > 0;
    }

    private HarvestSeasonComparisonHarvestResponse toComparisonHarvest(
            HarvestSeason harvestSeason) {
        HarvestSeasonDetailSummaryResponse summary = buildDetailSummary(harvestSeason);
        boolean hasPlanning =
                !harvestSeasonBudgetItemRepository
                        .findAllByHarvestSeasonId(harvestSeason.getId())
                        .isEmpty();
        boolean hasTransactions = summary.transactionCount() > 0;

        return new HarvestSeasonComparisonHarvestResponse(
                harvestSeason.getId(),
                harvestSeason.getName(),
                harvestSeason.getStatus(),
                harvestSeason.getProductionActivity().getName(),
                harvestSeason.getStartDate(),
                harvestSeason.getEndDate(),
                harvestSeason.getAreaHectares(),
                hasPlanning ? summary.planning() : null,
                hasTransactions ? summary.projection() : null,
                hasTransactions ? summary.realized() : null,
                new HarvestSeasonPerHectareComparisonResponse(
                        hasPlanning ? summary.plannedCostPerHectare() : null,
                        hasPlanning ? summary.plannedRevenuePerHectare() : null,
                        hasPlanning ? summary.plannedResultPerHectare() : null,
                        hasTransactions ? summary.projectedCostPerHectare() : null,
                        hasTransactions ? summary.projectedRevenuePerHectare() : null,
                        hasTransactions ? summary.projectedProfitPerHectare() : null,
                        hasTransactions ? summary.realizedCostPerHectare() : null,
                        hasTransactions ? summary.realizedRevenuePerHectare() : null,
                        hasTransactions ? summary.realizedProfitPerHectare() : null));
    }

    private List<HarvestSeasonComparisonDifferenceResponse> comparisonDifferences(
            HarvestSeasonComparisonHarvestResponse harvestA,
            HarvestSeasonComparisonHarvestResponse harvestB) {
        return List.of(
                comparisonDifference(
                        HarvestSeasonComparisonMetric.AREA_HECTARES,
                        harvestA,
                        harvestB,
                        HarvestSeasonComparisonHarvestResponse::areaHectares,
                        false),
                comparisonDifference(
                        HarvestSeasonComparisonMetric.PLANNED_COST,
                        harvestA,
                        harvestB,
                        harvest ->
                                harvest.planning() == null
                                        ? null
                                        : harvest.planning().plannedCost(),
                        true),
                comparisonDifference(
                        HarvestSeasonComparisonMetric.PLANNED_REVENUE,
                        harvestA,
                        harvestB,
                        harvest ->
                                harvest.planning() == null
                                        ? null
                                        : harvest.planning().plannedRevenue(),
                        false),
                comparisonDifference(
                        HarvestSeasonComparisonMetric.PLANNED_RESULT,
                        harvestA,
                        harvestB,
                        harvest ->
                                harvest.planning() == null
                                        ? null
                                        : harvest.planning().plannedProfit(),
                        false),
                comparisonDifference(
                        HarvestSeasonComparisonMetric.PLANNED_MARGIN,
                        harvestA,
                        harvestB,
                        harvest ->
                                harvest.planning() == null
                                        ? null
                                        : harvest.planning().plannedMargin(),
                        false),
                comparisonDifference(
                        HarvestSeasonComparisonMetric.PROJECTED_COST,
                        harvestA,
                        harvestB,
                        harvest ->
                                harvest.projection() == null
                                        ? null
                                        : harvest.projection().projectedCost(),
                        true),
                comparisonDifference(
                        HarvestSeasonComparisonMetric.PROJECTED_REVENUE,
                        harvestA,
                        harvestB,
                        harvest ->
                                harvest.projection() == null
                                        ? null
                                        : harvest.projection().projectedRevenue(),
                        false),
                comparisonDifference(
                        HarvestSeasonComparisonMetric.PROJECTED_PROFIT,
                        harvestA,
                        harvestB,
                        harvest ->
                                harvest.projection() == null
                                        ? null
                                        : harvest.projection().projectedProfit(),
                        false),
                comparisonDifference(
                        HarvestSeasonComparisonMetric.PROJECTED_MARGIN,
                        harvestA,
                        harvestB,
                        harvest ->
                                harvest.projection() == null
                                        ? null
                                        : harvest.projection().projectedMargin(),
                        false),
                comparisonDifference(
                        HarvestSeasonComparisonMetric.REALIZED_COST,
                        harvestA,
                        harvestB,
                        harvest ->
                                harvest.realized() == null
                                        ? null
                                        : harvest.realized().realizedCost(),
                        true),
                comparisonDifference(
                        HarvestSeasonComparisonMetric.REALIZED_REVENUE,
                        harvestA,
                        harvestB,
                        harvest ->
                                harvest.realized() == null
                                        ? null
                                        : harvest.realized().realizedRevenue(),
                        false),
                comparisonDifference(
                        HarvestSeasonComparisonMetric.REALIZED_PROFIT,
                        harvestA,
                        harvestB,
                        harvest ->
                                harvest.realized() == null
                                        ? null
                                        : harvest.realized().realizedProfit(),
                        false),
                comparisonDifference(
                        HarvestSeasonComparisonMetric.REALIZED_MARGIN,
                        harvestA,
                        harvestB,
                        harvest ->
                                harvest.realized() == null
                                        ? null
                                        : harvest.realized().realizedMargin(),
                        false),
                comparisonDifference(
                        HarvestSeasonComparisonMetric.PLANNED_COST_PER_HECTARE,
                        harvestA,
                        harvestB,
                        harvest -> harvest.perHectare().plannedCostPerHectare(),
                        true),
                comparisonDifference(
                        HarvestSeasonComparisonMetric.PLANNED_REVENUE_PER_HECTARE,
                        harvestA,
                        harvestB,
                        harvest -> harvest.perHectare().plannedRevenuePerHectare(),
                        false),
                comparisonDifference(
                        HarvestSeasonComparisonMetric.PLANNED_RESULT_PER_HECTARE,
                        harvestA,
                        harvestB,
                        harvest -> harvest.perHectare().plannedResultPerHectare(),
                        false),
                comparisonDifference(
                        HarvestSeasonComparisonMetric.PROJECTED_COST_PER_HECTARE,
                        harvestA,
                        harvestB,
                        harvest -> harvest.perHectare().projectedCostPerHectare(),
                        true),
                comparisonDifference(
                        HarvestSeasonComparisonMetric.PROJECTED_REVENUE_PER_HECTARE,
                        harvestA,
                        harvestB,
                        harvest -> harvest.perHectare().projectedRevenuePerHectare(),
                        false),
                comparisonDifference(
                        HarvestSeasonComparisonMetric.PROJECTED_PROFIT_PER_HECTARE,
                        harvestA,
                        harvestB,
                        harvest -> harvest.perHectare().projectedProfitPerHectare(),
                        false),
                comparisonDifference(
                        HarvestSeasonComparisonMetric.REALIZED_COST_PER_HECTARE,
                        harvestA,
                        harvestB,
                        harvest -> harvest.perHectare().realizedCostPerHectare(),
                        true),
                comparisonDifference(
                        HarvestSeasonComparisonMetric.REALIZED_REVENUE_PER_HECTARE,
                        harvestA,
                        harvestB,
                        harvest -> harvest.perHectare().realizedRevenuePerHectare(),
                        false),
                comparisonDifference(
                        HarvestSeasonComparisonMetric.REALIZED_PROFIT_PER_HECTARE,
                        harvestA,
                        harvestB,
                        harvest -> harvest.perHectare().realizedProfitPerHectare(),
                        false));
    }

    private List<HarvestSeasonComparisonBestResponse> bestMetrics(
            HarvestSeason harvestSeasonA,
            HarvestSeason harvestSeasonB,
            HarvestSeasonComparisonHarvestResponse harvestA,
            HarvestSeasonComparisonHarvestResponse harvestB,
            List<HarvestSeasonComparisonDifferenceResponse> differences) {
        if (!harvestSeasonA
                .getProductionActivity()
                .getId()
                .equals(harvestSeasonB.getProductionActivity().getId())) {
            return List.of();
        }

        return differences.stream()
                .filter(difference -> difference.difference() != null)
                .filter(
                        difference ->
                                difference.metric().direction()
                                        != HarvestSeasonComparisonDirection.NO_COMPARISON)
                .map(
                        difference ->
                                new HarvestSeasonComparisonBestResponse(
                                        difference.metric(),
                                        bestHarvestSeasonIds(
                                                difference, harvestA.id(), harvestB.id())))
                .toList();
    }

    private List<Long> bestHarvestSeasonIds(
            HarvestSeasonComparisonDifferenceResponse difference,
            Long harvestAId,
            Long harvestBId) {
        int comparison = difference.difference().compareTo(BigDecimal.ZERO);
        if (comparison == 0) {
            return List.of(harvestAId, harvestBId);
        }

        boolean harvestBIsBest =
                (comparison > 0
                                && difference.metric().direction()
                                        == HarvestSeasonComparisonDirection.HIGHER_IS_BETTER)
                        || (comparison < 0
                                && difference.metric().direction()
                                        == HarvestSeasonComparisonDirection.LOWER_IS_BETTER);
        return List.of(harvestBIsBest ? harvestBId : harvestAId);
    }

    private HarvestSeasonComparisonDifferenceResponse comparisonDifference(
            HarvestSeasonComparisonMetric metric,
            HarvestSeasonComparisonHarvestResponse harvestA,
            HarvestSeasonComparisonHarvestResponse harvestB,
            Function<HarvestSeasonComparisonHarvestResponse, BigDecimal> valueExtractor,
            boolean lowerIsBetter) {
        BigDecimal valueA = valueExtractor.apply(harvestA);
        BigDecimal valueB = valueExtractor.apply(harvestB);

        if (valueA == null || valueB == null) {
            return new HarvestSeasonComparisonDifferenceResponse(metric, null, null, null);
        }

        BigDecimal difference = valueB.subtract(valueA);
        return new HarvestSeasonComparisonDifferenceResponse(
                metric,
                difference,
                percentageDifference(difference, valueA),
                comparisonSemantic(difference, lowerIsBetter));
    }

    private BigDecimal percentageDifference(BigDecimal difference, BigDecimal base) {
        if (base.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }

        return difference
                .multiply(BigDecimal.valueOf(100))
                .divide(base.abs(), 2, RoundingMode.HALF_UP);
    }

    private ComparisonSemantic comparisonSemantic(BigDecimal difference, boolean lowerIsBetter) {
        int comparison = difference.compareTo(BigDecimal.ZERO);

        if (comparison == 0) {
            return ComparisonSemantic.NEUTRAL;
        }

        if ((comparison < 0 && lowerIsBetter) || (comparison > 0 && !lowerIsBetter)) {
            return ComparisonSemantic.BETTER;
        }

        return ComparisonSemantic.WORSE;
    }

    private boolean isHighlightCandidate(HarvestSeasonComparisonDifferenceResponse difference) {
        return switch (difference.metric()) {
            case PLANNED_COST,
                            PLANNED_REVENUE,
                            PLANNED_RESULT,
                            PROJECTED_COST,
                            PROJECTED_REVENUE,
                            PROJECTED_PROFIT,
                            REALIZED_COST,
                            REALIZED_REVENUE,
                            REALIZED_PROFIT ->
                    true;
            default -> false;
        };
    }

    private void ensureHarvestSeasonBelongsToFarm(HarvestSeason harvestSeason, Farm farm) {
        if (!harvestSeason.getFarm().getId().equals(farm.getId())) {
            throw new BusinessException("Harvest seasons must belong to the selected farm.");
        }
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
