package br.com.gestaodireta.financial.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.gestaodireta.farm.entity.Farm;
import br.com.gestaodireta.farm.enumeration.FarmStatus;
import br.com.gestaodireta.farm.repository.FarmRepository;
import br.com.gestaodireta.financial.dto.FinancialReportFilter;
import br.com.gestaodireta.financial.dto.FinancialReportResponse;
import br.com.gestaodireta.financial.dto.FinancialReportTransactionResponse;
import br.com.gestaodireta.financial.entity.FinancialCategory;
import br.com.gestaodireta.financial.entity.FinancialTransaction;
import br.com.gestaodireta.financial.enumeration.FinancialCategoryStatus;
import br.com.gestaodireta.financial.enumeration.FinancialPlanningAvailability;
import br.com.gestaodireta.financial.enumeration.FinancialRecordStatus;
import br.com.gestaodireta.financial.enumeration.FinancialReportBasis;
import br.com.gestaodireta.financial.enumeration.FinancialReportGranularity;
import br.com.gestaodireta.financial.enumeration.PaymentStatus;
import br.com.gestaodireta.financial.enumeration.TransactionType;
import br.com.gestaodireta.financial.repository.FinancialCategoryRepository;
import br.com.gestaodireta.financial.repository.FinancialTransactionRepository;
import br.com.gestaodireta.harvest.entity.HarvestSeason;
import br.com.gestaodireta.harvest.entity.HarvestSeasonBudgetItem;
import br.com.gestaodireta.harvest.entity.ProductionActivity;
import br.com.gestaodireta.harvest.enumeration.HarvestSeasonStatus;
import br.com.gestaodireta.harvest.enumeration.ProductionActivityStatus;
import br.com.gestaodireta.harvest.repository.HarvestSeasonBudgetItemRepository;
import br.com.gestaodireta.harvest.repository.HarvestSeasonRepository;
import br.com.gestaodireta.harvest.repository.ProductionActivityRepository;
import br.com.gestaodireta.shared.exception.ValidationException;
import br.com.gestaodireta.shared.response.PageResponse;
import br.com.gestaodireta.support.PostgresIntegrationTest;
import br.com.gestaodireta.user.entity.User;
import br.com.gestaodireta.user.enumeration.UserStatus;
import br.com.gestaodireta.user.enumeration.UserType;
import br.com.gestaodireta.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest
class FinancialReportServiceTest extends PostgresIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 30);

    @Autowired private FinancialReportService financialReportService;

    @Autowired private FinancialTransactionRepository financialTransactionRepository;

    @Autowired private FinancialCategoryRepository financialCategoryRepository;

    @Autowired private HarvestSeasonRepository harvestSeasonRepository;

    @Autowired private HarvestSeasonBudgetItemRepository harvestSeasonBudgetItemRepository;

    @Autowired private ProductionActivityRepository productionActivityRepository;

    @Autowired private FarmRepository farmRepository;

    @Autowired private UserRepository userRepository;

    @Autowired private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        financialTransactionRepository.deleteAll();
        harvestSeasonBudgetItemRepository.deleteAll();
        harvestSeasonRepository.deleteAll();
        productionActivityRepository.deleteAll();
        financialCategoryRepository.deleteAll();
        farmRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void shouldAllowExactlyTwelveCalendarMonths() {
        Farm farm = saveFarm();

        FinancialReportResponse report =
                financialReportService.getReport(
                        new FinancialReportFilter(
                                farm.getId(),
                                LocalDate.of(2025, 1, 1),
                                LocalDate.of(2026, 1, 1),
                                FinancialReportBasis.ACCRUAL,
                                null,
                                null));

        assertThat(report.startDate()).isEqualTo(LocalDate.of(2025, 1, 1));
        assertThat(report.endDate()).isEqualTo(LocalDate.of(2026, 1, 1));
    }

    @Test
    void shouldRejectPeriodsLongerThanTwelveCalendarMonths() {
        Farm farm = saveFarm();
        FinancialReportFilter filter =
                new FinancialReportFilter(
                        farm.getId(),
                        LocalDate.of(2025, 1, 1),
                        LocalDate.of(2026, 1, 2),
                        FinancialReportBasis.ACCRUAL,
                        null,
                        null);

        assertThatThrownBy(() -> financialReportService.getReport(filter))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Report period cannot exceed 12 months");
    }

    @Test
    void shouldRejectAnEndDateBeforeTheStartDate() {
        Farm farm = saveFarm();
        FinancialReportFilter filter =
                new FinancialReportFilter(
                        farm.getId(),
                        LocalDate.of(2026, 2, 1),
                        LocalDate.of(2026, 1, 31),
                        FinancialReportBasis.ACCRUAL,
                        null,
                        null);

        assertThatThrownBy(() -> financialReportService.getReport(filter))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Start date cannot be after end date");
    }

    @Test
    void shouldCalculateFilteredFinancialIndicatorsInTheBackend() {
        Farm farm = saveFarm();
        farm.setTotalArea(new BigDecimal("10.00"));
        farmRepository.save(farm);
        User user = saveUser();
        Farm otherFarm = saveFarm("Other farm");
        saveTransaction(
                otherFarm,
                user,
                TransactionType.INCOME,
                PaymentStatus.PAID,
                new BigDecimal("999.00"),
                LocalDate.of(2026, 1, 10),
                LocalDate.of(2026, 1, 10),
                LocalDate.of(2026, 1, 10));
        saveTransaction(
                farm,
                user,
                TransactionType.INCOME,
                PaymentStatus.PAID,
                new BigDecimal("100.00"),
                LocalDate.of(2026, 1, 10),
                LocalDate.of(2026, 1, 10),
                LocalDate.of(2026, 1, 10));
        saveTransaction(
                farm,
                user,
                TransactionType.EXPENSE,
                PaymentStatus.PAID,
                new BigDecimal("40.00"),
                LocalDate.of(2026, 1, 11),
                LocalDate.of(2026, 1, 11),
                LocalDate.of(2026, 1, 11));
        saveTransaction(
                farm,
                user,
                TransactionType.INCOME,
                PaymentStatus.PENDING,
                new BigDecimal("50.00"),
                LocalDate.of(2026, 1, 12),
                LocalDate.of(2026, 1, 20),
                null);
        saveTransaction(
                farm,
                user,
                TransactionType.EXPENSE,
                PaymentStatus.OVERDUE,
                new BigDecimal("30.00"),
                LocalDate.of(2026, 1, 5),
                LocalDate.of(2026, 1, 5),
                null);

        FinancialReportResponse report =
                financialReportService.getReport(
                        new FinancialReportFilter(
                                farm.getId(),
                                LocalDate.of(2026, 1, 1),
                                LocalDate.of(2026, 1, 31),
                                FinancialReportBasis.ACCRUAL,
                                null,
                                null));

        assertThat(report.financialIndicators().result().totalIncome())
                .isEqualByComparingTo("150.00");
        assertThat(report.financialIndicators().result().totalExpense())
                .isEqualByComparingTo("70.00");
        assertThat(report.financialIndicators().result().realizedResult())
                .isEqualByComparingTo("60.00");
        assertThat(report.financialIndicators().result().projectedResult())
                .isEqualByComparingTo("80.00");
        assertThat(report.financialIndicators().result().marginPercentage())
                .isEqualByComparingTo("53.33");
        assertThat(report.financialIndicators().liquidity().accountsReceivable())
                .isEqualByComparingTo("50.00");
        assertThat(report.financialIndicators().liquidity().accountsPayable())
                .isEqualByComparingTo("30.00");
        assertThat(report.financialIndicators().liquidity().overdueReceivable())
                .isEqualByComparingTo("50.00");
        assertThat(report.financialIndicators().liquidity().overduePayable())
                .isEqualByComparingTo("30.00");
        assertThat(report.financialIndicators().liquidity().coveragePercentage())
                .isEqualByComparingTo("366.67");
        assertThat(report.financialIndicators().liquidity().cashNeed())
                .isEqualByComparingTo("-80.00");
        assertThat(report.financialIndicators().efficiency().costToIncomePercentage())
                .isEqualByComparingTo("46.67");
        assertThat(report.financialIndicators().efficiency().returnOnCostsPercentage())
                .isEqualByComparingTo("150.00");
        assertThat(report.financialIndicators().ruralManagement().incomePerHectare())
                .isEqualByComparingTo("15.00");
        assertThat(report.financialIndicators().ruralManagement().costPerHectare())
                .isEqualByComparingTo("7.00");
        assertThat(report.financialIndicators().ruralManagement().resultPerHectare())
                .isEqualByComparingTo("8.00");
    }

    @Test
    void shouldReturnUnavailableRatiosWithoutAreaOrPlanning() {
        Farm farm = saveFarm();

        FinancialReportResponse report =
                financialReportService.getReport(
                        new FinancialReportFilter(
                                farm.getId(),
                                LocalDate.of(2026, 1, 1),
                                LocalDate.of(2026, 1, 31),
                                FinancialReportBasis.ACCRUAL,
                                null,
                                null));

        assertThat(report.financialIndicators().result().marginPercentage()).isNull();
        assertThat(report.financialIndicators().liquidity().coveragePercentage()).isNull();
        assertThat(report.financialIndicators().efficiency().costToIncomePercentage()).isNull();
        assertThat(report.financialIndicators().efficiency().returnOnCostsPercentage()).isNull();
        assertThat(report.financialIndicators().ruralManagement()).isNull();
        assertThat(report.financialIndicators().planning().availability())
                .isEqualTo(FinancialPlanningAvailability.HARVEST_REQUIRED);
    }

    @Test
    void shouldCalculatePlanningForACompleteFilteredHarvestPeriod() {
        Farm farm = saveFarm();
        User user = saveUser();
        FinancialCategory incomeCategory = saveCategory(farm, "Venda", TransactionType.INCOME);
        FinancialCategory expenseCategory = saveCategory(farm, "Insumos", TransactionType.EXPENSE);
        HarvestSeason harvest = saveHarvest(farm, "Soja");
        harvest.setEndDate(LocalDate.of(2026, 12, 31));
        harvest.setAreaHectares(new BigDecimal("20.00"));
        harvestSeasonRepository.save(harvest);
        saveBudgetItem(harvest, incomeCategory, TransactionType.INCOME, "200.00");
        saveBudgetItem(harvest, expenseCategory, TransactionType.EXPENSE, "100.00");
        saveTransaction(
                farm,
                user,
                incomeCategory,
                harvest,
                TransactionType.INCOME,
                new BigDecimal("120.00"));
        saveTransaction(
                farm,
                user,
                expenseCategory,
                harvest,
                TransactionType.EXPENSE,
                new BigDecimal("80.00"));

        FinancialReportResponse report =
                financialReportService.getReport(
                        new FinancialReportFilter(
                                farm.getId(),
                                LocalDate.of(2026, 1, 1),
                                LocalDate.of(2026, 12, 31),
                                FinancialReportBasis.ACCRUAL,
                                java.util.List.of(harvest.getId()),
                                null));

        assertThat(report.financialIndicators().planning().availability())
                .isEqualTo(FinancialPlanningAvailability.AVAILABLE);
        assertThat(report.financialIndicators().planning().incomeExecutionPercentage())
                .isEqualByComparingTo("60.00");
        assertThat(report.financialIndicators().planning().expenseExecutionPercentage())
                .isEqualByComparingTo("80.00");
        assertThat(report.financialIndicators().planning().incomeDeviation())
                .isEqualByComparingTo("-80.00");
        assertThat(report.financialIndicators().planning().expenseDeviation())
                .isEqualByComparingTo("-20.00");
        assertThat(report.financialIndicators().ruralManagement().areaHectares())
                .isEqualByComparingTo("20.00");

        FinancialReportResponse expenseCategoryReport =
                financialReportService.getReport(
                        new FinancialReportFilter(
                                farm.getId(),
                                LocalDate.of(2026, 1, 1),
                                LocalDate.of(2026, 12, 31),
                                FinancialReportBasis.ACCRUAL,
                                java.util.List.of(harvest.getId()),
                                java.util.List.of(expenseCategory.getId())));

        assertThat(
                        expenseCategoryReport
                                .financialIndicators()
                                .planning()
                                .incomeExecutionPercentage())
                .isNull();
        assertThat(
                        expenseCategoryReport
                                .financialIndicators()
                                .planning()
                                .expenseExecutionPercentage())
                .isEqualByComparingTo("80.00");
    }

    @Test
    void shouldNotComparePlanningAgainstAPartialHarvestPeriod() {
        Farm farm = saveFarm();
        HarvestSeason harvest = saveHarvest(farm, "Soja");
        harvest.setEndDate(LocalDate.of(2026, 12, 31));
        harvestSeasonRepository.save(harvest);
        saveBudgetItem(harvest, null, TransactionType.EXPENSE, "100.00");

        FinancialReportResponse report =
                financialReportService.getReport(
                        new FinancialReportFilter(
                                farm.getId(),
                                LocalDate.of(2026, 2, 1),
                                LocalDate.of(2026, 12, 31),
                                FinancialReportBasis.ACCRUAL,
                                java.util.List.of(harvest.getId()),
                                null));

        assertThat(report.financialIndicators().planning().availability())
                .isEqualTo(FinancialPlanningAvailability.PARTIAL_PERIOD);
        assertThat(report.financialIndicators().planning().expenseExecutionPercentage()).isNull();
    }

    @Test
    void shouldNotAggregateAreaAcrossMultipleFilteredHarvests() {
        Farm farm = saveFarm();
        HarvestSeason soybean = saveHarvest(farm, "Soja");
        soybean.setAreaHectares(new BigDecimal("20.00"));
        harvestSeasonRepository.save(soybean);
        HarvestSeason corn = saveHarvest(farm, "Milho");
        corn.setAreaHectares(new BigDecimal("30.00"));
        harvestSeasonRepository.save(corn);

        FinancialReportResponse report =
                financialReportService.getReport(
                        new FinancialReportFilter(
                                farm.getId(),
                                LocalDate.of(2026, 1, 1),
                                LocalDate.of(2026, 1, 31),
                                FinancialReportBasis.ACCRUAL,
                                java.util.List.of(soybean.getId(), corn.getId()),
                                null));

        assertThat(report.financialIndicators().ruralManagement()).isNull();
        assertThat(report.financialIndicators().planning().availability())
                .isEqualTo(FinancialPlanningAvailability.HARVEST_REQUIRED);
    }

    @Test
    void shouldUseCashReferenceDatesAndExposeUnallocatedTransactions() {
        Farm farm = saveFarm();
        User user = saveUser();
        saveTransaction(
                farm,
                user,
                TransactionType.INCOME,
                PaymentStatus.PAID,
                new BigDecimal("100.00"),
                LocalDate.of(2026, 1, 5),
                null,
                LocalDate.of(2026, 2, 2));
        saveTransaction(
                farm,
                user,
                TransactionType.EXPENSE,
                PaymentStatus.PENDING,
                new BigDecimal("25.00"),
                LocalDate.of(2026, 1, 5),
                LocalDate.of(2026, 2, 8),
                null);
        saveTransaction(
                farm,
                user,
                TransactionType.EXPENSE,
                PaymentStatus.PENDING,
                new BigDecimal("30.00"),
                LocalDate.of(2026, 1, 5),
                null,
                null);

        FinancialReportFilter filter =
                new FinancialReportFilter(
                        farm.getId(),
                        LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 2, 28),
                        FinancialReportBasis.CASH,
                        null,
                        null);

        FinancialReportResponse report = financialReportService.getReport(filter);
        PageResponse<FinancialReportTransactionResponse> transactions =
                financialReportService.findTransactions(filter, 0, 20, "referenceDate", "ASC");

        assertThat(report.summary().totalIncome()).isEqualByComparingTo("100.00");
        assertThat(report.summary().totalExpense()).isEqualByComparingTo("25.00");
        assertThat(report.summary().netBalance()).isEqualByComparingTo("75.00");
        assertThat(report.unallocated().expense()).isEqualByComparingTo("30.00");
        assertThat(report.evolution()).hasSize(2);
        assertThat(report.evolution().get(0).transactionCount()).isZero();
        assertThat(report.evolution().get(1).transactionCount()).isEqualTo(2);
        assertThat(transactions.content())
                .extracting(FinancialReportTransactionResponse::referenceDate)
                .containsExactly(LocalDate.of(2026, 2, 2), LocalDate.of(2026, 2, 8));
    }

    @Test
    void shouldUseTransactionDateForAllAccrualStatuses() {
        Farm farm = saveFarm();
        User user = saveUser();
        saveTransaction(
                farm,
                user,
                TransactionType.EXPENSE,
                PaymentStatus.OVERDUE,
                new BigDecimal("70.00"),
                LocalDate.of(2026, 1, 15),
                LocalDate.of(2026, 3, 1),
                null);

        FinancialReportResponse report =
                financialReportService.getReport(
                        new FinancialReportFilter(
                                farm.getId(),
                                LocalDate.of(2026, 1, 1),
                                LocalDate.of(2026, 1, 31),
                                FinancialReportBasis.ACCRUAL,
                                null,
                                null));

        assertThat(report.summary().totalExpense()).isEqualByComparingTo("70.00");
        assertThat(report.evolution().getFirst().transactionCount()).isEqualTo(1);
    }

    @Test
    void shouldBuildCommitmentsAsOfTheReportEndDate() {
        Farm farm = saveFarm();
        User user = saveUser();
        saveTransaction(
                farm,
                user,
                TransactionType.INCOME,
                PaymentStatus.PENDING,
                new BigDecimal("100.00"),
                LocalDate.of(2026, 7, 20),
                LocalDate.of(2026, 7, 31),
                null);
        saveTransaction(
                farm,
                user,
                TransactionType.EXPENSE,
                PaymentStatus.PENDING,
                new BigDecimal("40.00"),
                TODAY,
                TODAY,
                null);
        saveTransaction(
                farm,
                user,
                TransactionType.INCOME,
                PaymentStatus.PENDING,
                new BigDecimal("60.00"),
                TODAY,
                LocalDate.of(2026, 8, 31),
                null);
        saveTransaction(
                farm,
                user,
                TransactionType.EXPENSE,
                PaymentStatus.PENDING,
                new BigDecimal("30.00"),
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 1),
                null);
        saveTransaction(
                farm,
                user,
                TransactionType.INCOME,
                PaymentStatus.PENDING,
                new BigDecimal("25.00"),
                LocalDate.of(2026, 8, 20),
                null,
                null);
        saveTransaction(
                farm,
                user,
                TransactionType.INCOME,
                PaymentStatus.PAID,
                new BigDecimal("80.00"),
                LocalDate.of(2026, 8, 15),
                LocalDate.of(2026, 8, 15),
                LocalDate.of(2026, 9, 5));
        saveTransaction(
                farm,
                user,
                TransactionType.EXPENSE,
                PaymentStatus.PAID,
                new BigDecimal("90.00"),
                LocalDate.of(2026, 8, 15),
                LocalDate.of(2026, 8, 15),
                LocalDate.of(2026, 8, 20));

        FinancialReportResponse report =
                financialReportService.getReport(
                        new FinancialReportFilter(
                                farm.getId(),
                                LocalDate.of(2026, 8, 1),
                                LocalDate.of(2026, 8, 31),
                                FinancialReportBasis.ACCRUAL,
                                null,
                                null));

        assertThat(report.summary().totalIncome()).isEqualByComparingTo("165.00");
        assertThat(report.commitments().accountsReceivable()).isEqualByComparingTo("265.00");
        assertThat(report.commitments().accountsPayable()).isEqualByComparingTo("40.00");
        assertThat(report.commitments().overdueReceivableAmount()).isEqualByComparingTo("180.00");
        assertThat(report.commitments().overdueReceivableCount()).isEqualTo(2);
        assertThat(report.commitments().overduePayableAmount()).isEqualByComparingTo("40.00");
        assertThat(report.commitments().overduePayableCount()).isEqualTo(1);
        assertThat(report.commitments().next30DaysAvailable()).isFalse();
        assertThat(report.commitments().next30DaysReceivable()).isNull();
        assertThat(report.commitments().next30DaysPayable()).isNull();
    }

    @Test
    void shouldExposeTheFixedNextThirtyDayWindowOnlyWhenAvailable() {
        Farm farm = saveFarm();
        User user = saveUser();
        saveTransaction(
                farm,
                user,
                TransactionType.INCOME,
                PaymentStatus.PENDING,
                new BigDecimal("10.00"),
                TODAY,
                TODAY,
                null);
        saveTransaction(
                farm,
                user,
                TransactionType.EXPENSE,
                PaymentStatus.PENDING,
                new BigDecimal("20.00"),
                TODAY,
                TODAY.plusDays(15),
                null);
        saveTransaction(
                farm,
                user,
                TransactionType.INCOME,
                PaymentStatus.PENDING,
                new BigDecimal("30.00"),
                TODAY,
                TODAY.plusDays(30),
                null);
        saveTransaction(
                farm,
                user,
                TransactionType.EXPENSE,
                PaymentStatus.PENDING,
                new BigDecimal("40.00"),
                TODAY,
                TODAY.plusDays(31),
                null);

        FinancialReportResponse unavailable = reportForEndDate(farm, TODAY.plusDays(29));
        FinancialReportResponse available = reportForEndDate(farm, TODAY.plusDays(30));
        FinancialReportResponse future = reportForEndDate(farm, TODAY.plusDays(31));

        assertThat(unavailable.commitments().next30DaysAvailable()).isFalse();
        assertThat(unavailable.commitments().next30DaysReceivable()).isNull();
        assertThat(unavailable.commitments().next30DaysPayable()).isNull();
        assertThat(available.commitments().next30DaysAvailable()).isTrue();
        assertThat(available.commitments().next30DaysReceivable()).isEqualByComparingTo("40.00");
        assertThat(available.commitments().next30DaysPayable()).isEqualByComparingTo("20.00");
        assertThat(future.commitments().next30DaysAvailable()).isTrue();
    }

    @Test
    void shouldClassifyCashEvolutionStatesWithoutDoubleCounting() {
        Farm farm = saveFarm();
        User user = saveUser();
        saveTransaction(
                farm,
                user,
                TransactionType.INCOME,
                PaymentStatus.PAID,
                new BigDecimal("40.00"),
                LocalDate.of(2026, 7, 10),
                LocalDate.of(2026, 7, 10),
                LocalDate.of(2026, 8, 15));
        saveTransaction(
                farm,
                user,
                TransactionType.INCOME,
                PaymentStatus.PENDING,
                new BigDecimal("20.00"),
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 9, 20),
                null);
        saveTransaction(
                farm,
                user,
                TransactionType.INCOME,
                PaymentStatus.PAID,
                new BigDecimal("10.00"),
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 10),
                LocalDate.of(2026, 9, 15));
        saveTransaction(
                farm,
                user,
                TransactionType.EXPENSE,
                PaymentStatus.PAID,
                new BigDecimal("30.00"),
                LocalDate.of(2026, 7, 10),
                LocalDate.of(2026, 7, 10),
                LocalDate.of(2026, 8, 20));
        saveTransaction(
                farm,
                user,
                TransactionType.EXPENSE,
                PaymentStatus.PENDING,
                new BigDecimal("15.00"),
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 9, 10),
                null);
        saveTransaction(
                farm,
                user,
                TransactionType.EXPENSE,
                PaymentStatus.PENDING,
                new BigDecimal("5.00"),
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 20),
                null);

        FinancialReportResponse report =
                financialReportService.getReport(
                        new FinancialReportFilter(
                                farm.getId(),
                                LocalDate.of(2026, 7, 1),
                                LocalDate.of(2026, 9, 30),
                                FinancialReportBasis.CASH,
                                null,
                                null,
                                FinancialReportGranularity.MONTHLY));

        assertThat(report.evolution()).hasSize(3);
        assertThat(report.evolution().get(0).overdueIncome()).isEqualByComparingTo("10.00");
        assertThat(report.evolution().get(0).overdueExpense()).isEqualByComparingTo("5.00");
        assertThat(report.evolution().get(0).overdueIncomeCount()).isEqualTo(1);
        assertThat(report.evolution().get(1).realizedIncome()).isEqualByComparingTo("40.00");
        assertThat(report.evolution().get(1).realizedExpense()).isEqualByComparingTo("30.00");
        assertThat(report.evolution().get(1).realizedResult()).isEqualByComparingTo("10.00");
        assertThat(report.evolution().get(2).projectedIncome()).isEqualByComparingTo("20.00");
        assertThat(report.evolution().get(2).projectedExpense()).isEqualByComparingTo("15.00");
        assertThat(report.evolution().get(2).overdueIncome()).isZero();
        assertThat(report.evolution().get(2).realizedIncome()).isZero();
    }

    @Test
    void shouldBuildCashFlowExpectedAndOverdueProjectedBalancesWithoutDoubleCounting() {
        Farm farm = saveFarm();
        User user = saveUser();
        saveTransaction(
                farm,
                user,
                TransactionType.INCOME,
                PaymentStatus.PAID,
                new BigDecimal("100.00"),
                LocalDate.of(2026, 7, 10),
                LocalDate.of(2026, 7, 10),
                LocalDate.of(2026, 7, 10));
        saveTransaction(
                farm,
                user,
                TransactionType.EXPENSE,
                PaymentStatus.OVERDUE,
                new BigDecimal("30.00"),
                LocalDate.of(2026, 7, 15),
                LocalDate.of(2026, 7, 31),
                null);
        saveTransaction(
                farm,
                user,
                TransactionType.INCOME,
                PaymentStatus.PENDING,
                new BigDecimal("50.00"),
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 9, 15),
                null);
        saveTransaction(
                farm,
                user,
                TransactionType.EXPENSE,
                PaymentStatus.PENDING,
                new BigDecimal("20.00"),
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 9, 20),
                null);

        FinancialReportResponse report =
                financialReportService.getReport(
                        new FinancialReportFilter(
                                farm.getId(),
                                LocalDate.of(2026, 7, 1),
                                LocalDate.of(2026, 10, 31),
                                FinancialReportBasis.CASH,
                                null,
                                null,
                                FinancialReportGranularity.MONTHLY));

        assertThat(report.cashFlow().points()).hasSize(4);
        assertThat(report.cashFlow().points().get(0).expectedBalance())
                .isEqualByComparingTo("100.00");
        assertThat(report.cashFlow().points().get(0).projectedBalance())
                .isEqualByComparingTo("70.00");
        assertThat(report.cashFlow().points().get(0).overdueExpense())
                .isEqualByComparingTo("30.00");
        assertThat(report.cashFlow().points().get(2).expectedBalance())
                .isEqualByComparingTo("130.00");
        assertThat(report.cashFlow().points().get(2).projectedBalance())
                .isEqualByComparingTo("100.00");
        assertThat(report.cashFlow().points().get(3).expectedBalance())
                .isEqualByComparingTo("130.00");
        assertThat(report.cashFlow().points().get(3).projectedBalance())
                .isEqualByComparingTo("100.00");
    }

    @Test
    void shouldAggregateQuarterlyEvolutionAndFillMissingQuarters() {
        Farm farm = saveFarm();
        User user = saveUser();
        saveTransaction(
                farm,
                user,
                TransactionType.INCOME,
                PaymentStatus.PAID,
                new BigDecimal("100.00"),
                LocalDate.of(2026, 4, 10),
                LocalDate.of(2026, 4, 10),
                LocalDate.of(2026, 4, 10));
        saveTransaction(
                farm,
                user,
                TransactionType.EXPENSE,
                PaymentStatus.PAID,
                new BigDecimal("25.00"),
                LocalDate.of(2026, 6, 10),
                LocalDate.of(2026, 6, 10),
                LocalDate.of(2026, 6, 10));

        FinancialReportResponse report =
                financialReportService.getReport(
                        new FinancialReportFilter(
                                farm.getId(),
                                LocalDate.of(2026, 1, 15),
                                LocalDate.of(2026, 12, 10),
                                FinancialReportBasis.CASH,
                                null,
                                null,
                                FinancialReportGranularity.QUARTERLY));

        assertThat(report.evolution()).hasSize(4);
        assertThat(report.evolution().get(0).label()).isEqualTo("1º tri");
        assertThat(report.evolution().get(0).periodStart()).isEqualTo(LocalDate.of(2026, 1, 15));
        assertThat(report.evolution().get(0).transactionCount()).isZero();
        assertThat(report.evolution().get(1).realizedIncome()).isEqualByComparingTo("100.00");
        assertThat(report.evolution().get(1).realizedExpense()).isEqualByComparingTo("25.00");
        assertThat(report.evolution().get(1).realizedResult()).isEqualByComparingTo("75.00");
        assertThat(report.evolution().get(3).periodEnd()).isEqualTo(LocalDate.of(2026, 12, 10));
    }

    @Test
    void shouldBuildFilteredRealizedCumulativeEvolution() {
        Farm farm = saveFarm();
        User user = saveUser();
        FinancialCategory incomeCategory =
                saveCategory(farm, "Venda de produção", TransactionType.INCOME);
        FinancialCategory expenseCategory = saveCategory(farm, "Insumos", TransactionType.EXPENSE);
        FinancialCategory ignoredCategory =
                saveCategory(farm, "Outras receitas", TransactionType.INCOME);
        HarvestSeason selectedHarvest = saveHarvest(farm, "Soja");
        HarvestSeason ignoredHarvest = saveHarvest(farm, "Milho");

        saveTransaction(
                farm,
                user,
                incomeCategory,
                selectedHarvest,
                TransactionType.INCOME,
                new BigDecimal("999.00"),
                LocalDate.of(2026, 1, 10));
        saveTransaction(
                farm,
                user,
                incomeCategory,
                selectedHarvest,
                TransactionType.INCOME,
                new BigDecimal("100.00"),
                LocalDate.of(2026, 1, 20));
        saveTransaction(
                farm,
                user,
                expenseCategory,
                selectedHarvest,
                TransactionType.EXPENSE,
                new BigDecimal("60.00"),
                LocalDate.of(2026, 1, 21));
        saveTransaction(
                farm,
                user,
                incomeCategory,
                selectedHarvest,
                TransactionType.INCOME,
                new BigDecimal("50.00"),
                LocalDate.of(2026, 2, 10));
        saveTransaction(
                farm,
                user,
                expenseCategory,
                selectedHarvest,
                TransactionType.EXPENSE,
                new BigDecimal("20.00"),
                LocalDate.of(2026, 2, 12));
        saveTransaction(
                farm,
                user,
                incomeCategory,
                selectedHarvest,
                TransactionType.INCOME,
                new BigDecimal("25.00"),
                LocalDate.of(2026, 3, 10));
        saveTransaction(
                farm,
                user,
                expenseCategory,
                selectedHarvest,
                TransactionType.EXPENSE,
                new BigDecimal("40.00"),
                LocalDate.of(2026, 3, 12));
        saveTransaction(
                farm,
                user,
                incomeCategory,
                selectedHarvest,
                TransactionType.INCOME,
                new BigDecimal("888.00"),
                LocalDate.of(2026, 3, 20));
        saveTransaction(
                farm,
                user,
                ignoredCategory,
                selectedHarvest,
                TransactionType.INCOME,
                new BigDecimal("777.00"),
                LocalDate.of(2026, 2, 10));
        saveTransaction(
                farm,
                user,
                incomeCategory,
                ignoredHarvest,
                TransactionType.INCOME,
                new BigDecimal("666.00"),
                LocalDate.of(2026, 2, 10));

        FinancialReportFilter monthlyFilter =
                new FinancialReportFilter(
                        farm.getId(),
                        LocalDate.of(2026, 1, 15),
                        LocalDate.of(2026, 3, 15),
                        FinancialReportBasis.ACCRUAL,
                        java.util.List.of(selectedHarvest.getId()),
                        java.util.List.of(incomeCategory.getId(), expenseCategory.getId()),
                        FinancialReportGranularity.MONTHLY);

        FinancialReportResponse monthly = financialReportService.getReport(monthlyFilter);

        assertThat(monthly.evolution()).hasSize(3);
        assertThat(monthly.evolution().get(0).periodStart()).isEqualTo(LocalDate.of(2026, 1, 15));
        assertThat(monthly.evolution().get(0).income()).isEqualByComparingTo("100.00");
        assertThat(monthly.evolution().get(0).expense()).isEqualByComparingTo("60.00");
        assertThat(monthly.evolution().get(2).periodEnd()).isEqualTo(LocalDate.of(2026, 3, 15));
        assertThat(monthly.realizedCumulativeEvolution())
                .extracting(point -> point.cumulativeIncome())
                .containsExactly(
                        new BigDecimal("100.00"),
                        new BigDecimal("150.00"),
                        new BigDecimal("175.00"));
        assertThat(monthly.realizedCumulativeEvolution())
                .extracting(point -> point.cumulativeExpense())
                .containsExactly(
                        new BigDecimal("60.00"), new BigDecimal("80.00"), new BigDecimal("120.00"));
        assertThat(monthly.realizedCumulativeEvolution().getLast().cumulativeIncome())
                .isEqualByComparingTo(monthly.summary().realizedIncome());
        assertThat(monthly.realizedCumulativeEvolution().getLast().cumulativeExpense())
                .isEqualByComparingTo(monthly.summary().realizedExpense());

        FinancialReportResponse quarterly =
                financialReportService.getReport(
                        new FinancialReportFilter(
                                monthlyFilter.farmId(),
                                monthlyFilter.startDate(),
                                monthlyFilter.endDate(),
                                monthlyFilter.basis(),
                                monthlyFilter.harvestSeasonIds(),
                                monthlyFilter.categoryIds(),
                                FinancialReportGranularity.QUARTERLY));

        assertThat(quarterly.evolution()).hasSize(1);
        assertThat(quarterly.realizedCumulativeEvolution()).hasSize(1);
        assertThat(quarterly.realizedCumulativeEvolution().getFirst().cumulativeIncome())
                .isEqualByComparingTo("175.00");
        assertThat(quarterly.realizedCumulativeEvolution().getFirst().cumulativeExpense())
                .isEqualByComparingTo("120.00");
    }

    @Test
    void shouldCarryCumulativeValuesAcrossEmptyMonthsAndRestartAtFilteredStart() {
        Farm farm = saveFarm();
        User user = saveUser();
        saveTransaction(
                farm,
                user,
                TransactionType.INCOME,
                PaymentStatus.PAID,
                new BigDecimal("100.00"),
                LocalDate.of(2026, 1, 15),
                LocalDate.of(2026, 1, 15),
                LocalDate.of(2026, 1, 15));
        saveTransaction(
                farm,
                user,
                TransactionType.EXPENSE,
                PaymentStatus.PAID,
                new BigDecimal("60.00"),
                LocalDate.of(2026, 1, 15),
                LocalDate.of(2026, 1, 15),
                LocalDate.of(2026, 1, 15));
        saveTransaction(
                farm,
                user,
                TransactionType.INCOME,
                PaymentStatus.PENDING,
                new BigDecimal("500.00"),
                LocalDate.of(2026, 2, 10),
                LocalDate.of(2026, 2, 10),
                null);
        saveTransaction(
                farm,
                user,
                TransactionType.EXPENSE,
                PaymentStatus.PENDING,
                new BigDecimal("400.00"),
                LocalDate.of(2026, 2, 10),
                LocalDate.of(2026, 2, 10),
                null);
        saveTransaction(
                farm,
                user,
                TransactionType.INCOME,
                PaymentStatus.PAID,
                new BigDecimal("50.00"),
                LocalDate.of(2026, 3, 10),
                LocalDate.of(2026, 3, 10),
                LocalDate.of(2026, 3, 10));
        saveTransaction(
                farm,
                user,
                TransactionType.EXPENSE,
                PaymentStatus.PAID,
                new BigDecimal("40.00"),
                LocalDate.of(2026, 3, 10),
                LocalDate.of(2026, 3, 10),
                LocalDate.of(2026, 3, 10));
        saveTransaction(
                farm,
                user,
                TransactionType.INCOME,
                PaymentStatus.PAID,
                new BigDecimal("999.00"),
                LocalDate.of(2026, 3, 20),
                LocalDate.of(2026, 3, 20),
                LocalDate.of(2026, 3, 20));

        FinancialReportResponse completePeriod =
                financialReportService.getReport(
                        new FinancialReportFilter(
                                farm.getId(),
                                LocalDate.of(2026, 1, 1),
                                LocalDate.of(2026, 3, 15),
                                FinancialReportBasis.CASH,
                                null,
                                null,
                                FinancialReportGranularity.MONTHLY));

        assertThat(completePeriod.realizedCumulativeEvolution())
                .extracting(point -> point.cumulativeIncome())
                .containsExactly(
                        new BigDecimal("100.00"),
                        new BigDecimal("100.00"),
                        new BigDecimal("150.00"));
        assertThat(completePeriod.realizedCumulativeEvolution())
                .extracting(point -> point.cumulativeExpense())
                .containsExactly(
                        new BigDecimal("60.00"), new BigDecimal("60.00"), new BigDecimal("100.00"));

        FinancialReportResponse restartedPeriod =
                financialReportService.getReport(
                        new FinancialReportFilter(
                                farm.getId(),
                                LocalDate.of(2026, 3, 1),
                                LocalDate.of(2026, 3, 15),
                                FinancialReportBasis.CASH,
                                null,
                                null,
                                FinancialReportGranularity.MONTHLY));

        assertThat(restartedPeriod.realizedCumulativeEvolution()).hasSize(1);
        assertThat(restartedPeriod.realizedCumulativeEvolution().getFirst().cumulativeIncome())
                .isEqualByComparingTo("50.00");
        assertThat(restartedPeriod.realizedCumulativeEvolution().getFirst().cumulativeExpense())
                .isEqualByComparingTo("40.00");
    }

    @Test
    void shouldBuildCategoryRankingsByTypeWithinTheFilteredHarvestAndCategory() {
        Farm farm = saveFarm();
        User user = saveUser();
        FinancialCategory supplies = saveCategory(farm, "Insumos", TransactionType.EXPENSE);
        FinancialCategory seeds = saveCategory(farm, "Sementes", TransactionType.EXPENSE);
        FinancialCategory sale = saveCategory(farm, "Venda de produção", TransactionType.INCOME);
        HarvestSeason soybean = saveHarvest(farm, "Soja");
        HarvestSeason corn = saveHarvest(farm, "Milho");

        saveTransaction(
                farm, user, supplies, soybean, TransactionType.EXPENSE, new BigDecimal("40150.00"));
        saveTransaction(
                farm, user, seeds, soybean, TransactionType.EXPENSE, new BigDecimal("34900.00"));
        saveTransaction(
                farm, user, sale, soybean, TransactionType.INCOME, new BigDecimal("211500.00"));
        saveTransaction(
                farm, user, supplies, corn, TransactionType.EXPENSE, new BigDecimal("999.00"));

        FinancialReportResponse harvestReport =
                financialReportService.getReport(
                        new FinancialReportFilter(
                                farm.getId(),
                                LocalDate.of(2026, 1, 1),
                                LocalDate.of(2026, 1, 31),
                                FinancialReportBasis.ACCRUAL,
                                java.util.List.of(soybean.getId()),
                                null));

        assertThat(harvestReport.categories()).hasSize(2);
        assertThat(harvestReport.categories().getFirst().type()).isEqualTo(TransactionType.EXPENSE);
        assertThat(harvestReport.categories().getFirst().totalAmount())
                .isEqualByComparingTo("75050.00");
        assertThat(harvestReport.categories().getFirst().totalTransactionCount()).isEqualTo(2);
        assertThat(harvestReport.categories().getFirst().items())
                .extracting(item -> item.categoryName())
                .containsExactly("Insumos", "Sementes");
        assertThat(harvestReport.categories().getFirst().items())
                .extracting(item -> item.percentage())
                .containsExactly(new BigDecimal("53.50"), new BigDecimal("46.50"));
        assertThat(harvestReport.categories().get(1).type()).isEqualTo(TransactionType.INCOME);
        assertThat(harvestReport.categories().get(1).totalAmount())
                .isEqualByComparingTo("211500.00");
        assertThat(harvestReport.categories().get(1).items()).hasSize(1);
        assertThat(harvestReport.financialIndicators().result().totalExpense())
                .isEqualByComparingTo("75050.00");
        assertThat(harvestReport.financialIndicators().result().totalIncome())
                .isEqualByComparingTo("211500.00");

        FinancialReportResponse categoryReport =
                financialReportService.getReport(
                        new FinancialReportFilter(
                                farm.getId(),
                                LocalDate.of(2026, 1, 1),
                                LocalDate.of(2026, 1, 31),
                                FinancialReportBasis.ACCRUAL,
                                java.util.List.of(soybean.getId()),
                                java.util.List.of(supplies.getId())));

        assertThat(categoryReport.categories().getFirst().totalAmount())
                .isEqualByComparingTo("40150.00");
        assertThat(categoryReport.categories().getFirst().items()).hasSize(1);
        assertThat(categoryReport.categories().getFirst().items().getFirst().percentage())
                .isEqualByComparingTo("100.00");
        assertThat(categoryReport.categories().get(1).totalAmount()).isZero();
        assertThat(categoryReport.categories().get(1).items()).isEmpty();
        assertThat(categoryReport.financialIndicators().result().totalExpense())
                .isEqualByComparingTo("40150.00");
        assertThat(categoryReport.financialIndicators().result().totalIncome()).isZero();
    }

    private FinancialReportResponse reportForEndDate(Farm farm, LocalDate endDate) {
        return financialReportService.getReport(
                new FinancialReportFilter(
                        farm.getId(), TODAY, endDate, FinancialReportBasis.ACCRUAL, null, null));
    }

    private Farm saveFarm() {
        return saveFarm("Farm");
    }

    private Farm saveFarm(String name) {
        Farm farm = new Farm();
        farm.setName(name);
        farm.setStatus(FarmStatus.ACTIVE);
        return farmRepository.save(farm);
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

    private FinancialCategory saveCategory(Farm farm, String name, TransactionType type) {
        FinancialCategory category = new FinancialCategory();
        category.setFarm(farm);
        category.setName(name);
        category.setType(type);
        category.setStatus(FinancialCategoryStatus.ACTIVE);
        return financialCategoryRepository.save(category);
    }

    private HarvestSeason saveHarvest(Farm farm, String name) {
        ProductionActivity activity = new ProductionActivity();
        activity.setFarm(farm);
        activity.setName(name + " activity");
        activity.setStatus(ProductionActivityStatus.ACTIVE);
        ProductionActivity savedActivity = productionActivityRepository.save(activity);
        HarvestSeason harvest = new HarvestSeason();
        harvest.setFarm(farm);
        harvest.setProductionActivity(savedActivity);
        harvest.setName(name);
        harvest.setStartDate(LocalDate.of(2026, 1, 1));
        harvest.setStatus(HarvestSeasonStatus.IN_PROGRESS);
        return harvestSeasonRepository.save(harvest);
    }

    private HarvestSeasonBudgetItem saveBudgetItem(
            HarvestSeason harvest,
            FinancialCategory category,
            TransactionType type,
            String amount) {
        HarvestSeasonBudgetItem item = new HarvestSeasonBudgetItem();
        item.setHarvestSeason(harvest);
        item.setCategory(category);
        item.setType(type);
        item.setDescription("Planning");
        item.setPlannedAmount(new BigDecimal(amount));
        return harvestSeasonBudgetItemRepository.save(item);
    }

    private void saveTransaction(
            Farm farm,
            User user,
            TransactionType type,
            PaymentStatus status,
            BigDecimal amount,
            LocalDate transactionDate,
            LocalDate dueDate,
            LocalDate paidAt) {
        FinancialTransaction transaction = new FinancialTransaction();
        transaction.setDescription("Transaction");
        transaction.setAmount(amount);
        transaction.setType(type);
        transaction.setStatus(status);
        transaction.setTransactionDate(transactionDate);
        transaction.setDueDate(dueDate);
        transaction.setPaidAt(paidAt);
        transaction.setFarm(farm);
        transaction.setCreatedByUser(user);
        transaction.setRecordStatus(FinancialRecordStatus.ACTIVE);
        financialTransactionRepository.save(transaction);
    }

    private void saveTransaction(
            Farm farm,
            User user,
            FinancialCategory category,
            HarvestSeason harvest,
            TransactionType type,
            BigDecimal amount,
            LocalDate transactionDate) {
        FinancialTransaction transaction = new FinancialTransaction();
        transaction.setDescription("Filtered evolution transaction");
        transaction.setAmount(amount);
        transaction.setType(type);
        transaction.setStatus(PaymentStatus.PAID);
        transaction.setTransactionDate(transactionDate);
        transaction.setDueDate(transactionDate);
        transaction.setPaidAt(transactionDate);
        transaction.setFarm(farm);
        transaction.setCategory(category);
        transaction.setHarvestSeason(harvest);
        transaction.setCreatedByUser(user);
        transaction.setRecordStatus(FinancialRecordStatus.ACTIVE);
        financialTransactionRepository.save(transaction);
    }

    private void saveTransaction(
            Farm farm,
            User user,
            FinancialCategory category,
            HarvestSeason harvest,
            TransactionType type,
            BigDecimal amount) {
        FinancialTransaction transaction = new FinancialTransaction();
        transaction.setDescription("Category transaction");
        transaction.setAmount(amount);
        transaction.setType(type);
        transaction.setStatus(PaymentStatus.PAID);
        transaction.setTransactionDate(LocalDate.of(2026, 1, 10));
        transaction.setDueDate(LocalDate.of(2026, 1, 10));
        transaction.setPaidAt(LocalDate.of(2026, 1, 10));
        transaction.setFarm(farm);
        transaction.setCategory(category);
        transaction.setHarvestSeason(harvest);
        transaction.setCreatedByUser(user);
        transaction.setRecordStatus(FinancialRecordStatus.ACTIVE);
        financialTransactionRepository.save(transaction);
    }

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(
                    Instant.parse("2026-08-30T03:00:00Z"), ZoneId.of("America/Sao_Paulo"));
        }
    }
}
