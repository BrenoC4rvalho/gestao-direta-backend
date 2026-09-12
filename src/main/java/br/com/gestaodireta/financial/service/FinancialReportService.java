package br.com.gestaodireta.financial.service;

import br.com.gestaodireta.farm.entity.Farm;
import br.com.gestaodireta.farm.service.FarmService;
import br.com.gestaodireta.financial.dto.FinancialCashFlowOpeningResponse;
import br.com.gestaodireta.financial.dto.FinancialCashFlowPointResponse;
import br.com.gestaodireta.financial.dto.FinancialCashFlowResponse;
import br.com.gestaodireta.financial.dto.FinancialCategorySummaryGroupResponse;
import br.com.gestaodireta.financial.dto.FinancialCategorySummaryResponse;
import br.com.gestaodireta.financial.dto.FinancialCumulativeEvolutionPointResponse;
import br.com.gestaodireta.financial.dto.FinancialEfficiencyIndicatorsResponse;
import br.com.gestaodireta.financial.dto.FinancialEvolutionPointResponse;
import br.com.gestaodireta.financial.dto.FinancialHarvestPlanningComparisonResponse;
import br.com.gestaodireta.financial.dto.FinancialHarvestSummaryDetails;
import br.com.gestaodireta.financial.dto.FinancialHarvestSummaryResponse;
import br.com.gestaodireta.financial.dto.FinancialIndicatorsResponse;
import br.com.gestaodireta.financial.dto.FinancialLiquidityIndicatorsResponse;
import br.com.gestaodireta.financial.dto.FinancialPlanningIndicatorsResponse;
import br.com.gestaodireta.financial.dto.FinancialReportCategoryIndicatorResponse;
import br.com.gestaodireta.financial.dto.FinancialReportCommitmentsResponse;
import br.com.gestaodireta.financial.dto.FinancialReportComparisonMetricResponse;
import br.com.gestaodireta.financial.dto.FinancialReportComparisonResponse;
import br.com.gestaodireta.financial.dto.FinancialReportFilter;
import br.com.gestaodireta.financial.dto.FinancialReportHarvestIndicatorResponse;
import br.com.gestaodireta.financial.dto.FinancialReportIndicatorsResponse;
import br.com.gestaodireta.financial.dto.FinancialReportPeriodIndicatorResponse;
import br.com.gestaodireta.financial.dto.FinancialReportResponse;
import br.com.gestaodireta.financial.dto.FinancialReportSummaryResponse;
import br.com.gestaodireta.financial.dto.FinancialReportSummaryWithComparison;
import br.com.gestaodireta.financial.dto.FinancialReportTransactionResponse;
import br.com.gestaodireta.financial.dto.FinancialReportUnallocatedResponse;
import br.com.gestaodireta.financial.dto.FinancialResultIndicatorsResponse;
import br.com.gestaodireta.financial.dto.FinancialRuralManagementIndicatorsResponse;
import br.com.gestaodireta.financial.entity.FinancialCategory;
import br.com.gestaodireta.financial.enumeration.FinancialPlanningAvailability;
import br.com.gestaodireta.financial.enumeration.FinancialReportGranularity;
import br.com.gestaodireta.financial.enumeration.TransactionType;
import br.com.gestaodireta.financial.repository.FinancialCategoryRepository;
import br.com.gestaodireta.financial.repository.FinancialReportRepository;
import br.com.gestaodireta.harvest.dto.PlanningComparisonMetricResponse;
import br.com.gestaodireta.harvest.entity.HarvestSeason;
import br.com.gestaodireta.harvest.entity.HarvestSeasonBudgetItem;
import br.com.gestaodireta.harvest.enumeration.ComparisonSemantic;
import br.com.gestaodireta.harvest.enumeration.PlanningComparisonDifferenceUnit;
import br.com.gestaodireta.harvest.repository.HarvestSeasonBudgetItemRepository;
import br.com.gestaodireta.harvest.repository.HarvestSeasonRepository;
import br.com.gestaodireta.harvest.service.HarvestFinancialSummaryCalculator;
import br.com.gestaodireta.shared.exception.BusinessException;
import br.com.gestaodireta.shared.exception.ResourceNotFoundException;
import br.com.gestaodireta.shared.exception.ValidationException;
import br.com.gestaodireta.shared.response.PageResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FinancialReportService {

    private final FinancialReportRepository financialReportRepository;
    private final FarmService farmService;
    private final FinancialCategoryRepository financialCategoryRepository;
    private final HarvestSeasonRepository harvestSeasonRepository;
    private final HarvestSeasonBudgetItemRepository harvestSeasonBudgetItemRepository;
    private final HarvestFinancialSummaryCalculator harvestFinancialSummaryCalculator;

    private final Clock clock;

    public FinancialReportService(
            FinancialReportRepository financialReportRepository,
            FarmService farmService,
            FinancialCategoryRepository financialCategoryRepository,
            HarvestSeasonRepository harvestSeasonRepository,
            HarvestSeasonBudgetItemRepository harvestSeasonBudgetItemRepository,
            HarvestFinancialSummaryCalculator harvestFinancialSummaryCalculator,
            Clock clock) {
        this.financialReportRepository = financialReportRepository;
        this.farmService = farmService;
        this.financialCategoryRepository = financialCategoryRepository;
        this.harvestSeasonRepository = harvestSeasonRepository;
        this.harvestSeasonBudgetItemRepository = harvestSeasonBudgetItemRepository;
        this.harvestFinancialSummaryCalculator = harvestFinancialSummaryCalculator;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public FinancialReportResponse getReport(FinancialReportFilter filter) {
        ValidatedFilter validatedFilter = validateAndNormalize(filter);
        FinancialReportFilter normalizedFilter = validatedFilter.filter();
        LocalDate previousEndDate = normalizedFilter.startDate().minusDays(1);
        long periodDays =
                ChronoUnit.DAYS.between(normalizedFilter.startDate(), normalizedFilter.endDate())
                        + 1;
        LocalDate previousStartDate = previousEndDate.minusDays(periodDays - 1);
        FinancialReportSummaryWithComparison summaryWithComparison =
                financialReportRepository.summarize(
                        normalizedFilter, previousStartDate, previousEndDate);
        FinancialReportSummaryResponse summary = summaryWithComparison.summary();
        LocalDate today = LocalDate.now(clock);
        LocalDate cutoffDate = normalizedFilter.endDate();
        LocalDate referenceDate = cutoffDate.isBefore(today) ? cutoffDate : today;
        LocalDate next30End = today.plusDays(30);
        boolean next30DaysAvailable = !cutoffDate.isBefore(next30End);
        FinancialReportCommitmentsResponse commitments =
                financialReportRepository.summarizeCommitments(
                        normalizedFilter, cutoffDate, today, next30End, next30DaysAvailable);
        FinancialReportCommitmentsResponse indicatorCommitments =
                financialReportRepository.summarizeFilteredCommitments(
                        normalizedFilter, cutoffDate);
        FinancialReportFilter monthlyFilter =
                withGranularity(normalizedFilter, FinancialReportGranularity.MONTHLY);
        List<FinancialEvolutionPointResponse> monthlyEvolution =
                fillMissingPeriods(
                        monthlyFilter,
                        financialReportRepository.findEvolution(monthlyFilter, referenceDate),
                        today);
        List<FinancialEvolutionPointResponse> performanceEvolution =
                fillMissingPeriods(
                        monthlyFilter,
                        financialReportRepository.findPerformanceEvolution(monthlyFilter),
                        today);
        List<FinancialEvolutionPointResponse> evolution =
                FinancialReportGranularity.MONTHLY.equals(normalizedFilter.granularity())
                        ? monthlyEvolution
                        : fillMissingPeriods(
                                normalizedFilter,
                                financialReportRepository.findEvolution(
                                        normalizedFilter, referenceDate),
                                today);
        List<FinancialCumulativeEvolutionPointResponse> realizedCumulativeEvolution =
                realizedCumulativeEvolution(evolution);
        FinancialCashFlowResponse cashFlow =
                cashFlow(
                        normalizedFilter,
                        financialReportRepository.findCashFlowOpening(
                                normalizedFilter, referenceDate),
                        financialReportRepository.findEvolution(normalizedFilter, referenceDate),
                        today);
        List<FinancialCategorySummaryGroupResponse> categories =
                financialReportRepository.findCategories(normalizedFilter);
        List<FinancialHarvestSummaryResponse> harvests =
                enrichHarvests(
                        financialReportRepository.findHarvests(normalizedFilter),
                        normalizedFilter,
                        summary.totalIncome());
        FinancialReportUnallocatedResponse unallocated =
                financialReportRepository.findUnallocated(normalizedFilter);

        return new FinancialReportResponse(
                normalizedFilter.farmId(),
                normalizedFilter.startDate(),
                normalizedFilter.endDate(),
                normalizedFilter.basis(),
                summary,
                comparison(
                        normalizedFilter,
                        previousStartDate,
                        previousEndDate,
                        summaryWithComparison),
                commitments,
                evolution,
                realizedCumulativeEvolution,
                cashFlow,
                categories,
                harvests,
                financialIndicators(
                        summary,
                        indicatorCommitments,
                        validatedFilter.farm(),
                        validatedFilter.harvestSeasons(),
                        normalizedFilter),
                indicators(performanceEvolution, categories, harvests),
                unallocated);
    }

    private FinancialReportComparisonResponse comparison(
            FinancialReportFilter filter,
            LocalDate previousStartDate,
            LocalDate previousEndDate,
            FinancialReportSummaryWithComparison summaryWithComparison) {
        boolean previousDataAvailable =
                summaryWithComparison.previousRealizedTransactionCount() > 0;
        FinancialReportSummaryResponse summary = summaryWithComparison.summary();
        BigDecimal previousResult =
                summaryWithComparison
                        .previousRealizedIncome()
                        .subtract(summaryWithComparison.previousRealizedExpense());

        return new FinancialReportComparisonResponse(
                filter.startDate(),
                filter.endDate(),
                previousStartDate,
                previousEndDate,
                previousDataAvailable,
                comparisonMetric(
                        summary.realizedIncome(),
                        summaryWithComparison.previousRealizedIncome(),
                        previousDataAvailable,
                        false),
                comparisonMetric(
                        summary.realizedExpense(),
                        summaryWithComparison.previousRealizedExpense(),
                        previousDataAvailable,
                        true),
                comparisonMetric(
                        summary.realizedIncome().subtract(summary.realizedExpense()),
                        previousResult,
                        previousDataAvailable,
                        false));
    }

    private FinancialReportComparisonMetricResponse comparisonMetric(
            BigDecimal current,
            BigDecimal previous,
            boolean previousDataAvailable,
            boolean lowerIsBetter) {
        if (!previousDataAvailable) {
            return new FinancialReportComparisonMetricResponse(
                    current, null, null, null, ComparisonSemantic.NEUTRAL);
        }

        PlanningComparisonMetricResponse comparison =
                harvestFinancialSummaryCalculator.comparisonMetric(
                        previous, current, lowerIsBetter, PlanningComparisonDifferenceUnit.AMOUNT);
        return new FinancialReportComparisonMetricResponse(
                current,
                previous,
                comparison.difference(),
                comparison.percentageDifference(),
                comparison.semantic());
    }

    static List<FinancialCumulativeEvolutionPointResponse> realizedCumulativeEvolution(
            List<FinancialEvolutionPointResponse> evolution) {
        ArrayList<FinancialCumulativeEvolutionPointResponse> result = new ArrayList<>();
        BigDecimal cumulativeIncome = BigDecimal.ZERO;
        BigDecimal cumulativeExpense = BigDecimal.ZERO;

        for (FinancialEvolutionPointResponse point : evolution) {
            cumulativeIncome = cumulativeIncome.add(point.realizedIncome());
            cumulativeExpense = cumulativeExpense.add(point.realizedExpense());
            result.add(
                    new FinancialCumulativeEvolutionPointResponse(
                            point.period(),
                            point.label(),
                            point.periodStart(),
                            point.periodEnd(),
                            cumulativeIncome,
                            cumulativeExpense));
        }

        return List.copyOf(result);
    }

    @Transactional(readOnly = true)
    public PageResponse<FinancialReportTransactionResponse> findTransactions(
            FinancialReportFilter filter, int page, int size, String sort, String direction) {
        FinancialReportFilter normalizedFilter = validateAndNormalize(filter).filter();
        int normalizedPage = Math.max(page, 0);
        int normalizedSize = size <= 0 ? 20 : Math.min(size, 100);
        String normalizedDirection = "DESC".equalsIgnoreCase(direction) ? "DESC" : "ASC";
        return financialReportRepository.findTransactions(
                normalizedFilter, normalizedPage, normalizedSize, sort, normalizedDirection);
    }

    private ValidatedFilter validateAndNormalize(FinancialReportFilter filter) {
        if (filter == null
                || filter.farmId() == null
                || filter.startDate() == null
                || filter.endDate() == null
                || filter.basis() == null) {
            throw new ValidationException("Farm, period and report basis are required");
        }
        if (filter.startDate().isAfter(filter.endDate())) {
            throw new ValidationException("Start date cannot be after end date");
        }
        if (filter.endDate().isAfter(filter.startDate().plusMonths(12))) {
            throw new ValidationException("Report period cannot exceed 12 months");
        }

        Farm farm = farmService.findEntityById(filter.farmId());
        List<Long> categoryIds = normalizeIds(filter.categoryIds());
        List<Long> harvestSeasonIds = normalizeIds(filter.harvestSeasonIds());
        validateCategories(categoryIds, farm);
        List<HarvestSeason> harvestSeasons = validateHarvestSeasons(harvestSeasonIds, farm);
        return new ValidatedFilter(
                new FinancialReportFilter(
                        filter.farmId(),
                        filter.startDate(),
                        filter.endDate(),
                        filter.basis(),
                        harvestSeasonIds,
                        categoryIds,
                        filter.granularity() == null
                                ? FinancialReportGranularity.MONTHLY
                                : filter.granularity()),
                farm,
                harvestSeasons);
    }

    private List<Long> normalizeIds(List<Long> ids) {
        if (ids == null) {
            return null;
        }
        List<Long> normalized = ids.stream().filter(Objects::nonNull).distinct().toList();
        return normalized.isEmpty() ? null : normalized;
    }

    private void validateCategories(List<Long> categoryIds, Farm farm) {
        if (categoryIds == null) {
            return;
        }
        List<FinancialCategory> categories = financialCategoryRepository.findAllById(categoryIds);
        if (categories.size() != categoryIds.size()) {
            throw new ResourceNotFoundException("Financial category not found");
        }
        if (categories.stream()
                .anyMatch(category -> !category.getFarm().getId().equals(farm.getId()))) {
            throw new BusinessException("Financial category does not belong to farm");
        }
    }

    private List<HarvestSeason> validateHarvestSeasons(List<Long> harvestSeasonIds, Farm farm) {
        if (harvestSeasonIds == null) {
            return List.of();
        }
        List<HarvestSeason> seasons = harvestSeasonRepository.findAllById(harvestSeasonIds);
        if (seasons.size() != harvestSeasonIds.size()) {
            throw new ResourceNotFoundException("Harvest season not found");
        }
        if (seasons.stream().anyMatch(season -> !season.getFarm().getId().equals(farm.getId()))) {
            throw new BusinessException("Harvest season does not belong to farm");
        }
        return seasons;
    }

    private FinancialReportFilter withGranularity(
            FinancialReportFilter filter, FinancialReportGranularity granularity) {
        return new FinancialReportFilter(
                filter.farmId(),
                filter.startDate(),
                filter.endDate(),
                filter.basis(),
                filter.harvestSeasonIds(),
                filter.categoryIds(),
                granularity);
    }

    private List<FinancialEvolutionPointResponse> fillMissingPeriods(
            FinancialReportFilter filter,
            List<FinancialEvolutionPointResponse> points,
            LocalDate today) {
        java.util.Map<LocalDate, FinancialEvolutionPointResponse> byPeriodStart =
                points.stream()
                        .collect(
                                java.util.stream.Collectors.toMap(
                                        FinancialEvolutionPointResponse::periodStart,
                                        point -> point));
        java.util.ArrayList<FinancialEvolutionPointResponse> result = new java.util.ArrayList<>();
        LocalDate current = periodStart(filter.startDate(), filter.granularity());
        LocalDate last = periodStart(filter.endDate(), filter.granularity());
        while (!current.isAfter(last)) {
            FinancialEvolutionPointResponse point = byPeriodStart.get(current);
            if (point == null) {
                point = emptyPeriod(current, filter.granularity());
            }
            result.add(normalizePeriod(point, filter, today));
            current =
                    FinancialReportGranularity.QUARTERLY.equals(filter.granularity())
                            ? current.plusMonths(3)
                            : current.plusMonths(1);
        }
        return result;
    }

    private FinancialCashFlowResponse cashFlow(
            FinancialReportFilter filter,
            FinancialCashFlowOpeningResponse opening,
            List<FinancialEvolutionPointResponse> points,
            LocalDate today) {
        java.util.Map<LocalDate, FinancialEvolutionPointResponse> byPeriodStart =
                points.stream()
                        .collect(
                                java.util.stream.Collectors.toMap(
                                        FinancialEvolutionPointResponse::periodStart,
                                        point -> point));
        java.util.ArrayList<FinancialCashFlowPointResponse> result = new java.util.ArrayList<>();
        BigDecimal expectedBalance = opening.expectedBalance();
        BigDecimal projectedBalance = opening.projectedBalance();
        LocalDate current = periodStart(filter.startDate(), filter.granularity());
        LocalDate last = periodStart(filter.endDate(), filter.granularity());
        boolean multipleYears = filter.startDate().getYear() != filter.endDate().getYear();

        while (!current.isAfter(last)) {
            FinancialEvolutionPointResponse point = byPeriodStart.get(current);
            if (point == null) {
                point = emptyPeriod(current, filter.granularity());
            }
            BigDecimal expectedChange =
                    point.realizedResult()
                            .add(point.projectedIncome())
                            .subtract(point.projectedExpense());
            expectedBalance = expectedBalance.add(expectedChange);
            projectedBalance =
                    projectedBalance
                            .add(expectedChange)
                            .add(point.overdueIncome())
                            .subtract(point.overdueExpense());
            LocalDate rawEnd = periodEnd(current, filter.granularity());
            result.add(
                    new FinancialCashFlowPointResponse(
                            periodKey(current, filter.granularity()),
                            periodLabel(current, filter.granularity(), multipleYears),
                            current.isBefore(filter.startDate()) ? filter.startDate() : current,
                            rawEnd.isAfter(filter.endDate()) ? filter.endDate() : rawEnd,
                            point.realizedIncome(),
                            point.realizedExpense(),
                            point.projectedIncome(),
                            point.projectedExpense(),
                            point.overdueIncome(),
                            point.overdueExpense(),
                            expectedBalance,
                            projectedBalance,
                            !today.isBefore(current) && !today.isAfter(rawEnd)));
            current =
                    FinancialReportGranularity.QUARTERLY.equals(filter.granularity())
                            ? current.plusMonths(3)
                            : current.plusMonths(1);
        }
        return new FinancialCashFlowResponse(
                opening.expectedBalance(), opening.projectedBalance(), result);
    }

    private FinancialEvolutionPointResponse emptyPeriod(
            LocalDate periodStart, FinancialReportGranularity granularity) {
        return new FinancialEvolutionPointResponse(
                periodStart.toString(),
                "",
                periodStart,
                periodEnd(periodStart, granularity),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                0,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                0,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                0,
                BigDecimal.ZERO,
                false);
    }

    private FinancialEvolutionPointResponse normalizePeriod(
            FinancialEvolutionPointResponse point, FinancialReportFilter filter, LocalDate today) {
        LocalDate rawStart = point.periodStart();
        LocalDate rawEnd = periodEnd(rawStart, filter.granularity());
        LocalDate periodStart =
                rawStart.isBefore(filter.startDate()) ? filter.startDate() : rawStart;
        LocalDate periodEnd = rawEnd.isAfter(filter.endDate()) ? filter.endDate() : rawEnd;
        boolean multipleYears = filter.startDate().getYear() != filter.endDate().getYear();
        return new FinancialEvolutionPointResponse(
                periodKey(rawStart, filter.granularity()),
                periodLabel(rawStart, filter.granularity(), multipleYears),
                periodStart,
                periodEnd,
                point.income(),
                point.expense(),
                point.netBalance(),
                point.transactionCount(),
                point.realizedIncome(),
                point.projectedIncome(),
                point.overdueIncome(),
                point.overdueIncomeCount(),
                point.realizedExpense(),
                point.projectedExpense(),
                point.overdueExpense(),
                point.overdueExpenseCount(),
                point.realizedResult(),
                !today.isBefore(rawStart) && !today.isAfter(rawEnd));
    }

    private LocalDate periodStart(LocalDate date, FinancialReportGranularity granularity) {
        if (FinancialReportGranularity.QUARTERLY.equals(granularity)) {
            int firstQuarterMonth = ((date.getMonthValue() - 1) / 3) * 3 + 1;
            return LocalDate.of(date.getYear(), firstQuarterMonth, 1);
        }
        return YearMonth.from(date).atDay(1);
    }

    private LocalDate periodEnd(LocalDate periodStart, FinancialReportGranularity granularity) {
        return FinancialReportGranularity.QUARTERLY.equals(granularity)
                ? periodStart.plusMonths(3).minusDays(1)
                : YearMonth.from(periodStart).atEndOfMonth();
    }

    private String periodKey(LocalDate periodStart, FinancialReportGranularity granularity) {
        if (FinancialReportGranularity.QUARTERLY.equals(granularity)) {
            return periodStart.getYear() + "-Q" + ((periodStart.getMonthValue() - 1) / 3 + 1);
        }
        return YearMonth.from(periodStart).toString();
    }

    private String periodLabel(
            LocalDate periodStart, FinancialReportGranularity granularity, boolean multipleYears) {
        if (FinancialReportGranularity.QUARTERLY.equals(granularity)) {
            String label = ((periodStart.getMonthValue() - 1) / 3 + 1) + "º tri";
            return multipleYears ? label + "/" + periodStart.getYear() : label;
        }
        String label = monthLabel(periodStart);
        return multipleYears
                ? label + "/" + String.format("%02d", periodStart.getYear() % 100)
                : label;
    }

    private FinancialIndicatorsResponse financialIndicators(
            FinancialReportSummaryResponse summary,
            FinancialReportCommitmentsResponse commitments,
            Farm farm,
            List<HarvestSeason> harvestSeasons,
            FinancialReportFilter filter) {
        BigDecimal realizedResult = summary.realizedIncome().subtract(summary.realizedExpense());
        BigDecimal projectedResult = summary.totalIncome().subtract(summary.totalExpense());
        BigDecimal availableResources = realizedResult.add(commitments.accountsReceivable());
        BigDecimal cashNeed = commitments.accountsPayable().subtract(availableResources);

        FinancialResultIndicatorsResponse result =
                new FinancialResultIndicatorsResponse(
                        summary.totalIncome(),
                        summary.totalExpense(),
                        projectedResult,
                        percentageOrNull(projectedResult, summary.totalIncome()),
                        summary.realizedIncome(),
                        summary.realizedExpense(),
                        realizedResult);
        FinancialLiquidityIndicatorsResponse liquidity =
                new FinancialLiquidityIndicatorsResponse(
                        commitments.accountsReceivable(),
                        commitments.accountsPayable(),
                        commitments.overdueReceivableAmount(),
                        commitments.overduePayableAmount(),
                        percentageOrNull(availableResources, commitments.accountsPayable()),
                        cashNeed);
        FinancialEfficiencyIndicatorsResponse efficiency =
                new FinancialEfficiencyIndicatorsResponse(
                        percentageOrNull(summary.totalExpense(), summary.totalIncome()),
                        percentageOrNull(realizedResult, summary.realizedExpense()));

        return new FinancialIndicatorsResponse(
                result,
                liquidity,
                efficiency,
                ruralManagementIndicators(summary, projectedResult, farm, harvestSeasons),
                planningIndicators(summary, harvestSeasons, filter));
    }

    private FinancialRuralManagementIndicatorsResponse ruralManagementIndicators(
            FinancialReportSummaryResponse summary,
            BigDecimal projectedResult,
            Farm farm,
            List<HarvestSeason> harvestSeasons) {
        BigDecimal area;
        if (harvestSeasons.isEmpty()) {
            area = farm.getTotalArea();
        } else if (harvestSeasons.size() == 1) {
            area = harvestSeasons.getFirst().getAreaHectares();
        } else {
            return null;
        }

        if (area == null || area.signum() <= 0) {
            return null;
        }

        return new FinancialRuralManagementIndicatorsResponse(
                area,
                amountPerHectare(summary.totalIncome(), area),
                amountPerHectare(summary.totalExpense(), area),
                amountPerHectare(projectedResult, area));
    }

    private List<FinancialHarvestSummaryResponse> enrichHarvests(
            List<FinancialHarvestSummaryResponse> harvests,
            FinancialReportFilter filter,
            BigDecimal totalIncome) {
        List<Long> harvestSeasonIds =
                harvests.stream()
                        .map(FinancialHarvestSummaryResponse::harvestSeasonId)
                        .filter(Objects::nonNull)
                        .toList();
        if (harvestSeasonIds.isEmpty()) {
            return harvests;
        }

        Map<Long, HarvestSeason> harvestsById =
                harvestSeasonRepository.findAllById(harvestSeasonIds).stream()
                        .collect(
                                java.util.stream.Collectors.toMap(
                                        HarvestSeason::getId, Function.identity()));
        List<Long> comparableHarvestIds =
                harvestsById.values().stream()
                        .filter(harvest -> isPlanningComparable(harvest, filter))
                        .map(HarvestSeason::getId)
                        .toList();
        Map<Long, List<HarvestSeasonBudgetItem>> budgetItemsByHarvest =
                comparableHarvestIds.isEmpty()
                        ? Map.of()
                        : budgetItemsByHarvest(comparableHarvestIds, filter.categoryIds());

        return harvests.stream()
                .map(
                        harvest ->
                                enrichHarvest(
                                        harvest,
                                        harvestsById.get(harvest.harvestSeasonId()),
                                        budgetItemsByHarvest.get(harvest.harvestSeasonId()),
                                        totalIncome))
                .toList();
    }

    private Map<Long, List<HarvestSeasonBudgetItem>> budgetItemsByHarvest(
            List<Long> harvestSeasonIds, List<Long> categoryIds) {
        return harvestSeasonBudgetItemRepository
                .findAllByHarvestSeasonIdIn(harvestSeasonIds)
                .stream()
                .filter(
                        item ->
                                categoryIds == null
                                        || (item.getCategory() != null
                                                && categoryIds.contains(
                                                        item.getCategory().getId())))
                .collect(
                        java.util.stream.Collectors.groupingBy(
                                item -> item.getHarvestSeason().getId()));
    }

    private FinancialHarvestSummaryResponse enrichHarvest(
            FinancialHarvestSummaryResponse harvest,
            HarvestSeason harvestSeason,
            List<HarvestSeasonBudgetItem> budgetItems,
            BigDecimal totalIncome) {
        if (harvestSeason == null) {
            return harvest;
        }

        FinancialHarvestPlanningComparisonResponse incomeComparison = null;
        FinancialHarvestPlanningComparisonResponse expenseComparison = null;
        FinancialHarvestPlanningComparisonResponse resultComparison = null;
        if (budgetItems != null && !budgetItems.isEmpty()) {
            BigDecimal plannedIncome = plannedAmount(budgetItems, TransactionType.INCOME);
            BigDecimal plannedExpense = plannedAmount(budgetItems, TransactionType.EXPENSE);
            incomeComparison = planningComparison(plannedIncome, harvest.income(), false);
            expenseComparison = planningComparison(plannedExpense, harvest.expense(), true);
            resultComparison =
                    planningComparison(
                            plannedIncome.subtract(plannedExpense), harvest.profit(), false);
        }

        BigDecimal area = harvestSeason.getAreaHectares();
        BigDecimal resultPerHectare =
                area == null || area.signum() <= 0
                        ? null
                        : amountPerHectare(harvest.profit(), area);
        BigDecimal revenueShare = percentageOrZero(harvest.income(), totalIncome);
        return new FinancialHarvestSummaryResponse(
                harvest.harvestSeasonId(),
                harvest.harvestSeasonName(),
                harvest.income(),
                harvest.expense(),
                harvest.profit(),
                harvest.marginPercentage(),
                harvest.transactionCount(),
                new FinancialHarvestSummaryDetails(
                        resultPerHectare,
                        revenueShare,
                        incomeComparison,
                        expenseComparison,
                        resultComparison));
    }

    private FinancialHarvestPlanningComparisonResponse planningComparison(
            BigDecimal planned, BigDecimal current, boolean lowerIsBetter) {
        PlanningComparisonMetricResponse comparison =
                harvestFinancialSummaryCalculator.comparisonMetric(
                        planned, current, lowerIsBetter, PlanningComparisonDifferenceUnit.AMOUNT);
        return new FinancialHarvestPlanningComparisonResponse(
                comparison.planned(),
                comparison.difference(),
                comparison.percentageDifference(),
                comparison.semantic());
    }

    private boolean isPlanningComparable(HarvestSeason harvest, FinancialReportFilter filter) {
        return harvest.getEndDate() != null
                && !filter.startDate().isAfter(harvest.getStartDate())
                && !filter.endDate().isBefore(harvest.getEndDate());
    }

    private FinancialPlanningIndicatorsResponse planningIndicators(
            FinancialReportSummaryResponse summary,
            List<HarvestSeason> harvestSeasons,
            FinancialReportFilter filter) {
        if (harvestSeasons.size() != 1) {
            return unavailablePlanning(FinancialPlanningAvailability.HARVEST_REQUIRED);
        }

        HarvestSeason harvestSeason = harvestSeasons.getFirst();
        if (harvestSeason.getEndDate() == null
                || filter.startDate().isAfter(harvestSeason.getStartDate())
                || filter.endDate().isBefore(harvestSeason.getEndDate())) {
            return unavailablePlanning(FinancialPlanningAvailability.PARTIAL_PERIOD);
        }

        List<HarvestSeasonBudgetItem> budgetItems =
                harvestSeasonBudgetItemRepository.findAllByHarvestSeasonId(harvestSeason.getId());
        if (filter.categoryIds() != null) {
            budgetItems =
                    budgetItems.stream()
                            .filter(item -> item.getCategory() != null)
                            .filter(
                                    item ->
                                            filter.categoryIds()
                                                    .contains(item.getCategory().getId()))
                            .toList();
        }

        BigDecimal plannedIncome = plannedAmount(budgetItems, TransactionType.INCOME);
        BigDecimal plannedExpense = plannedAmount(budgetItems, TransactionType.EXPENSE);
        if (plannedIncome.signum() == 0 && plannedExpense.signum() == 0) {
            return unavailablePlanning(FinancialPlanningAvailability.MISSING_PLANNING);
        }

        return new FinancialPlanningIndicatorsResponse(
                FinancialPlanningAvailability.AVAILABLE,
                percentageOrNull(summary.realizedIncome(), plannedIncome),
                percentageOrNull(summary.realizedExpense(), plannedExpense),
                plannedIncome.signum() == 0
                        ? null
                        : summary.realizedIncome().subtract(plannedIncome),
                plannedExpense.signum() == 0
                        ? null
                        : summary.realizedExpense().subtract(plannedExpense));
    }

    private FinancialPlanningIndicatorsResponse unavailablePlanning(
            FinancialPlanningAvailability availability) {
        return new FinancialPlanningIndicatorsResponse(availability, null, null, null, null);
    }

    private BigDecimal plannedAmount(
            List<HarvestSeasonBudgetItem> budgetItems, TransactionType type) {
        return budgetItems.stream()
                .filter(item -> type.equals(item.getType()))
                .map(HarvestSeasonBudgetItem::getPlannedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal percentageOrNull(BigDecimal value, BigDecimal base) {
        if (base.signum() == 0) {
            return null;
        }

        return value.multiply(BigDecimal.valueOf(100)).divide(base, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal percentageOrZero(BigDecimal value, BigDecimal base) {
        BigDecimal percentage = percentageOrNull(value, base);
        return percentage == null ? BigDecimal.ZERO : percentage;
    }

    private BigDecimal amountPerHectare(BigDecimal amount, BigDecimal area) {
        return amount.divide(area, 2, RoundingMode.HALF_UP);
    }

    private FinancialReportIndicatorsResponse indicators(
            List<FinancialEvolutionPointResponse> evolution,
            List<FinancialCategorySummaryGroupResponse> categories,
            List<FinancialHarvestSummaryResponse> harvests) {
        return new FinancialReportIndicatorsResponse(
                evolution.size(),
                periodIndicator(
                        evolution,
                        FinancialEvolutionPointResponse::income,
                        Comparator.naturalOrder()),
                periodIndicator(
                        evolution,
                        FinancialEvolutionPointResponse::expense,
                        Comparator.naturalOrder()),
                periodIndicator(
                        evolution,
                        FinancialEvolutionPointResponse::netBalance,
                        Comparator.naturalOrder()),
                periodIndicator(
                        evolution,
                        FinancialEvolutionPointResponse::netBalance,
                        Comparator.reverseOrder()),
                categories.stream()
                        .filter(category -> TransactionType.EXPENSE.equals(category.type()))
                        .flatMap(category -> category.items().stream())
                        .max(Comparator.comparing(FinancialCategorySummaryResponse::amount))
                        .map(
                                category ->
                                        new FinancialReportCategoryIndicatorResponse(
                                                category.categoryId(),
                                                category.categoryName(),
                                                category.amount()))
                        .orElse(null),
                harvests.stream()
                        .max(Comparator.comparing(FinancialHarvestSummaryResponse::profit))
                        .map(
                                harvest ->
                                        new FinancialReportHarvestIndicatorResponse(
                                                harvest.harvestSeasonId(),
                                                harvest.harvestSeasonName(),
                                                harvest.profit()))
                        .orElse(null));
    }

    private FinancialReportPeriodIndicatorResponse periodIndicator(
            List<FinancialEvolutionPointResponse> points,
            Function<FinancialEvolutionPointResponse, BigDecimal> amount,
            Comparator<BigDecimal> amountComparator) {
        return points.stream()
                .max(
                        Comparator.comparing(amount, amountComparator)
                                .thenComparing(FinancialEvolutionPointResponse::periodStart))
                .map(
                        point ->
                                new FinancialReportPeriodIndicatorResponse(
                                        point.period(), point.label(), amount.apply(point)))
                .orElse(null);
    }

    private String monthLabel(LocalDate date) {
        return switch (date.getMonthValue()) {
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

    private record ValidatedFilter(
            FinancialReportFilter filter, Farm farm, List<HarvestSeason> harvestSeasons) {}
}
