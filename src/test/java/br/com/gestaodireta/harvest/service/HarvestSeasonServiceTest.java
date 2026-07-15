package br.com.gestaodireta.harvest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.gestaodireta.farm.entity.Farm;
import br.com.gestaodireta.farm.enumeration.FarmStatus;
import br.com.gestaodireta.farm.repository.FarmRepository;
import br.com.gestaodireta.farm.repository.FarmUserRepository;
import br.com.gestaodireta.financial.entity.FinancialTransaction;
import br.com.gestaodireta.financial.enumeration.FinancialRecordStatus;
import br.com.gestaodireta.financial.enumeration.PaymentStatus;
import br.com.gestaodireta.financial.enumeration.TransactionType;
import br.com.gestaodireta.financial.repository.FinancialCategoryRepository;
import br.com.gestaodireta.financial.repository.FinancialTransactionRepository;
import br.com.gestaodireta.harvest.dto.HarvestSeasonFinancialSummaryResponse;
import br.com.gestaodireta.harvest.dto.HarvestSeasonRequest;
import br.com.gestaodireta.harvest.dto.HarvestSeasonSummaryListResponse;
import br.com.gestaodireta.harvest.dto.HarvestSeasonSummaryResponse;
import br.com.gestaodireta.harvest.dto.HarvestSeasonUpdateRequest;
import br.com.gestaodireta.harvest.entity.HarvestSeason;
import br.com.gestaodireta.harvest.entity.ProductionActivity;
import br.com.gestaodireta.harvest.enumeration.HarvestSeasonStatus;
import br.com.gestaodireta.harvest.enumeration.ProductionActivityStatus;
import br.com.gestaodireta.harvest.repository.HarvestSeasonRepository;
import br.com.gestaodireta.harvest.repository.ProductionActivityRepository;
import br.com.gestaodireta.shared.exception.ResourceNotFoundException;
import br.com.gestaodireta.shared.pagination.PaginationParams;
import br.com.gestaodireta.shared.response.PageResponse;
import br.com.gestaodireta.support.PostgresIntegrationTest;
import br.com.gestaodireta.user.entity.User;
import br.com.gestaodireta.user.enumeration.UserStatus;
import br.com.gestaodireta.user.enumeration.UserType;
import br.com.gestaodireta.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest
class HarvestSeasonServiceTest extends PostgresIntegrationTest {

    @Autowired private HarvestSeasonService harvestSeasonService;

    @Autowired private FinancialTransactionRepository financialTransactionRepository;

    @Autowired private FinancialCategoryRepository financialCategoryRepository;

    @Autowired private HarvestSeasonRepository harvestSeasonRepository;

    @Autowired private ProductionActivityRepository productionActivityRepository;

    @Autowired private FarmUserRepository farmUserRepository;

    @Autowired private FarmRepository farmRepository;

    @Autowired private UserRepository userRepository;

    @Autowired private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        financialTransactionRepository.deleteAll();
        financialCategoryRepository.deleteAll();
        harvestSeasonRepository.deleteAll();
        productionActivityRepository.deleteAll();
        farmUserRepository.deleteAll();
        farmRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void shouldCreateHarvestSeasonWithProductionActivityFromSameFarm() {
        Farm farm = saveFarm("Farm");
        ProductionActivity activity = saveActivity(farm, "Soja");

        var response =
                harvestSeasonService.create(
                        seasonRequest(farm.getId(), activity.getId(), "Safra Soja"));

        assertThat(response.farmId()).isEqualTo(farm.getId());
        assertThat(response.productionActivityId()).isEqualTo(activity.getId());
    }

    @Test
    void shouldRejectHarvestSeasonWithProductionActivityFromAnotherFarm() {
        Farm farm = saveFarm("Farm");
        Farm otherFarm = saveFarm("Other Farm");
        ProductionActivity otherActivity = saveActivity(otherFarm, "Soja");

        assertThatThrownBy(
                        () ->
                                harvestSeasonService.create(
                                        seasonRequest(
                                                farm.getId(), otherActivity.getId(), "Safra Soja")))
                .isInstanceOf(br.com.gestaodireta.shared.exception.BusinessException.class)
                .hasMessage(
                        "Production activity must belong to the same farm as the harvest season.");
    }

