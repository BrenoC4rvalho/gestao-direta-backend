package br.com.gestaodireta.harvest.service;

import br.com.gestaodireta.farm.entity.Farm;
import br.com.gestaodireta.farm.enumeration.FarmStatus;
import br.com.gestaodireta.farm.service.FarmService;
import br.com.gestaodireta.financial.repository.FinancialTransactionRepository;
import br.com.gestaodireta.financial.repository.HarvestSeasonFinancialSummaryProjection;
import br.com.gestaodireta.financial.repository.HarvestSeasonFinancialTotalsProjection;
import br.com.gestaodireta.harvest.dto.HarvestComparisonSummaryResponse;
import br.com.gestaodireta.harvest.dto.HarvestPlanningSummaryResponse;
import br.com.gestaodireta.harvest.dto.HarvestProjectionSummaryResponse;
import br.com.gestaodireta.harvest.dto.HarvestRealizedSummaryResponse;
import br.com.gestaodireta.harvest.dto.HarvestSeasonFinancialSummaryResponse;
import br.com.gestaodireta.harvest.dto.HarvestSeasonRequest;
import br.com.gestaodireta.harvest.dto.HarvestSeasonResponse;
import br.com.gestaodireta.harvest.dto.HarvestSeasonStatusUpdateRequest;
import br.com.gestaodireta.harvest.dto.HarvestSeasonSummaryListResponse;
import br.com.gestaodireta.harvest.dto.HarvestSeasonSummaryResponse;
import br.com.gestaodireta.harvest.dto.HarvestSeasonUpdateRequest;
import br.com.gestaodireta.harvest.entity.HarvestSeason;
import br.com.gestaodireta.harvest.entity.ProductionActivity;
import br.com.gestaodireta.harvest.enumeration.CostVarianceStatus;
import br.com.gestaodireta.harvest.enumeration.HarvestSeasonStatus;
import br.com.gestaodireta.harvest.enumeration.ProductionActivityStatus;
import br.com.gestaodireta.harvest.enumeration.ProfitPerformanceStatus;
import br.com.gestaodireta.harvest.mapper.HarvestSeasonMapper;
import br.com.gestaodireta.harvest.repository.HarvestSeasonRepository;
import br.com.gestaodireta.harvest.repository.HarvestSeasonSummaryListProjection;
import br.com.gestaodireta.shared.exception.BusinessException;
import br.com.gestaodireta.shared.exception.ResourceNotFoundException;
import br.com.gestaodireta.shared.pagination.PaginationParams;
import br.com.gestaodireta.shared.response.PageResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HarvestSeasonService {

    private final HarvestSeasonRepository harvestSeasonRepository;

    private final FarmService farmService;

    private final FinancialTransactionRepository financialTransactionRepository;

    private final ProductionActivityService productionActivityService;

    private final HarvestSeasonMapper harvestSeasonMapper;

    public HarvestSeasonService(
            HarvestSeasonRepository harvestSeasonRepository,
            FarmService farmService,
            FinancialTransactionRepository financialTransactionRepository,
            ProductionActivityService productionActivityService,
            HarvestSeasonMapper harvestSeasonMapper) {
        this.harvestSeasonRepository = harvestSeasonRepository;
        this.farmService = farmService;
        this.financialTransactionRepository = financialTransactionRepository;
        this.productionActivityService = productionActivityService;
        this.harvestSeasonMapper = harvestSeasonMapper;
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
                request.expectedRevenue(),
                request.expectedCost(),
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
        List<Long> resolvedProductionActivityIds =
                resolveProductionActivityIds(productionActivityId, productionActivityIds);
        validateProductionActivitiesBelongToFarm(resolvedProductionActivityIds, farmId);

        List<HarvestSeason> seasons =
                harvestSeasonRepository.findAllForFinancialSummary(
                        farmId,
                        !resolvedStatuses.isEmpty(),
                        statusesForQuery(resolvedStatuses),
                        !resolvedProductionActivityIds.isEmpty(),
                        idsForQuery(resolvedProductionActivityIds),
                        periodStart != null,
                        periodStart,
                        periodEnd != null,
                        periodEnd,
                        normalizeSearch(search));

        BigDecimal plannedCost =
                seasons.stream()
                        .map(HarvestSeason::getExpectedCost)
                        .map(this::zeroIfNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal plannedRevenue =
                seasons.stream()
                        .map(HarvestSeason::getExpectedRevenue)
                        .map(this::zeroIfNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal plannedProfit = plannedRevenue.subtract(plannedCost);
        long activeHarvestCount = seasons.stream().filter(this::isActiveHarvestSeason).count();

        BigDecimal realizedCost = BigDecimal.ZERO;
        BigDecimal realizedRevenue = BigDecimal.ZERO;
        BigDecimal openCost = BigDecimal.ZERO;
        BigDecimal openRevenue = BigDecimal.ZERO;
        List<Long> seasonIds = seasons.stream().map(HarvestSeason::getId).toList();

        if (!seasonIds.isEmpty()) {
            HarvestSeasonFinancialTotalsProjection totals =
                    financialTransactionRepository.summarizeHarvestSeasonFinancialTotals(
                            farmId, seasonIds);
            realizedCost = zeroIfNull(totals.getRealizedCost());
            realizedRevenue = zeroIfNull(totals.getRealizedRevenue());
            openCost = zeroIfNull(totals.getOpenCost());
            openRevenue = zeroIfNull(totals.getOpenRevenue());
        }

        BigDecimal realizedProfit = realizedRevenue.subtract(realizedCost);
        BigDecimal projectedCost = realizedCost.add(openCost);
        BigDecimal projectedRevenue = realizedRevenue.add(openRevenue);
        BigDecimal projectedProfit = projectedRevenue.subtract(projectedCost);
        BigDecimal costVarianceAmount = realizedCost.subtract(plannedCost);

        return new HarvestSeasonFinancialSummaryResponse(
                farmId,
                activeHarvestCount,
                new HarvestPlanningSummaryResponse(plannedCost, plannedRevenue, plannedProfit),
                new HarvestRealizedSummaryResponse(realizedCost, realizedRevenue, realizedProfit),
                new HarvestProjectionSummaryResponse(
                        projectedCost, projectedRevenue, projectedProfit),
                new HarvestComparisonSummaryResponse(
                        percentage(realizedProfit, plannedProfit.abs()),
                        profitPerformanceStatus(realizedProfit, plannedProfit),
                        costVarianceAmount,
                        percentage(costVarianceAmount, plannedCost),
                        costVarianceStatus(costVarianceAmount, plannedCost)));
    }

    @Transactional(readOnly = true)
    public HarvestSeasonResponse findById(Long id) {
        return harvestSeasonMapper.toResponse(findEntityById(id));
    }

    @Transactional(readOnly = true)
    public HarvestSeasonSummaryResponse getSummary(Long id) {
        HarvestSeason harvestSeason = findEntityById(id);
        HarvestSeasonFinancialSummaryProjection summary =
                financialTransactionRepository.summarizeByHarvestSeasonId(id);

        BigDecimal expectedCost = zeroIfNull(harvestSeason.getExpectedCost());
        BigDecimal expectedRevenue = zeroIfNull(harvestSeason.getExpectedRevenue());
        BigDecimal expectedProfit = expectedRevenue.subtract(expectedCost);
        BigDecimal realizedCost = zeroIfNull(summary.getRealizedCost());
        BigDecimal realizedRevenue = zeroIfNull(summary.getRealizedRevenue());
        BigDecimal realizedProfit = realizedRevenue.subtract(realizedCost);
        BigDecimal areaHectares = harvestSeason.getAreaHectares();

        return new HarvestSeasonSummaryResponse(
                harvestSeason.getId(),
                harvestSeason.getName(),
                harvestSeason.getProductionActivity().getId(),
                harvestSeason.getProductionActivity().getName(),
                harvestSeason.getFarm().getId(),
                harvestSeason.getFarm().getName(),
                expectedCost,
                expectedRevenue,
                expectedProfit,
                realizedCost,
                realizedRevenue,
                realizedProfit,
                zeroIfNull(summary.getPendingExpenses()),
                zeroIfNull(summary.getOverdueExpenses()),
                zeroIfNull(summary.getPendingRevenue()),
                zeroIfNull(summary.getTransactionCount()),
                zeroIfNull(summary.getIncomeCount()),
                zeroIfNull(summary.getExpenseCount()),
                areaHectares,
                amountPerHectare(realizedCost, areaHectares),
                amountPerHectare(realizedRevenue, areaHectares),
                amountPerHectare(realizedProfit, areaHectares));
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
                request.expectedRevenue(),
                request.expectedCost(),
                request.areaHectares(),
                id);

        return harvestSeasonMapper.toResponse(harvestSeasonRepository.save(harvestSeason));
    }

    @Transactional
    public HarvestSeasonResponse updateStatus(Long id, HarvestSeasonStatusUpdateRequest request) {
        HarvestSeason harvestSeason = findEntityById(id);

        if (!HarvestSeasonStatus.INACTIVE.equals(request.status())) {
            ensureFarmIsActive(harvestSeason.getFarm());
        }

        harvestSeason.setStatus(request.status());

        return harvestSeasonMapper.toResponse(harvestSeasonRepository.save(harvestSeason));
    }

    @Transactional
    public HarvestSeasonResponse activate(Long id) {
        HarvestSeason harvestSeason = findEntityById(id);
        ensureFarmIsActive(harvestSeason.getFarm());
        harvestSeason.setStatus(HarvestSeasonStatus.PLANNED);

        return harvestSeasonMapper.toResponse(harvestSeasonRepository.save(harvestSeason));
    }

    @Transactional
    public void inactivate(Long id) {
        HarvestSeason harvestSeason = findEntityById(id);
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
            BigDecimal expectedRevenue,
            BigDecimal expectedCost,
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
        harvestSeason.setExpectedRevenue(expectedRevenue);
        harvestSeason.setExpectedCost(expectedCost);
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
                        ? harvestSeasonRepository.existsActiveByFarmIdAndNormalizedName(
                                farmId, normalizedName, HarvestSeasonStatus.INACTIVE)
                        : harvestSeasonRepository.existsActiveByFarmIdAndNormalizedNameAndIdNot(
                                farmId,
                                ignoredSeasonId,
                                normalizedName,
                                HarvestSeasonStatus.INACTIVE);

        if (duplicateExists) {
            throw new BusinessException(
                    "A harvest season with this name already exists for this farm.");
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

    private BigDecimal percentage(BigDecimal value, BigDecimal base) {
        if (BigDecimal.ZERO.compareTo(base) == 0) {
            return null;
        }

        return value.multiply(BigDecimal.valueOf(100)).divide(base, 2, RoundingMode.HALF_UP);
    }

    private ProfitPerformanceStatus profitPerformanceStatus(
            BigDecimal realizedProfit, BigDecimal plannedProfit) {
        if (BigDecimal.ZERO.compareTo(plannedProfit) == 0) {
            return ProfitPerformanceStatus.NOT_APPLICABLE;
        }

        int comparison = realizedProfit.compareTo(plannedProfit);
        if (comparison > 0) {
            return ProfitPerformanceStatus.ABOVE_PLANNED;
        }
        if (comparison < 0) {
            return ProfitPerformanceStatus.BELOW_PLANNED;
        }
        return ProfitPerformanceStatus.ON_TARGET;
    }

    private CostVarianceStatus costVarianceStatus(
            BigDecimal costVarianceAmount, BigDecimal plannedCost) {
        if (BigDecimal.ZERO.compareTo(plannedCost) == 0) {
            return CostVarianceStatus.NOT_APPLICABLE;
        }

        int comparison = costVarianceAmount.compareTo(BigDecimal.ZERO);
        if (comparison > 0) {
            return CostVarianceStatus.ABOVE_PLANNED;
        }
        if (comparison < 0) {
            return CostVarianceStatus.BELOW_PLANNED;
        }
        return CostVarianceStatus.ON_TARGET;
    }

    private BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private Long zeroIfNull(Long value) {
        return value == null ? 0L : value;
    }

    private BigDecimal amountPerHectare(BigDecimal amount, BigDecimal areaHectares) {
        if (areaHectares == null || BigDecimal.ZERO.compareTo(areaHectares) == 0) {
            return null;
        }

        return amount.divide(areaHectares, 2, RoundingMode.HALF_UP);
    }
}
