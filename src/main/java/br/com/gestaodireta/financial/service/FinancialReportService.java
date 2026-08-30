package br.com.gestaodireta.financial.service;

import br.com.gestaodireta.farm.entity.Farm;
import br.com.gestaodireta.farm.service.FarmService;
import br.com.gestaodireta.financial.dto.FinancialCategorySummaryGroupResponse;
import br.com.gestaodireta.financial.dto.FinancialCategorySummaryResponse;
import br.com.gestaodireta.financial.dto.FinancialEvolutionPointResponse;
import br.com.gestaodireta.financial.dto.FinancialHarvestSummaryResponse;
import br.com.gestaodireta.financial.dto.FinancialReportCategoryIndicatorResponse;
import br.com.gestaodireta.financial.dto.FinancialReportCommitmentsResponse;
import br.com.gestaodireta.financial.dto.FinancialReportFilter;
import br.com.gestaodireta.financial.dto.FinancialReportHarvestIndicatorResponse;
import br.com.gestaodireta.financial.dto.FinancialReportIndicatorsResponse;
import br.com.gestaodireta.financial.dto.FinancialReportPeriodIndicatorResponse;
import br.com.gestaodireta.financial.dto.FinancialReportResponse;
import br.com.gestaodireta.financial.dto.FinancialReportSummaryResponse;
import br.com.gestaodireta.financial.dto.FinancialReportTransactionResponse;
import br.com.gestaodireta.financial.dto.FinancialReportUnallocatedResponse;
import br.com.gestaodireta.financial.entity.FinancialCategory;
import br.com.gestaodireta.financial.enumeration.FinancialReportGranularity;
import br.com.gestaodireta.financial.enumeration.TransactionType;
import br.com.gestaodireta.financial.repository.FinancialCategoryRepository;
import br.com.gestaodireta.financial.repository.FinancialReportRepository;
import br.com.gestaodireta.harvest.entity.HarvestSeason;
import br.com.gestaodireta.harvest.repository.HarvestSeasonRepository;
import br.com.gestaodireta.shared.exception.BusinessException;
import br.com.gestaodireta.shared.exception.ResourceNotFoundException;
import br.com.gestaodireta.shared.exception.ValidationException;
import br.com.gestaodireta.shared.response.PageResponse;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
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

    private final Clock clock;

    public FinancialReportService(
            FinancialReportRepository financialReportRepository,
            FarmService farmService,
            FinancialCategoryRepository financialCategoryRepository,
            HarvestSeasonRepository harvestSeasonRepository,
            Clock clock) {
        this.financialReportRepository = financialReportRepository;
        this.farmService = farmService;
        this.financialCategoryRepository = financialCategoryRepository;
        this.harvestSeasonRepository = harvestSeasonRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public FinancialReportResponse getReport(FinancialReportFilter filter) {
        FinancialReportFilter normalizedFilter = validateAndNormalize(filter);
        FinancialReportSummaryResponse summary =
                financialReportRepository.summarize(normalizedFilter);
        LocalDate today = LocalDate.now(clock);
        LocalDate cutoffDate = normalizedFilter.endDate();
        LocalDate referenceDate = cutoffDate.isBefore(today) ? cutoffDate : today;
        LocalDate next30End = today.plusDays(30);
        boolean next30DaysAvailable = !cutoffDate.isBefore(next30End);
        FinancialReportCommitmentsResponse commitments =
                financialReportRepository.summarizeCommitments(
                        normalizedFilter, cutoffDate, today, next30End, next30DaysAvailable);
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
        List<FinancialCategorySummaryGroupResponse> categories =
                financialReportRepository.findCategories(normalizedFilter);
        List<FinancialHarvestSummaryResponse> harvests =
                financialReportRepository.findHarvests(normalizedFilter);
        FinancialReportUnallocatedResponse unallocated =
                financialReportRepository.findUnallocated(normalizedFilter);

        return new FinancialReportResponse(
                normalizedFilter.farmId(),
                normalizedFilter.startDate(),
                normalizedFilter.endDate(),
                normalizedFilter.basis(),
                summary,
                commitments,
                evolution,
                categories,
                harvests,
                indicators(performanceEvolution, categories, harvests),
                unallocated);
    }

    @Transactional(readOnly = true)
    public PageResponse<FinancialReportTransactionResponse> findTransactions(
            FinancialReportFilter filter, int page, int size, String sort, String direction) {
        FinancialReportFilter normalizedFilter = validateAndNormalize(filter);
        int normalizedPage = Math.max(page, 0);
        int normalizedSize = size <= 0 ? 20 : Math.min(size, 100);
        String normalizedDirection = "DESC".equalsIgnoreCase(direction) ? "DESC" : "ASC";
        return financialReportRepository.findTransactions(
                normalizedFilter, normalizedPage, normalizedSize, sort, normalizedDirection);
    }

    private FinancialReportFilter validateAndNormalize(FinancialReportFilter filter) {
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

        Farm farm = farmService.findEntityById(filter.farmId());
        List<Long> categoryIds = normalizeIds(filter.categoryIds());
        List<Long> harvestSeasonIds = normalizeIds(filter.harvestSeasonIds());
        validateCategories(categoryIds, farm);
        validateHarvestSeasons(harvestSeasonIds, farm);
        return new FinancialReportFilter(
                filter.farmId(),
                filter.startDate(),
                filter.endDate(),
                filter.basis(),
                harvestSeasonIds,
                categoryIds,
                filter.granularity() == null
                        ? FinancialReportGranularity.MONTHLY
                        : filter.granularity());
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

    private void validateHarvestSeasons(List<Long> harvestSeasonIds, Farm farm) {
        if (harvestSeasonIds == null) {
            return;
        }
        List<HarvestSeason> seasons = harvestSeasonRepository.findAllById(harvestSeasonIds);
        if (seasons.size() != harvestSeasonIds.size()) {
            throw new ResourceNotFoundException("Harvest season not found");
        }
        if (seasons.stream().anyMatch(season -> !season.getFarm().getId().equals(farm.getId()))) {
            throw new BusinessException("Harvest season does not belong to farm");
        }
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
}
