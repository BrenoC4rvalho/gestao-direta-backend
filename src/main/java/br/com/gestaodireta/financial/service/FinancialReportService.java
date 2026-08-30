package br.com.gestaodireta.financial.service;

import br.com.gestaodireta.farm.entity.Farm;
import br.com.gestaodireta.farm.service.FarmService;
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
        LocalDate cutoffDate = normalizedFilter.endDate();
        LocalDate today = LocalDate.now(clock);
        LocalDate next30End = today.plusDays(30);
        boolean next30DaysAvailable = !cutoffDate.isBefore(next30End);
        FinancialReportCommitmentsResponse commitments =
                financialReportRepository.summarizeCommitments(
                        normalizedFilter, cutoffDate, today, next30End, next30DaysAvailable);
        List<FinancialEvolutionPointResponse> evolution =
                fillMissingMonths(
                        normalizedFilter,
                        financialReportRepository.findEvolution(normalizedFilter));
        List<FinancialCategorySummaryResponse> categories =
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
                indicators(evolution, categories, harvests),
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
                categoryIds);
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

    private List<FinancialEvolutionPointResponse> fillMissingMonths(
            FinancialReportFilter filter, List<FinancialEvolutionPointResponse> points) {
        java.util.Map<YearMonth, FinancialEvolutionPointResponse> byMonth =
                points.stream()
                        .collect(
                                java.util.stream.Collectors.toMap(
                                        point -> YearMonth.from(point.periodStart()),
                                        point -> point));
        YearMonth current = YearMonth.from(filter.startDate());
        YearMonth end = YearMonth.from(filter.endDate());
        java.util.ArrayList<FinancialEvolutionPointResponse> result = new java.util.ArrayList<>();
        while (!current.isAfter(end)) {
            FinancialEvolutionPointResponse point = byMonth.get(current);
            if (point == null) {
                LocalDate periodStart = current.atDay(1);
                point =
                        new FinancialEvolutionPointResponse(
                                current.toString(),
                                monthLabel(periodStart),
                                periodStart,
                                current.atEndOfMonth(),
                                BigDecimal.ZERO,
                                BigDecimal.ZERO,
                                BigDecimal.ZERO,
                                0);
            }
            result.add(point);
            current = current.plusMonths(1);
        }
        return result;
    }

    private FinancialReportIndicatorsResponse indicators(
            List<FinancialEvolutionPointResponse> evolution,
            List<FinancialCategorySummaryResponse> categories,
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