    @Test
    void shouldRejectHarvestSeasonUpdateWithProductionActivityFromAnotherFarm() {
        Farm farm = saveFarm("Farm");
        Farm otherFarm = saveFarm("Other Farm");
        ProductionActivity activity = saveActivity(farm, "Soja");
        ProductionActivity otherActivity = saveActivity(otherFarm, "Milho");
        HarvestSeason season = saveSeason(farm, activity, null, null, null, "Safra Soja");

        assertThatThrownBy(
                        () ->
                                harvestSeasonService.update(
                                        season.getId(),
                                        seasonUpdateRequest(otherActivity.getId(), "Safra Milho")))
                .isInstanceOf(br.com.gestaodireta.shared.exception.BusinessException.class)
                .hasMessage(
                        "Production activity must belong to the same farm as the harvest season.");
    }

    @Test
    void shouldRejectHarvestSeasonWithInactiveProductionActivityFromSameFarm() {
        Farm farm = saveFarm("Farm");
        ProductionActivity activity = saveActivity(farm, "Soja");
        activity.setStatus(ProductionActivityStatus.INACTIVE);
        productionActivityRepository.save(activity);

        assertThatThrownBy(
                        () ->
                                harvestSeasonService.create(
                                        seasonRequest(
                                                farm.getId(), activity.getId(), "Safra Soja")))
                .isInstanceOf(br.com.gestaodireta.shared.exception.BusinessException.class)
                .hasMessage("Inactive production activity cannot be used in a harvest season.");
    }

    @Test
    void shouldReturnZeroSummaryWhenHarvestSeasonHasNoTransactions() {
        Farm farm = saveFarm("Farm");
        ProductionActivity activity = saveActivity("Soja");
        HarvestSeason season = saveSeason(farm, activity, null, null, null, "Safra Soja");

        HarvestSeasonSummaryResponse response = harvestSeasonService.getSummary(season.getId());

        assertThat(response.harvestSeasonId()).isEqualTo(season.getId());
        assertThat(response.harvestSeasonName()).isEqualTo("Safra Soja");
        assertThat(response.productionActivityId()).isEqualTo(activity.getId());
        assertThat(response.productionActivityName()).isEqualTo("Soja");
        assertThat(response.farmId()).isEqualTo(farm.getId());
        assertThat(response.farmName()).isEqualTo("Farm");
        assertThat(response.expectedCost()).isEqualByComparingTo("0.00");
        assertThat(response.expectedRevenue()).isEqualByComparingTo("0.00");
        assertThat(response.expectedProfit()).isEqualByComparingTo("0.00");
        assertThat(response.realizedCost()).isEqualByComparingTo("0.00");
        assertThat(response.realizedRevenue()).isEqualByComparingTo("0.00");
        assertThat(response.realizedProfit()).isEqualByComparingTo("0.00");
        assertThat(response.pendingExpenses()).isEqualByComparingTo("0.00");
        assertThat(response.overdueExpenses()).isEqualByComparingTo("0.00");
        assertThat(response.pendingRevenue()).isEqualByComparingTo("0.00");
        assertThat(response.transactionCount()).isZero();
        assertThat(response.incomeCount()).isZero();
        assertThat(response.expenseCount()).isZero();
        assertThat(response.costPerHectare()).isNull();
        assertThat(response.revenuePerHectare()).isNull();
        assertThat(response.profitPerHectare()).isNull();
    }

