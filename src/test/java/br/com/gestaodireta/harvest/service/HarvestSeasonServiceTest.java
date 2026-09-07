package br.com.gestaodireta.harvest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.gestaodireta.farm.entity.Farm;
import br.com.gestaodireta.farm.enumeration.FarmStatus;
import br.com.gestaodireta.farm.repository.FarmRepository;
import br.com.gestaodireta.farm.repository.FarmUserRepository;
import br.com.gestaodireta.financial.entity.FinancialCategory;
import br.com.gestaodireta.financial.entity.FinancialTransaction;
import br.com.gestaodireta.financial.enumeration.FinancialCategoryStatus;
import br.com.gestaodireta.financial.enumeration.FinancialRecordStatus;
import br.com.gestaodireta.financial.enumeration.PaymentStatus;
import br.com.gestaodireta.financial.enumeration.TransactionType;
import br.com.gestaodireta.financial.repository.FinancialCategoryRepository;
import br.com.gestaodireta.financial.repository.FinancialTransactionRepository;
import br.com.gestaodireta.harvest.dto.HarvestCategoryComparisonResponse;
import br.com.gestaodireta.harvest.dto.HarvestCategoryMovementsResponse;
import br.com.gestaodireta.harvest.dto.HarvestSeasonBudgetItemRequest;
import br.com.gestaodireta.harvest.dto.HarvestSeasonDetailSummaryResponse;
import br.com.gestaodireta.harvest.dto.HarvestSeasonFinancialSummaryResponse;
import br.com.gestaodireta.harvest.dto.HarvestSeasonRequest;
import br.com.gestaodireta.harvest.dto.HarvestSeasonStatusUpdateRequest;
import br.com.gestaodireta.harvest.dto.HarvestSeasonSummaryListResponse;
import br.com.gestaodireta.harvest.dto.HarvestSeasonUpdateRequest;
import br.com.gestaodireta.harvest.entity.HarvestSeason;
import br.com.gestaodireta.harvest.entity.HarvestSeasonBudgetItem;
import br.com.gestaodireta.harvest.entity.ProductionActivity;
import br.com.gestaodireta.harvest.enumeration.ComparisonSemantic;
import br.com.gestaodireta.harvest.enumeration.HarvestCategoryComparisonStatus;
import br.com.gestaodireta.harvest.enumeration.HarvestSeasonComparisonMetric;
import br.com.gestaodireta.harvest.enumeration.HarvestSeasonStatus;
import br.com.gestaodireta.harvest.enumeration.ProductionActivityStatus;
import br.com.gestaodireta.harvest.repository.HarvestSeasonBudgetItemRepository;
import br.com.gestaodireta.harvest.repository.HarvestSeasonRepository;
import br.com.gestaodireta.harvest.repository.ProductionActivityRepository;
import br.com.gestaodireta.shared.exception.BusinessException;
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

    @Autowired private HarvestSeasonBudgetItemService harvestSeasonBudgetItemService;

    @Autowired private FinancialTransactionRepository financialTransactionRepository;

    @Autowired private FinancialCategoryRepository financialCategoryRepository;

    @Autowired private HarvestSeasonRepository harvestSeasonRepository;

    @Autowired private HarvestSeasonBudgetItemRepository harvestSeasonBudgetItemRepository;

    @Autowired private ProductionActivityRepository productionActivityRepository;

    @Autowired private FarmUserRepository farmUserRepository;

    @Autowired private FarmRepository farmRepository;

    @Autowired private UserRepository userRepository;

    @Autowired private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        financialTransactionRepository.deleteAll();
        harvestSeasonBudgetItemRepository.deleteAll();
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
    void shouldCreateMultipleBudgetItemsForTheSameCategoryAndCalculateTotals() {
        Farm farm = saveFarm("Farm");
        ProductionActivity activity = saveActivity(farm, "Soja");
        HarvestSeason season = saveSeason(farm, activity, null, null, "10", "Safra");
        FinancialCategory expenseCategory =
                saveCategory(farm, "Fertilizantes", TransactionType.EXPENSE);
        FinancialCategory incomeCategory = saveCategory(farm, "Venda", TransactionType.INCOME);

        harvestSeasonBudgetItemService.create(
                season.getId(),
                budgetItemRequest(
                        expenseCategory.getId(), TransactionType.EXPENSE, "Plantio", "20000"));
        harvestSeasonBudgetItemService.create(
                season.getId(),
                budgetItemRequest(
                        expenseCategory.getId(), TransactionType.EXPENSE, "Cobertura", "12000"));
        harvestSeasonBudgetItemService.create(
                season.getId(),
                budgetItemRequest(
                        incomeCategory.getId(), TransactionType.INCOME, "Venda estimada", "80000"));

        var budget = harvestSeasonBudgetItemService.findAll(season.getId());

        assertThat(budget.expenses()).hasSize(1);
        assertThat(budget.expenses().getFirst().itemCount()).isEqualTo(2);
        assertThat(budget.plannedExpense()).isEqualByComparingTo("32000");
        assertThat(budget.plannedRevenue()).isEqualByComparingTo("80000");
        assertThat(budget.plannedResult()).isEqualByComparingTo("48000");
        assertThat(budget.plannedMargin()).isEqualByComparingTo("60.00");
    }

    @Test
    void shouldRejectBudgetItemCategoryFromAnotherFarmAndFinishedSeasonChanges() {
        Farm farm = saveFarm("Farm");
        Farm otherFarm = saveFarm("Other Farm");
        ProductionActivity activity = saveActivity(farm, "Soja");
        HarvestSeason season = saveSeason(farm, activity, null, null, "10", "Safra");
        FinancialCategory otherCategory =
                saveCategory(otherFarm, "Diesel", TransactionType.EXPENSE);

        assertThatThrownBy(
                        () ->
                                harvestSeasonBudgetItemService.create(
                                        season.getId(),
                                        budgetItemRequest(
                                                otherCategory.getId(),
                                                TransactionType.EXPENSE,
                                                "Diesel",
                                                "100")))
                .isInstanceOf(BusinessException.class);

        season.setStatus(HarvestSeasonStatus.FINISHED);
        harvestSeasonRepository.save(season);
        FinancialCategory category = saveCategory(farm, "Insumos", TransactionType.EXPENSE);

        assertThatThrownBy(
                        () ->
                                harvestSeasonBudgetItemService.create(
                                        season.getId(),
                                        budgetItemRequest(
                                                category.getId(),
                                                TransactionType.EXPENSE,
                                                "Insumos",
                                                "100")))
                .isInstanceOf(BusinessException.class);
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
    void shouldCreateHarvestSeasonAsPlanned() {
        Farm farm = saveFarm("Farm");
        ProductionActivity activity = saveActivity(farm, "Soja");

        var response =
                harvestSeasonService.create(
                        seasonRequest(farm.getId(), activity.getId(), "Safra Soja"));

        assertThat(response.status()).isEqualTo(HarvestSeasonStatus.PLANNED);
    }

    @Test
    void shouldApplyOnlyAllowedHarvestSeasonStatusTransitions() {
        Farm farm = saveFarm("Farm");
        ProductionActivity activity = saveActivity(farm, "Soja");
        HarvestSeason season = saveSeason(farm, activity, null, null, null, "Safra Soja");

        assertThat(
                        harvestSeasonService
                                .updateStatus(
                                        season.getId(),
                                        new HarvestSeasonStatusUpdateRequest(
                                                HarvestSeasonStatus.IN_PROGRESS))
                                .status())
                .isEqualTo(HarvestSeasonStatus.IN_PROGRESS);
        assertThat(
                        harvestSeasonService
                                .updateStatus(
                                        season.getId(),
                                        new HarvestSeasonStatusUpdateRequest(
                                                HarvestSeasonStatus.FINISHED))
                                .status())
                .isEqualTo(HarvestSeasonStatus.FINISHED);
        assertThat(
                        harvestSeasonService
                                .updateStatus(
                                        season.getId(),
                                        new HarvestSeasonStatusUpdateRequest(
                                                HarvestSeasonStatus.IN_PROGRESS))
                                .status())
                .isEqualTo(HarvestSeasonStatus.IN_PROGRESS);

        harvestSeasonService.inactivate(season.getId());

        assertThat(harvestSeasonService.findById(season.getId()).status())
                .isEqualTo(HarvestSeasonStatus.INACTIVE);
        assertThat(harvestSeasonService.activate(season.getId()).status())
                .isEqualTo(HarvestSeasonStatus.PLANNED);
    }

    @Test
    void shouldRejectInvalidHarvestSeasonStatusTransitions() {
        Farm farm = saveFarm("Farm");
        ProductionActivity activity = saveActivity(farm, "Soja");
        HarvestSeason season = saveSeason(farm, activity, null, null, null, "Safra Soja");

        assertThatThrownBy(
                        () ->
                                harvestSeasonService.updateStatus(
                                        season.getId(),
                                        new HarvestSeasonStatusUpdateRequest(
                                                HarvestSeasonStatus.FINISHED)))
                .isInstanceOf(br.com.gestaodireta.shared.exception.BusinessException.class)
                .hasMessageContaining("Invalid harvest season status transition");

        harvestSeasonService.inactivate(season.getId());

        assertThatThrownBy(
                        () ->
                                harvestSeasonService.updateStatus(
                                        season.getId(),
                                        new HarvestSeasonStatusUpdateRequest(
                                                HarvestSeasonStatus.IN_PROGRESS)))
                .isInstanceOf(br.com.gestaodireta.shared.exception.BusinessException.class)
                .hasMessageContaining("Invalid harvest season status transition");

        assertThatThrownBy(
                        () ->
                                harvestSeasonService.updateStatus(
                                        season.getId(),
                                        new HarvestSeasonStatusUpdateRequest(
                                                HarvestSeasonStatus.FINISHED)))
                .isInstanceOf(br.com.gestaodireta.shared.exception.BusinessException.class)
                .hasMessageContaining("Invalid harvest season status transition");
        harvestSeasonService.activate(season.getId());

        HarvestSeason plannedSeason =
                harvestSeasonRepository.findById(season.getId()).orElseThrow();
        plannedSeason.setStatus(HarvestSeasonStatus.PLANNED);
        harvestSeasonRepository.saveAndFlush(plannedSeason);

        assertThatThrownBy(() -> harvestSeasonService.activate(season.getId()))
                .isInstanceOf(br.com.gestaodireta.shared.exception.BusinessException.class)
                .hasMessage("Only inactive harvest seasons can be activated.");
    }

    @Test
    void shouldRejectHarvestSeasonNameAlreadyUsedByInactiveSeason() {
        Farm farm = saveFarm("Farm");
        ProductionActivity activity = saveActivity(farm, "Soja");
        HarvestSeason inactive = saveSeason(farm, activity, null, null, null, "Safra Soja");
        inactive.setStatus(HarvestSeasonStatus.INACTIVE);
        harvestSeasonRepository.save(inactive);

        assertThatThrownBy(
                        () ->
                                harvestSeasonService.create(
                                        seasonRequest(
                                                farm.getId(), activity.getId(), " safra soja ")))
                .isInstanceOf(br.com.gestaodireta.shared.exception.BusinessException.class)
                .hasMessage("A harvest season with this name already exists for this farm.");
    }

    @Test
    void shouldRejectHarvestSeasonUpdateToAnotherSeasonNameAndAllowOwnName() {
        Farm farm = saveFarm("Farm");
        ProductionActivity activity = saveActivity(farm, "Soja");
        HarvestSeason soy = saveSeason(farm, activity, null, null, null, "Safra Soja");
        HarvestSeason corn = saveSeason(farm, activity, null, null, null, "Safra Milho");

        assertThatThrownBy(
                        () ->
                                harvestSeasonService.update(
                                        corn.getId(),
                                        seasonUpdateRequest(activity.getId(), "Safra Soja")))
                .isInstanceOf(br.com.gestaodireta.shared.exception.BusinessException.class)
                .hasMessage("A harvest season with this name already exists for this farm.");
        assertThat(
                        harvestSeasonService
                                .update(
                                        soy.getId(),
                                        seasonUpdateRequest(activity.getId(), " safra soja "))
                                .name())
                .isEqualTo("safra soja");
    }

    @Test
    void shouldReturnZeroSummaryWhenHarvestSeasonHasNoTransactions() {
        Farm farm = saveFarm("Farm");
        ProductionActivity activity = saveActivity(farm, "Soja");
        HarvestSeason season = saveSeason(farm, activity, null, null, null, "Safra Soja");

        HarvestSeasonDetailSummaryResponse response =
                harvestSeasonService.getSummary(season.getId());

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
        assertThat(response.planningComparison().state())
                .isEqualTo(
                        br.com.gestaodireta.harvest.enumeration.PlanningComparisonState
                                .MISSING_PLANNING);
    }

    @Test
    void shouldCalculateFinancialSummaryForHarvestSeason() {
        Farm farm = saveFarm("Farm");
        Farm otherFarm = saveFarm("Other Farm");
        ProductionActivity activity = saveActivity(farm, "Soja");
        ProductionActivity otherActivity = saveActivity(otherFarm, "Soja");
        HarvestSeason season =
                saveSeason(farm, activity, "210000.00", "96500.00", "120.00", "Safra Soja");
        HarvestSeason otherSeason =
                saveSeason(otherFarm, otherActivity, "1.00", "1.00", "1.00", "Safra Milho");
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

        HarvestSeasonDetailSummaryResponse response =
                harvestSeasonService.getSummary(season.getId());

        assertThat(response.expectedCost()).isEqualByComparingTo("96500.00");
        assertThat(response.expectedRevenue()).isEqualByComparingTo("210000.00");
        assertThat(response.expectedProfit()).isEqualByComparingTo("113500.00");
        assertThat(response.planning().plannedMargin()).isEqualByComparingTo("54.05");
        assertThat(response.realizedRevenue()).isEqualByComparingTo("150000.00");
        assertThat(response.realizedCost()).isEqualByComparingTo("72500.00");
        assertThat(response.realizedProfit()).isEqualByComparingTo("77500.00");
        assertThat(response.realized().realizedMargin()).isEqualByComparingTo("51.67");
        assertThat(response.pendingExpenses()).isEqualByComparingTo("18000.00");
        assertThat(response.overdueExpenses()).isEqualByComparingTo("6000.00");
        assertThat(response.pendingRevenue()).isEqualByComparingTo("25000.00");
        assertThat(response.openAmounts().payableAmount()).isEqualByComparingTo("24000.00");
        assertThat(response.openAmounts().receivableAmount()).isEqualByComparingTo("25000.00");
        assertThat(response.projection().projectedCost()).isEqualByComparingTo("96500.00");
        assertThat(response.projection().projectedRevenue()).isEqualByComparingTo("175000.00");
        assertThat(response.projection().projectedProfit()).isEqualByComparingTo("78500.00");
        assertThat(response.transactionCount()).isEqualTo(7L);
        assertThat(response.incomeCount()).isEqualTo(3L);
        assertThat(response.expenseCount()).isEqualTo(4L);
        assertThat(response.areaHectares()).isEqualByComparingTo("120.00");
        assertThat(response.costPerHectare()).isEqualByComparingTo("604.17");
        assertThat(response.revenuePerHectare()).isEqualByComparingTo("1250.00");
        assertThat(response.profitPerHectare()).isEqualByComparingTo("645.83");
        assertThat(response.plannedCostPerHectare()).isEqualByComparingTo("804.17");
        assertThat(response.plannedRevenuePerHectare()).isEqualByComparingTo("1750.00");
        assertThat(response.plannedResultPerHectare()).isEqualByComparingTo("945.83");
        assertThat(response.projectedCostPerHectare()).isEqualByComparingTo("804.17");
        assertThat(response.projectedRevenuePerHectare()).isEqualByComparingTo("1458.33");
        assertThat(response.projectedProfitPerHectare()).isEqualByComparingTo("654.17");
        assertThat(response.realizedCostPerHectare()).isEqualByComparingTo("604.17");
        assertThat(response.realizedRevenuePerHectare()).isEqualByComparingTo("1250.00");
        assertThat(response.realizedProfitPerHectare()).isEqualByComparingTo("645.83");
        assertThat(response.planningComparison().state())
                .isEqualTo(br.com.gestaodireta.harvest.enumeration.PlanningComparisonState.PLANNED);
    }

    @Test
    void shouldReturnMissingCurrentDataWhenActiveHarvestSeasonHasPlanningOnly() {
        Farm farm = saveFarm("Farm");
        ProductionActivity activity = saveActivity(farm, "Soja");
        HarvestSeason season = saveSeason(farm, activity, "200.00", "100.00", null, "Safra Soja");
        season.setStatus(HarvestSeasonStatus.IN_PROGRESS);
        harvestSeasonRepository.save(season);

        HarvestSeasonDetailSummaryResponse response =
                harvestSeasonService.getSummary(season.getId());

        assertThat(response.planningComparison().state())
                .isEqualTo(
                        br.com.gestaodireta.harvest.enumeration.PlanningComparisonState
                                .MISSING_CURRENT_DATA);
        assertThat(response.planningComparison().basis()).isNull();
        assertThat(response.planningComparison().cost()).isNull();
    }

    @Test
    void shouldListHarvestSeasonsWithFinancialSummary() {
        Farm farm = saveFarm("Farm");
        Farm otherFarm = saveFarm("Other Farm");
        ProductionActivity activity = saveActivity(farm, "Soja");
        ProductionActivity otherActivity = saveActivity(otherFarm, "Soja");
        HarvestSeason season =
                saveSeason(farm, activity, "210000.00", "96500.00", "120.00", "Safra Soja");
        HarvestSeason emptySeason =
                saveSeason(farm, activity, null, null, null, "Safra Sem Movimentos");
        HarvestSeason inactiveSeason =
                saveSeason(farm, activity, "1.00", "1.00", "1.00", "Safra Inativa");
        HarvestSeason otherSeason =
                saveSeason(otherFarm, otherActivity, "1.00", "1.00", "1.00", "Safra Outra");
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
        HarvestSeason seasonWithNegativeArea =
                saveSeason(farm, activity, "100.00", "50.00", "-10.00", "Safra Area Negativa");
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
        saveTransaction(
                farm,
                user,
                seasonWithNegativeArea,
                TransactionType.INCOME,
                PaymentStatus.PAID,
                "100.00");

        HarvestSeasonDetailSummaryResponse withoutArea =
                harvestSeasonService.getSummary(seasonWithoutArea.getId());
        HarvestSeasonDetailSummaryResponse withZeroArea =
                harvestSeasonService.getSummary(seasonWithZeroArea.getId());
        HarvestSeasonDetailSummaryResponse withNegativeArea =
                harvestSeasonService.getSummary(seasonWithNegativeArea.getId());

        assertThat(withoutArea.costPerHectare()).isNull();
        assertThat(withoutArea.revenuePerHectare()).isNull();
        assertThat(withoutArea.profitPerHectare()).isNull();
        assertThat(withZeroArea.costPerHectare()).isNull();
        assertThat(withZeroArea.revenuePerHectare()).isNull();
        assertThat(withZeroArea.profitPerHectare()).isNull();
        assertThat(withNegativeArea.plannedCostPerHectare()).isNull();
        assertThat(withNegativeArea.plannedRevenuePerHectare()).isNull();
        assertThat(withNegativeArea.plannedResultPerHectare()).isNull();
        assertThat(withNegativeArea.projectedCostPerHectare()).isNull();
        assertThat(withNegativeArea.projectedRevenuePerHectare()).isNull();
        assertThat(withNegativeArea.projectedProfitPerHectare()).isNull();
        assertThat(withNegativeArea.realizedCostPerHectare()).isNull();
        assertThat(withNegativeArea.realizedRevenuePerHectare()).isNull();
        assertThat(withNegativeArea.realizedProfitPerHectare()).isNull();
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
        assertThat(response.planning().plannedMargin()).isEqualByComparingTo("44.44");
        assertThat(response.realized().realizedCost()).isEqualByComparingTo("50000.00");
        assertThat(response.realized().realizedRevenue()).isEqualByComparingTo("50000.00");
        assertThat(response.projection().projectedCost()).isEqualByComparingTo("75000.00");
        assertThat(response.projection().projectedRevenue()).isEqualByComparingTo("150000.00");
        assertThat(response.projection().projectedProfit()).isEqualByComparingTo("75000.00");
        assertThat(response.projection().projectedMargin()).isEqualByComparingTo("50.00");
        assertThat(response.openAmounts().payableAmount()).isEqualByComparingTo("25000.00");
        assertThat(response.openAmounts().receivableAmount()).isEqualByComparingTo("100000.00");
        assertThat(response.comparison().profitPerformancePercentage())
                .isEqualByComparingTo("-37.50");
        assertThat(response.comparison().costVarianceAmount()).isEqualByComparingTo("-75000.00");
        assertThat(response.comparison().costVariancePercentage()).isEqualByComparingTo("-50.00");
        assertThat(response.comparison().profitPerformanceStatus().name())
                .isEqualTo("BELOW_PLANNED");
        assertThat(response.comparison().costVarianceStatus().name()).isEqualTo("BELOW_PLANNED");
    }

    @Test
    void shouldBreakDownRealizedMovementsByCategoryForHarvestSeason() {
        Farm farm = saveFarm("Farm");
        Farm otherFarm = saveFarm("Other farm");
        ProductionActivity activity = saveActivity(farm, "Soy");
        ProductionActivity otherActivity = saveActivity(otherFarm, "Corn");
        HarvestSeason season = saveSeason(farm, activity, null, null, null, "Safra A");
        HarvestSeason otherSeason =
                saveSeason(otherFarm, otherActivity, null, null, null, "Safra B");
        HarvestSeason anotherSeason = saveSeason(farm, activity, null, null, null, "Safra C");
        FinancialCategory diesel = saveCategory(farm, "Diesel", TransactionType.EXPENSE);
        FinancialCategory fertilizer = saveCategory(farm, "Fertilizantes", TransactionType.EXPENSE);
        FinancialCategory sales = saveCategory(farm, "Vendas", TransactionType.INCOME);
        User user = saveUser();

        saveTransactionWithCategory(
                farm, user, season, diesel, TransactionType.EXPENSE, PaymentStatus.PAID, "40");
        saveTransactionWithCategory(
                farm, user, season, diesel, TransactionType.EXPENSE, PaymentStatus.PAID, "60");
        saveTransactionWithCategory(
                farm, user, season, fertilizer, TransactionType.EXPENSE, PaymentStatus.PAID, "100");
        saveTransaction(farm, user, season, TransactionType.EXPENSE, PaymentStatus.PAID, "50");
        saveTransactionWithCategory(
                farm, user, season, sales, TransactionType.INCOME, PaymentStatus.PAID, "300");
        saveTransactionWithCategory(
                farm, user, season, diesel, TransactionType.EXPENSE, PaymentStatus.PENDING, "500");
        FinancialTransaction inactiveTransaction =
                saveTransactionWithCategory(
                        farm,
                        user,
                        season,
                        diesel,
                        TransactionType.EXPENSE,
                        PaymentStatus.PAID,
                        "1000");
        inactiveTransaction.setRecordStatus(FinancialRecordStatus.DELETED);
        financialTransactionRepository.save(inactiveTransaction);
        saveTransactionWithCategory(
                farm,
                user,
                anotherSeason,
                diesel,
                TransactionType.EXPENSE,
                PaymentStatus.PAID,
                "2000");
        saveTransactionWithCategory(
                otherFarm,
                user,
                otherSeason,
                diesel,
                TransactionType.EXPENSE,
                PaymentStatus.PAID,
                "3000");

        HarvestCategoryMovementsResponse response =
                harvestSeasonService.getCategoryBreakdown(season.getId());
        HarvestSeasonDetailSummaryResponse summary =
                harvestSeasonService.getSummary(season.getId());

        assertThat(response.expenses().total()).isEqualByComparingTo("250.00");
        assertThat(response.expenses().categories())
                .extracting(category -> category.categoryName())
                .containsExactly("Diesel", "Fertilizantes", "Sem categoria");
        assertThat(response.expenses().categories())
                .extracting(category -> category.amount())
                .containsExactly(
                        new BigDecimal("100.00"),
                        new BigDecimal("100.00"),
                        new BigDecimal("50.00"));
        assertThat(response.expenses().categories())
                .extracting(category -> category.percentage())
                .containsExactly(
                        new BigDecimal("40.00"), new BigDecimal("40.00"), new BigDecimal("20.00"));
        assertThat(response.expenses().categories().getLast().categoryId()).isNull();
        assertThat(response.incomes().total()).isEqualByComparingTo("300.00");
        assertThat(response.incomes().categories())
                .extracting(category -> category.categoryName())
                .containsExactly("Vendas");
        assertThat(response.incomes().categories().getFirst().percentage())
                .isEqualByComparingTo("100.00");
        assertThat(response.expenses().total()).isEqualByComparingTo(summary.realizedCost());
        assertThat(response.incomes().total()).isEqualByComparingTo(summary.realizedRevenue());
    }

    @Test
    void shouldReturnZeroTotalsAndNoCategoriesWhenHarvestHasNoPaidMovements() {
        Farm farm = saveFarm("Farm");
        ProductionActivity activity = saveActivity(farm, "Soy");
        HarvestSeason season = saveSeason(farm, activity, null, null, null, "Safra A");

        HarvestCategoryMovementsResponse response =
                harvestSeasonService.getCategoryBreakdown(season.getId());

        assertThat(response.expenses().total()).isEqualByComparingTo("0.00");
        assertThat(response.expenses().categories()).isEmpty();
        assertThat(response.incomes().total()).isEqualByComparingTo("0.00");
        assertThat(response.incomes().categories()).isEmpty();
    }

    @Test
    void shouldComparePlannedAndRealizedAmountsByCategory() {
        Farm farm = saveFarm("Farm");
        Farm otherFarm = saveFarm("Other farm");
        ProductionActivity activity = saveActivity(farm, "Soy");
        ProductionActivity otherActivity = saveActivity(otherFarm, "Corn");
        HarvestSeason season = saveSeason(farm, activity, null, null, null, "Safra A");
        HarvestSeason otherSeason =
                saveSeason(otherFarm, otherActivity, null, null, null, "Safra B");
        FinancialCategory seeds = saveCategory(farm, "Sementes", TransactionType.EXPENSE);
        FinancialCategory fertilizer = saveCategory(farm, "Fertilizantes", TransactionType.EXPENSE);
        FinancialCategory fuel = saveCategory(farm, "Combustível", TransactionType.EXPENSE);
        FinancialCategory sale = saveCategory(farm, "Venda", TransactionType.INCOME);
        FinancialCategory bonus = saveCategory(farm, "Bônus", TransactionType.INCOME);
        User user = saveUser();

        harvestSeasonBudgetItemService.create(
                season.getId(),
                budgetItemRequest(seeds.getId(), TransactionType.EXPENSE, "A", "10000"));
        harvestSeasonBudgetItemService.create(
                season.getId(),
                budgetItemRequest(seeds.getId(), TransactionType.EXPENSE, "B", "7000"));
        harvestSeasonBudgetItemService.create(
                season.getId(),
                budgetItemRequest(fertilizer.getId(), TransactionType.EXPENSE, "C", "25000"));
        harvestSeasonBudgetItemService.create(
                season.getId(),
                budgetItemRequest(sale.getId(), TransactionType.INCOME, "D", "20000"));
        saveLegacyBudgetItem(season, TransactionType.EXPENSE, "15000");
        saveTransactionWithCategory(
                farm, user, season, seeds, TransactionType.EXPENSE, PaymentStatus.PAID, "26000");
        saveTransactionWithCategory(
                farm, user, season, fuel, TransactionType.EXPENSE, PaymentStatus.PAID, "8000");
        saveTransaction(farm, user, season, TransactionType.EXPENSE, PaymentStatus.PAID, "3000");
        saveTransactionWithCategory(
                farm, user, season, sale, TransactionType.INCOME, PaymentStatus.PAID, "15000");
        saveTransactionWithCategory(
                farm, user, season, bonus, TransactionType.INCOME, PaymentStatus.PAID, "8000");
        saveTransactionWithCategory(
                otherFarm,
                user,
                otherSeason,
                fuel,
                TransactionType.EXPENSE,
                PaymentStatus.PAID,
                "9000");

        HarvestCategoryComparisonResponse response =
                harvestSeasonService.getCategoryComparison(season.getId());

        assertThat(response.expenses().plannedTotal()).isEqualByComparingTo("42000.00");
        assertThat(response.expenses().realizedTotal()).isEqualByComparingTo("37000.00");
        assertThat(response.expenses().difference()).isEqualByComparingTo("-5000.00");
        assertThat(response.expenses().categories())
                .extracting(category -> category.categoryName())
                .containsExactly("Sementes", "Combustível", "Sem categoria", "Fertilizantes");
        assertThat(response.expenses().categories().getFirst())
                .extracting(
                        category -> category.plannedAmount(),
                        category -> category.realizedAmount(),
                        category -> category.difference(),
                        category -> category.percentageDifference(),
                        category -> category.status(),
                        category -> category.semantic())
                .containsExactly(
                        new BigDecimal("17000.00"),
                        new BigDecimal("26000.00"),
                        new BigDecimal("9000.00"),
                        new BigDecimal("52.94"),
                        HarvestCategoryComparisonStatus.ABOVE_PLAN,
                        ComparisonSemantic.WORSE);
        assertThat(response.expenses().categories().get(1))
                .extracting(
                        category -> category.planned(),
                        category -> category.plannedAmount(),
                        category -> category.status(),
                        category -> category.semantic())
                .containsExactly(
                        false,
                        null,
                        HarvestCategoryComparisonStatus.UNPLANNED,
                        ComparisonSemantic.WORSE);
        assertThat(response.expenses().categories().get(2))
                .extracting(category -> category.status(), category -> category.semantic())
                .containsExactly(
                        HarvestCategoryComparisonStatus.BELOW_PLAN, ComparisonSemantic.BETTER);
        assertThat(response.expenses().categories().getLast())
                .extracting(
                        category -> category.realizedAmount(),
                        category -> category.status(),
                        category -> category.semantic())
                .containsExactly(
                        BigDecimal.ZERO,
                        HarvestCategoryComparisonStatus.NO_MOVEMENT,
                        ComparisonSemantic.NEUTRAL);
        assertThat(response.incomes().categories())
                .extracting(category -> category.categoryName())
                .containsExactly("Venda", "Bônus");
        assertThat(response.incomes().categories().getFirst().semantic())
                .isEqualTo(ComparisonSemantic.WORSE);
        assertThat(response.incomes().categories().getLast())
                .extracting(category -> category.status(), category -> category.semantic())
                .containsExactly(
                        HarvestCategoryComparisonStatus.UNPLANNED, ComparisonSemantic.BETTER);
    }

    @Test
    void shouldCompareTwoHarvestSeasonsUsingExistingFinancialSummaries() {
        Farm farm = saveFarm("Farm");
        ProductionActivity activity = saveActivity(farm, "Soy");
        FinancialCategory expense = saveCategory(farm, "Inputs", TransactionType.EXPENSE);
        FinancialCategory income = saveCategory(farm, "Sales", TransactionType.INCOME);
        HarvestSeason first = saveSeason(farm, activity, null, null, "10", "Safra A");
        HarvestSeason second = saveSeason(farm, activity, null, null, "20", "Safra B");
        User user = saveUser();

        harvestSeasonBudgetItemService.create(
                first.getId(),
                budgetItemRequest(expense.getId(), TransactionType.EXPENSE, "Input", "100"));
        harvestSeasonBudgetItemService.create(
                first.getId(),
                budgetItemRequest(income.getId(), TransactionType.INCOME, "Sale", "200"));
        harvestSeasonBudgetItemService.create(
                second.getId(),
                budgetItemRequest(expense.getId(), TransactionType.EXPENSE, "Input", "150"));
        harvestSeasonBudgetItemService.create(
                second.getId(),
                budgetItemRequest(income.getId(), TransactionType.INCOME, "Sale", "300"));
        saveTransaction(farm, user, first, TransactionType.EXPENSE, PaymentStatus.PAID, "40");
        saveTransaction(farm, user, first, TransactionType.INCOME, PaymentStatus.PAID, "80");
        saveTransaction(farm, user, second, TransactionType.EXPENSE, PaymentStatus.PAID, "70");
        saveTransaction(farm, user, second, TransactionType.INCOME, PaymentStatus.PAID, "140");

        var comparison = harvestSeasonService.compare(farm.getId(), first.getId(), second.getId());

        assertThat(comparison.harvestA().planning().plannedCost()).isEqualByComparingTo("100");
        assertThat(comparison.harvestB().planning().plannedCost()).isEqualByComparingTo("150");
        assertThat(comparison.harvestB().perHectare().plannedCostPerHectare())
                .isEqualByComparingTo("7.50");
        assertThat(comparison.differences())
                .anySatisfy(
                        difference -> {
                            assertThat(difference.metric().name()).isEqualTo("PLANNED_COST");
                            assertThat(difference.difference()).isEqualByComparingTo("50");
                            assertThat(difference.percentageDifference())
                                    .isEqualByComparingTo("50.00");
                            assertThat(difference.semantic().name()).isEqualTo("WORSE");
                        });
        assertThat(comparison.bestMetrics())
                .anySatisfy(
                        best -> {
                            assertThat(best.metric())
                                    .isEqualTo(
                                            HarvestSeasonComparisonMetric.PLANNED_COST_PER_HECTARE);
                            assertThat(best.harvestSeasonIds()).containsExactly(second.getId());
                        })
                .noneMatch(best -> best.metric() == HarvestSeasonComparisonMetric.PLANNED_COST);
    }

    @Test
    void shouldRejectComparisonForDifferentOrEqualHarvestSeasons() {
        Farm farm = saveFarm("Farm");
        Farm otherFarm = saveFarm("Other farm");
        ProductionActivity activity = saveActivity(farm, "Soy");
        ProductionActivity otherActivity = saveActivity(otherFarm, "Corn");
        HarvestSeason season = saveSeason(farm, activity, null, null, null, "Safra A");
        HarvestSeason otherSeason =
                saveSeason(otherFarm, otherActivity, null, null, null, "Safra B");

        assertThatThrownBy(
                        () ->
                                harvestSeasonService.compare(
                                        farm.getId(), season.getId(), season.getId()))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(
                        () ->
                                harvestSeasonService.compare(
                                        farm.getId(), season.getId(), otherSeason.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Harvest seasons must belong to the selected farm.");
    }

    @Test
    void shouldReturnOnlyThreeMostRecentInProgressDashboardSeasons() {
        Farm farm = saveFarm("Farm");
        ProductionActivity activity = saveActivity(farm, "Coffee");
        HarvestSeason oldest = saveSeason(farm, activity, null, null, null, "Oldest");
        HarvestSeason middle = saveSeason(farm, activity, null, null, null, "Middle");
        HarvestSeason latest = saveSeason(farm, activity, null, null, null, "Latest");
        HarvestSeason excluded = saveSeason(farm, activity, null, null, null, "Excluded");
        HarvestSeason planned = saveSeason(farm, activity, null, null, null, "Planned");
        HarvestSeason finished = saveSeason(farm, activity, null, null, null, "Finished");
        oldest.setStatus(HarvestSeasonStatus.IN_PROGRESS);
        oldest.setStartDate(LocalDate.of(2026, 1, 1));
        middle.setStatus(HarvestSeasonStatus.IN_PROGRESS);
        middle.setStartDate(LocalDate.of(2026, 2, 1));
        latest.setStatus(HarvestSeasonStatus.IN_PROGRESS);
        latest.setStartDate(LocalDate.of(2026, 3, 1));
        excluded.setStatus(HarvestSeasonStatus.IN_PROGRESS);
        excluded.setStartDate(LocalDate.of(2025, 12, 1));
        planned.setStatus(HarvestSeasonStatus.PLANNED);
        finished.setStatus(HarvestSeasonStatus.FINISHED);
        harvestSeasonRepository.saveAll(
                List.of(oldest, middle, latest, excluded, planned, finished));

        var response = harvestSeasonService.findDashboardSeasons(farm.getId());

        assertThat(response)
                .extracting(item -> item.name())
                .containsExactly("Latest", "Middle", "Oldest");
        assertThat(response)
                .allMatch(item -> item.status().equals(HarvestSeasonStatus.IN_PROGRESS));
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

    private FinancialCategory saveCategory(Farm farm, String name, TransactionType type) {
        FinancialCategory category = new FinancialCategory();
        category.setFarm(farm);
        category.setName(name);
        category.setType(type);
        category.setStatus(FinancialCategoryStatus.ACTIVE);
        return financialCategoryRepository.save(category);
    }

    private HarvestSeasonBudgetItemRequest budgetItemRequest(
            Long categoryId, TransactionType type, String description, String amount) {
        return new HarvestSeasonBudgetItemRequest(
                categoryId, type, description, toBigDecimal(amount));
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

        HarvestSeason savedSeason = harvestSeasonRepository.save(season);
        saveLegacyBudgetItem(savedSeason, TransactionType.INCOME, expectedRevenue);
        saveLegacyBudgetItem(savedSeason, TransactionType.EXPENSE, expectedCost);
        return savedSeason;
    }

    private void saveLegacyBudgetItem(
            HarvestSeason season, TransactionType type, String plannedAmount) {
        if (plannedAmount == null) {
            return;
        }

        HarvestSeasonBudgetItem item = new HarvestSeasonBudgetItem();
        item.setHarvestSeason(season);
        item.setType(type);
        item.setDescription("Planejamento anterior");
        item.setPlannedAmount(toBigDecimal(plannedAmount));
        harvestSeasonBudgetItemRepository.save(item);
    }

    private HarvestSeasonRequest seasonRequest(Long farmId, Long activityId, String name) {
        return new HarvestSeasonRequest(
                farmId,
                activityId,
                name,
                "Season description",
                LocalDate.of(2026, 1, 1),
                null,
                BigDecimal.ZERO);
    }

    private HarvestSeasonUpdateRequest seasonUpdateRequest(Long activityId, String name) {
        return new HarvestSeasonUpdateRequest(
                activityId,
                name,
                "Updated description",
                LocalDate.of(2026, 1, 1),
                null,
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

    private FinancialTransaction saveTransactionWithCategory(
            Farm farm,
            User user,
            HarvestSeason harvestSeason,
            FinancialCategory category,
            TransactionType type,
            PaymentStatus status,
            String amount) {
        FinancialTransaction transaction =
                saveTransaction(farm, user, harvestSeason, type, status, amount);
        transaction.setCategory(category);

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