    @Test
    void shouldCalculateFinancialSummaryForHarvestSeason() {
        Farm farm = saveFarm("Farm");
        Farm otherFarm = saveFarm("Other Farm");
        ProductionActivity activity = saveActivity("Soja");
        HarvestSeason season =
                saveSeason(farm, activity, "210000.00", "96500.00", "120.00", "Safra Soja");
        HarvestSeason otherSeason =
                saveSeason(otherFarm, activity, "1.00", "1.00", "1.00", "Safra Milho");
        User user = saveUser();

        saveTransaction(
                farm, user, season, TransactionType.INCOME, PaymentStatus.PAID, "100000.00");
        saveTransaction(farm, user, season, TransactionType.INCOME, PaymentStatus.PAID, "50000.00");
        saveTransaction(
                farm, user, season, TransactionType.EXPENSE, PaymentStatus.PAID, "70000.00");
        saveTransaction(farm, user, season, TransactionType.EXPENSE, PaymentStatus.PAID, "2500.00");
        saveTransaction(
                farm, user, season, TransactionType.EXPENSE, PaymentStatus.PENDING, "18000.00");
        saveTransaction(
                farm, user, season, TransactionType.EXPENSE, PaymentStatus.OVERDUE, "6000.00");
        saveTransaction(
                farm, user, season, TransactionType.INCOME, PaymentStatus.PENDING, "25000.00");
        saveTransaction(
                farm, user, season, TransactionType.INCOME, PaymentStatus.CANCELED, "999.00");
        FinancialTransaction deleted =
                saveTransaction(
                        farm, user, season, TransactionType.EXPENSE, PaymentStatus.PAID, "999.00");
        deleted.setRecordStatus(FinancialRecordStatus.DELETED);
        financialTransactionRepository.save(deleted);
        saveTransaction(farm, user, null, TransactionType.INCOME, PaymentStatus.PAID, "999.00");
        saveTransaction(
                otherFarm, user, otherSeason, TransactionType.INCOME, PaymentStatus.PAID, "999.00");

        HarvestSeasonSummaryResponse response = harvestSeasonService.getSummary(season.getId());

        assertThat(response.expectedCost()).isEqualByComparingTo("96500.00");
        assertThat(response.expectedRevenue()).isEqualByComparingTo("210000.00");
        assertThat(response.expectedProfit()).isEqualByComparingTo("113500.00");
        assertThat(response.realizedRevenue()).isEqualByComparingTo("150000.00");
        assertThat(response.realizedCost()).isEqualByComparingTo("72500.00");
        assertThat(response.realizedProfit()).isEqualByComparingTo("77500.00");
        assertThat(response.pendingExpenses()).isEqualByComparingTo("18000.00");
        assertThat(response.overdueExpenses()).isEqualByComparingTo("6000.00");
        assertThat(response.pendingRevenue()).isEqualByComparingTo("25000.00");
        assertThat(response.transactionCount()).isEqualTo(7L);
        assertThat(response.incomeCount()).isEqualTo(3L);
        assertThat(response.expenseCount()).isEqualTo(4L);
        assertThat(response.areaHectares()).isEqualByComparingTo("120.00");
        assertThat(response.costPerHectare()).isEqualByComparingTo("604.17");
        assertThat(response.revenuePerHectare()).isEqualByComparingTo("1250.00");
        assertThat(response.profitPerHectare()).isEqualByComparingTo("645.83");
    }

    @Test
    void shouldListHarvestSeasonsWithFinancialSummary() {
        Farm farm = saveFarm("Farm");
        Farm otherFarm = saveFarm("Other Farm");
        ProductionActivity activity = saveActivity("Soja");
        HarvestSeason season =
                saveSeason(farm, activity, "210000.00", "96500.00", "120.00", "Safra Soja");
        HarvestSeason emptySeason =
                saveSeason(farm, activity, null, null, null, "Safra Sem Movimentos");
        HarvestSeason inactiveSeason =
                saveSeason(farm, activity, "1.00", "1.00", "1.00", "Safra Inativa");
        HarvestSeason otherSeason =
                saveSeason(otherFarm, activity, "1.00", "1.00", "1.00", "Safra Outra");
        inactiveSeason.setStatus(HarvestSeasonStatus.INACTIVE);
        harvestSeasonRepository.save(inactiveSeason);
        User user = saveUser();

        saveTransaction(
                farm, user, season, TransactionType.INCOME, PaymentStatus.PAID, "100000.00");
        saveTransaction(farm, user, season, TransactionType.INCOME, PaymentStatus.PAID, "50000.00");
        saveTransaction(
                farm, user, season, TransactionType.EXPENSE, PaymentStatus.PAID, "72500.00");
        saveTransaction(
                farm, user, season, TransactionType.EXPENSE, PaymentStatus.PENDING, "18000.00");
        saveTransaction(
                farm, user, season, TransactionType.EXPENSE, PaymentStatus.OVERDUE, "6000.00");
        saveTransaction(
                farm, user, season, TransactionType.INCOME, PaymentStatus.PENDING, "25000.00");
        saveTransaction(
                farm, user, season, TransactionType.INCOME, PaymentStatus.CANCELED, "999.00");
        FinancialTransaction deleted =
                saveTransaction(
                        farm, user, season, TransactionType.EXPENSE, PaymentStatus.PAID, "999.00");
        deleted.setRecordStatus(FinancialRecordStatus.DELETED);
        financialTransactionRepository.save(deleted);
        saveTransaction(farm, user, null, TransactionType.INCOME, PaymentStatus.PAID, "999.00");
        saveTransaction(
                otherFarm, user, otherSeason, TransactionType.INCOME, PaymentStatus.PAID, "999.00");

        PageResponse<HarvestSeasonSummaryListResponse> response =
                harvestSeasonService.findSummaryList(
                        farm.getId(), null, null, null, null, null, null, pagination("id", 20));

        assertThat(response.totalElements()).isEqualTo(2);
        assertThat(response.content())
                .extracting(HarvestSeasonSummaryListResponse::id)
                .containsExactly(season.getId(), emptySeason.getId());

        HarvestSeasonSummaryListResponse summary = response.content().getFirst();
        assertThat(summary.expectedCost()).isEqualByComparingTo("96500.00");
        assertThat(summary.expectedRevenue()).isEqualByComparingTo("210000.00");
        assertThat(summary.expectedProfit()).isEqualByComparingTo("113500.00");
        assertThat(summary.realizedRevenue()).isEqualByComparingTo("150000.00");
        assertThat(summary.realizedCost()).isEqualByComparingTo("72500.00");
        assertThat(summary.realizedProfit()).isEqualByComparingTo("77500.00");
        assertThat(summary.pendingExpenses()).isEqualByComparingTo("18000.00");
        assertThat(summary.overdueExpenses()).isEqualByComparingTo("6000.00");
        assertThat(summary.pendingRevenue()).isEqualByComparingTo("25000.00");
        assertThat(summary.transactionCount()).isEqualTo(6L);
        assertThat(summary.incomeCount()).isEqualTo(3L);
        assertThat(summary.expenseCount()).isEqualTo(3L);

        HarvestSeasonSummaryListResponse emptySummary = response.content().get(1);
        assertThat(emptySummary.expectedCost()).isEqualByComparingTo("0.00");
        assertThat(emptySummary.expectedRevenue()).isEqualByComparingTo("0.00");
        assertThat(emptySummary.expectedProfit()).isEqualByComparingTo("0.00");
        assertThat(emptySummary.realizedCost()).isEqualByComparingTo("0.00");
        assertThat(emptySummary.realizedRevenue()).isEqualByComparingTo("0.00");
        assertThat(emptySummary.realizedProfit()).isEqualByComparingTo("0.00");
        assertThat(emptySummary.transactionCount()).isZero();
        assertThat(emptySummary.incomeCount()).isZero();
        assertThat(emptySummary.expenseCount()).isZero();
    }

    @Test
    void shouldFilterSummaryListByStatusSearchAndRespectPagination() {
        Farm farm = saveFarm("Farm");
        ProductionActivity soy = saveActivity("Soja");
        ProductionActivity corn = saveActivity("Milho");
        HarvestSeason first = saveSeason(farm, soy, "10.00", "4.00", null, "Safra Alpha");
        HarvestSeason second = saveSeason(farm, corn, "20.00", "5.00", null, "Safra Beta");
        HarvestSeason inactive = saveSeason(farm, soy, "30.00", "6.00", null, "Safra Inativa");
        first.setDescription("Primeiro ciclo agricola");
        inactive.setStatus(HarvestSeasonStatus.INACTIVE);
        harvestSeasonRepository.save(first);
        harvestSeasonRepository.save(inactive);

        PageResponse<HarvestSeasonSummaryListResponse> activitySearch =
                harvestSeasonService.findSummaryList(
                        farm.getId(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        "  milho ",
                        pagination("id", 10));
        PageResponse<HarvestSeasonSummaryListResponse> descriptionSearch =
                harvestSeasonService.findSummaryList(
                        farm.getId(), null, null, null, null, null, "CICLO", pagination("id", 10));
        PageResponse<HarvestSeasonSummaryListResponse> inactiveOnly =
                harvestSeasonService.findSummaryList(
                        farm.getId(),
                        List.of(HarvestSeasonStatus.INACTIVE),
                        null,
                        null,
                        null,
                        null,
                        null,
                        pagination("id", 10));
        PageResponse<HarvestSeasonSummaryListResponse> paged =
                harvestSeasonService.findSummaryList(
                        farm.getId(), null, null, null, null, null, null, pagination("id", 1));

        assertThat(activitySearch.content())
                .extracting(HarvestSeasonSummaryListResponse::id)
                .containsExactly(second.getId());
        assertThat(descriptionSearch.content())
                .extracting(HarvestSeasonSummaryListResponse::id)
                .containsExactly(first.getId());
        assertThat(inactiveOnly.content())
                .extracting(HarvestSeasonSummaryListResponse::id)
                .containsExactly(inactive.getId());
        assertThat(paged.content())
                .extracting(HarvestSeasonSummaryListResponse::id)
                .containsExactly(first.getId());
        assertThat(paged.totalElements()).isEqualTo(2);
        assertThat(paged.totalPages()).isEqualTo(2);
    }

    @Test
    void shouldReturnPerHectareIndicatorsNullWhenAreaIsNullOrZero() {
        Farm farm = saveFarm("Farm");
        ProductionActivity activity = saveActivity("Soja");
        User user = saveUser();
        HarvestSeason seasonWithoutArea =
                saveSeason(farm, activity, "100.00", "50.00", null, "Safra Sem Area");
        HarvestSeason seasonWithZeroArea =
                saveSeason(farm, activity, "100.00", "50.00", "0.00", "Safra Zero");
        saveTransaction(
                farm,
                user,
                seasonWithoutArea,
                TransactionType.INCOME,
                PaymentStatus.PAID,
                "100.00");
        saveTransaction(
                farm,
                user,
                seasonWithZeroArea,
                TransactionType.INCOME,
                PaymentStatus.PAID,
                "100.00");

        HarvestSeasonSummaryResponse withoutArea =
                harvestSeasonService.getSummary(seasonWithoutArea.getId());
        HarvestSeasonSummaryResponse withZeroArea =
                harvestSeasonService.getSummary(seasonWithZeroArea.getId());

        assertThat(withoutArea.costPerHectare()).isNull();
        assertThat(withoutArea.revenuePerHectare()).isNull();
        assertThat(withoutArea.profitPerHectare()).isNull();
        assertThat(withZeroArea.costPerHectare()).isNull();
        assertThat(withZeroArea.revenuePerHectare()).isNull();
        assertThat(withZeroArea.profitPerHectare()).isNull();
    }

    @Test
    void shouldThrowNotFoundWhenHarvestSeasonDoesNotExist() {
        assertThatThrownBy(() -> harvestSeasonService.getSummary(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Harvest season not found");
    }

    @Test
    void shouldNormalizeStatusesAndRemoveDuplicates() {
        assertThat(
                        harvestSeasonService.resolveStatuses(
                                List.of(
                                        HarvestSeasonStatus.PLANNED,
                                        HarvestSeasonStatus.PLANNED,
                                        HarvestSeasonStatus.IN_PROGRESS)))
                .containsExactly(HarvestSeasonStatus.PLANNED, HarvestSeasonStatus.IN_PROGRESS);
        assertThat(harvestSeasonService.resolveStatuses(null)).isEmpty();
    }

    @Test
    void shouldResolvePluralProductionActivityIdsWithPriorityOverSingleId() {
        List<Long> resolvedIds =
                harvestSeasonService.resolveProductionActivityIds(1L, List.of(2L, 3L));

        assertThat(resolvedIds).containsExactly(2L, 3L);
    }

    @Test
    void shouldResolveSingleProductionActivityIdWhenPluralIdsAreEmpty() {
        assertThat(harvestSeasonService.resolveProductionActivityIds(1L, null)).containsExactly(1L);
        assertThat(harvestSeasonService.resolveProductionActivityIds(2L, List.of()))
                .containsExactly(2L);
    }

    @Test
    void shouldResolveEmptyProductionActivityIdsWhenNoActivityFilterIsProvided() {
        assertThat(harvestSeasonService.resolveProductionActivityIds(null, null)).isEmpty();
        assertThat(harvestSeasonService.resolveProductionActivityIds(null, List.of())).isEmpty();
    }

    @Test
    void shouldRejectInvalidPeriodFilter() {
        Farm farm = saveFarm("Farm");

        assertThatThrownBy(
                        () ->
                                harvestSeasonService.findAll(
                                        farm.getId(),
                                        null,
                                        null,
                                        null,
                                        LocalDate.of(2026, 2, 1),
                                        LocalDate.of(2026, 1, 31),
                                        false,
                                        pagination("id", 10)))
                .isInstanceOf(br.com.gestaodireta.shared.exception.BusinessException.class)
                .hasMessage("A data inicial do período não pode ser posterior à data final.");
    }

    @Test
    void shouldNotRestrictHarvestSeasonListWhenActivityAndPeriodFiltersAreEmpty() {
        Farm farm = saveFarm("Farm");
        ProductionActivity soy = saveActivity("Soja");
        ProductionActivity corn = saveActivity("Milho");
        HarvestSeason first = saveSeason(farm, soy, null, null, null, "Safra Soja");
        HarvestSeason second = saveSeason(farm, corn, null, null, null, "Safra Milho");

        PageResponse<?> response =
                harvestSeasonService.findAll(
                        farm.getId(),
                        null,
                        null,
                        List.of(),
                        null,
                        null,
                        false,
                        pagination("id", 10));

        assertThat(response.content())
                .extracting("id")
                .containsExactly(first.getId(), second.getId());
    }

    @Test
    void shouldFilterSummaryListByProductionActivitiesAndIntersectingPeriod() {
        Farm farm = saveFarm("Farm");
        ProductionActivity soy = saveActivity("Soja");
        ProductionActivity corn = saveActivity("Milho");
        HarvestSeason soySeason = saveSeason(farm, soy, null, null, null, "Safra Soja");
        HarvestSeason cornSeason = saveSeason(farm, corn, null, null, null, "Safra Milho");
        HarvestSeason openEndedSeason = saveSeason(farm, soy, null, null, null, "Safra Permanente");
        soySeason.setStartDate(LocalDate.of(2026, 1, 1));
        soySeason.setEndDate(LocalDate.of(2026, 3, 31));
        cornSeason.setStartDate(LocalDate.of(2026, 4, 1));
        cornSeason.setEndDate(LocalDate.of(2026, 6, 30));
        openEndedSeason.setStartDate(LocalDate.of(2025, 11, 1));
        openEndedSeason.setEndDate(null);
        harvestSeasonRepository.saveAll(List.of(soySeason, cornSeason, openEndedSeason));

        PageResponse<HarvestSeasonSummaryListResponse> response =
                harvestSeasonService.findSummaryList(
                        farm.getId(),
                        null,
                        null,
                        List.of(soy.getId()),
                        LocalDate.of(2026, 2, 1),
                        LocalDate.of(2026, 4, 30),
                        null,
                        pagination("id", 10));

        assertThat(response.content())
                .extracting(HarvestSeasonSummaryListResponse::id)
                .containsExactly(soySeason.getId(), openEndedSeason.getId());
    }

    @Test
    void shouldCalculateFinancialSummaryForFilteredHarvestSeasons() {
        Farm farm = saveFarm("Farm");
        ProductionActivity activity = saveActivity(farm, "Soy");
        HarvestSeason first = saveSeason(farm, activity, "180000.00", "100000.00", null, "Safra A");
        HarvestSeason second = saveSeason(farm, activity, "90000.00", "50000.00", null, "Safra B");
        first.setStatus(HarvestSeasonStatus.IN_PROGRESS);
        harvestSeasonRepository.save(first);
        User user = saveUser();

        saveTransaction(farm, user, first, TransactionType.EXPENSE, PaymentStatus.PAID, "40000.00");
        saveTransaction(farm, user, first, TransactionType.INCOME, PaymentStatus.PAID, "50000.00");
        saveTransaction(
                farm, user, first, TransactionType.EXPENSE, PaymentStatus.PENDING, "20000.00");
        saveTransaction(
                farm, user, first, TransactionType.INCOME, PaymentStatus.PENDING, "70000.00");
        saveTransaction(
                farm, user, second, TransactionType.EXPENSE, PaymentStatus.PAID, "10000.00");
        saveTransaction(
                farm, user, second, TransactionType.EXPENSE, PaymentStatus.OVERDUE, "5000.00");
        saveTransaction(
                farm, user, second, TransactionType.INCOME, PaymentStatus.PENDING, "30000.00");

        HarvestSeasonFinancialSummaryResponse response =
                harvestSeasonService.getFinancialSummary(
                        farm.getId(), null, null, null, null, null, null);

        assertThat(response.activeHarvestCount()).isEqualTo(2L);
        assertThat(response.planning().plannedCost()).isEqualByComparingTo("150000.00");
        assertThat(response.planning().plannedRevenue()).isEqualByComparingTo("270000.00");
        assertThat(response.planning().plannedProfit()).isEqualByComparingTo("120000.00");
        assertThat(response.realized().realizedCost()).isEqualByComparingTo("50000.00");
        assertThat(response.realized().realizedRevenue()).isEqualByComparingTo("50000.00");
        assertThat(response.projection().projectedCost()).isEqualByComparingTo("75000.00");
        assertThat(response.projection().projectedRevenue()).isEqualByComparingTo("150000.00");
        assertThat(response.projection().projectedProfit()).isEqualByComparingTo("75000.00");
        assertThat(response.comparison().profitPerformancePercentage())
                .isEqualByComparingTo("0.00");
        assertThat(response.comparison().costVarianceAmount()).isEqualByComparingTo("-100000.00");
        assertThat(response.comparison().costVariancePercentage()).isEqualByComparingTo("-66.67");
        assertThat(response.comparison().profitPerformanceStatus().name())
                .isEqualTo("BELOW_PLANNED");
        assertThat(response.comparison().costVarianceStatus().name()).isEqualTo("BELOW_PLANNED");
    }

    private Farm saveFarm(String name) {
        Farm farm = new Farm();
        farm.setName(name);
        farm.setStatus(FarmStatus.ACTIVE);

        return farmRepository.save(farm);
    }

    private ProductionActivity saveActivity(String name) {
        Farm farm = farmRepository.findAll(Sort.by("id")).getFirst();

        return saveActivity(farm, name);
    }

    private ProductionActivity saveActivity(Farm farm, String name) {
        ProductionActivity activity = new ProductionActivity();
        activity.setFarm(farm);
        activity.setName(name);
        activity.setStatus(ProductionActivityStatus.ACTIVE);

        return productionActivityRepository.save(activity);
    }

    private HarvestSeason saveSeason(
            Farm farm,
            ProductionActivity activity,
            String expectedRevenue,
            String expectedCost,
            String areaHectares,
            String name) {
        HarvestSeason season = new HarvestSeason();
        season.setFarm(farm);
        season.setProductionActivity(activity);
        season.setName(name);
        season.setStartDate(LocalDate.of(2026, 1, 1));
        season.setExpectedRevenue(toBigDecimal(expectedRevenue));
        season.setExpectedCost(toBigDecimal(expectedCost));
        season.setAreaHectares(toBigDecimal(areaHectares));
        season.setStatus(HarvestSeasonStatus.PLANNED);

        return harvestSeasonRepository.save(season);
    }

    private HarvestSeasonRequest seasonRequest(Long farmId, Long activityId, String name) {
        return new HarvestSeasonRequest(
                farmId,
                activityId,
                name,
                "Season description",
                LocalDate.of(2026, 1, 1),
                null,
                new BigDecimal("1000.00"),
                new BigDecimal("500.00"),
                BigDecimal.ZERO);
    }

    private HarvestSeasonUpdateRequest seasonUpdateRequest(Long activityId, String name) {
        return new HarvestSeasonUpdateRequest(
                activityId,
                name,
                "Updated description",
                LocalDate.of(2026, 1, 1),
                null,
                new BigDecimal("1000.00"),
                new BigDecimal("500.00"),
                BigDecimal.ZERO);
    }

    private User saveUser() {
        User user = new User();
        user.setName("User");
        user.setEmail("user@example.com");
        user.setPassword(passwordEncoder.encode("Strong1!"));
        user.setUserType(UserType.USER);
        user.setStatus(UserStatus.ACTIVE);

        return userRepository.save(user);
    }

    private FinancialTransaction saveTransaction(
            Farm farm,
            User user,
            HarvestSeason harvestSeason,
            TransactionType type,
            PaymentStatus status,
            String amount) {
        FinancialTransaction transaction = new FinancialTransaction();
        transaction.setDescription("Transaction");
        transaction.setAmount(new BigDecimal(amount));
        transaction.setType(type);
        transaction.setStatus(status);
        transaction.setTransactionDate(LocalDate.now());
        transaction.setDueDate(LocalDate.now().plusDays(5));
        transaction.setFarm(farm);
        transaction.setHarvestSeason(harvestSeason);
        transaction.setCreatedByUser(user);
        transaction.setRecordStatus(FinancialRecordStatus.ACTIVE);

        return financialTransactionRepository.save(transaction);
    }

    private PaginationParams pagination(String sort, int size) {
        PaginationParams paginationParams = new PaginationParams();
        paginationParams.setSort(sort);
        paginationParams.setSize(size);

        return paginationParams;
    }

    private BigDecimal toBigDecimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }
}
