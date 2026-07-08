package br.com.gestaodireta.financial.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.gestaodireta.farm.entity.Farm;
import br.com.gestaodireta.farm.enumeration.FarmStatus;
import br.com.gestaodireta.farm.repository.FarmRepository;
import br.com.gestaodireta.financial.dto.FinancialAgendaFilter;
import br.com.gestaodireta.financial.dto.FinancialAgendaItemResponse;
import br.com.gestaodireta.financial.dto.FinancialAgendaSummaryResponse;
import br.com.gestaodireta.financial.entity.FinancialCategory;
import br.com.gestaodireta.financial.entity.FinancialTransaction;
import br.com.gestaodireta.financial.enumeration.FinancialAgendaStatus;
import br.com.gestaodireta.financial.enumeration.FinancialAgendaStatusFilter;
import br.com.gestaodireta.financial.enumeration.FinancialAgendaType;
import br.com.gestaodireta.financial.enumeration.FinancialAgendaTypeFilter;
import br.com.gestaodireta.financial.enumeration.FinancialCategoryStatus;
import br.com.gestaodireta.financial.enumeration.FinancialRecordStatus;
import br.com.gestaodireta.financial.enumeration.PaymentStatus;
import br.com.gestaodireta.financial.enumeration.TransactionType;
import br.com.gestaodireta.financial.repository.FinancialCategoryRepository;
import br.com.gestaodireta.financial.repository.FinancialTransactionRepository;
import br.com.gestaodireta.harvest.entity.HarvestSeason;
import br.com.gestaodireta.harvest.entity.ProductionActivity;
import br.com.gestaodireta.harvest.enumeration.HarvestSeasonStatus;
import br.com.gestaodireta.harvest.enumeration.ProductionActivityStatus;
import br.com.gestaodireta.harvest.repository.HarvestSeasonRepository;
import br.com.gestaodireta.harvest.repository.ProductionActivityRepository;
import br.com.gestaodireta.shared.exception.BusinessException;
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
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest
class FinancialAgendaServiceTest extends PostgresIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 7, 7);

    @Autowired private FinancialAgendaService financialAgendaService;

    @Autowired private FinancialTransactionRepository financialTransactionRepository;

    @Autowired private FinancialCategoryRepository financialCategoryRepository;

    @Autowired private HarvestSeasonRepository harvestSeasonRepository;

    @Autowired private ProductionActivityRepository productionActivityRepository;

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
        farmRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void shouldSummarizeAllOpenTransactionsIgnoringPaidCanceledDeletedNoDueDateAndOtherFarm() {
        AgendaScenario scenario = saveAgendaScenario();

        FinancialAgendaSummaryResponse response =
                financialAgendaService.summarize(filter(scenario.farm.getId()));

        assertThat(response.farmId()).isEqualTo(scenario.farm.getId());
        assertThat(response.overdueReceivable().count()).isEqualTo(2);
        assertThat(response.overdueReceivable().totalAmount()).isEqualByComparingTo("3000.00");
        assertThat(response.pendingReceivable().count()).isEqualTo(2);
        assertThat(response.pendingReceivable().totalAmount()).isEqualByComparingTo("7000.00");
        assertThat(response.openReceivable().count()).isEqualTo(4);
        assertThat(response.openReceivable().totalAmount()).isEqualByComparingTo("10000.00");
        assertThat(response.overduePayable().count()).isEqualTo(2);
        assertThat(response.overduePayable().totalAmount()).isEqualByComparingTo("1100.00");
        assertThat(response.pendingPayable().count()).isEqualTo(2);
        assertThat(response.pendingPayable().totalAmount()).isEqualByComparingTo("1500.00");
        assertThat(response.openPayable().count()).isEqualTo(4);
        assertThat(response.openPayable().totalAmount()).isEqualByComparingTo("2600.00");
    }

    @Test
    void shouldApplyStatusTypeAndPeriodFiltersToSummary() {
        AgendaScenario scenario = saveAgendaScenario();

        FinancialAgendaSummaryResponse pending =
                financialAgendaService.summarize(
                        filter(
                                scenario.farm.getId(),
                                FinancialAgendaStatusFilter.PENDING,
                                FinancialAgendaTypeFilter.ALL,
                                null,
                                null));
        assertThat(pending.overdueReceivable().count()).isZero();
        assertThat(pending.overduePayable().count()).isZero();
        assertThat(pending.pendingReceivable().totalAmount()).isEqualByComparingTo("7000.00");
        assertThat(pending.pendingPayable().totalAmount()).isEqualByComparingTo("1500.00");

        FinancialAgendaSummaryResponse overdue =
                financialAgendaService.summarize(
                        filter(
                                scenario.farm.getId(),
                                FinancialAgendaStatusFilter.OVERDUE,
                                FinancialAgendaTypeFilter.ALL,
                                null,
                                null));
        assertThat(overdue.pendingReceivable().count()).isZero();
        assertThat(overdue.pendingPayable().count()).isZero();
        assertThat(overdue.overdueReceivable().totalAmount()).isEqualByComparingTo("3000.00");
        assertThat(overdue.overduePayable().totalAmount()).isEqualByComparingTo("1100.00");

        FinancialAgendaSummaryResponse receivable =
                financialAgendaService.summarize(
                        filter(
                                scenario.farm.getId(),
                                FinancialAgendaStatusFilter.ALL,
                                FinancialAgendaTypeFilter.RECEIVABLE,
                                null,
                                null));
        assertThat(receivable.openReceivable().totalAmount()).isEqualByComparingTo("10000.00");
        assertThat(receivable.openPayable().count()).isZero();
        assertThat(receivable.openPayable().totalAmount()).isEqualByComparingTo(BigDecimal.ZERO);

        FinancialAgendaSummaryResponse payable =
                financialAgendaService.summarize(
                        filter(
                                scenario.farm.getId(),
                                FinancialAgendaStatusFilter.ALL,
                                FinancialAgendaTypeFilter.PAYABLE,
                                null,
                                null));
        assertThat(payable.openReceivable().count()).isZero();
        assertThat(payable.openPayable().totalAmount()).isEqualByComparingTo("2600.00");

        FinancialAgendaSummaryResponse next7Days =
                financialAgendaService.summarize(
                        filter(
                                scenario.farm.getId(),
                                FinancialAgendaStatusFilter.ALL,
                                FinancialAgendaTypeFilter.ALL,
                                7,
                                null));
        assertThat(next7Days.openReceivable().totalAmount()).isEqualByComparingTo("6000.00");
        assertThat(next7Days.openPayable().totalAmount()).isEqualByComparingTo("1800.00");

        FinancialAgendaSummaryResponse pendingNext7Days =
                financialAgendaService.summarize(
                        filter(
                                scenario.farm.getId(),
                                FinancialAgendaStatusFilter.PENDING,
                                FinancialAgendaTypeFilter.ALL,
                                7,
                                null));
        assertThat(pendingNext7Days.openReceivable().totalAmount()).isEqualByComparingTo("3000.00");
        assertThat(pendingNext7Days.openPayable().totalAmount()).isEqualByComparingTo("700.00");

        FinancialAgendaSummaryResponse overdueWithPeriod =
                financialAgendaService.summarize(
                        filter(
                                scenario.farm.getId(),
                                FinancialAgendaStatusFilter.OVERDUE,
                                FinancialAgendaTypeFilter.ALL,
                                7,
                                null));
        assertThat(overdueWithPeriod.openReceivable().totalAmount())
                .isEqualByComparingTo("3000.00");
        assertThat(overdueWithPeriod.openPayable().totalAmount()).isEqualByComparingTo("1100.00");
    }

    @Test
    void shouldFilterByMultipleHarvestSeasonsAndRejectSeasonFromAnotherFarm() {
        AgendaScenario scenario = saveAgendaScenario();

        FinancialAgendaSummaryResponse response =
                financialAgendaService.summarize(
                        filter(
                                scenario.farm.getId(),
                                FinancialAgendaStatusFilter.ALL,
                                FinancialAgendaTypeFilter.ALL,
                                null,
                                List.of(
                                        scenario.firstSeason.getId(),
                                        scenario.secondSeason.getId())));

        assertThat(response.openReceivable().totalAmount()).isEqualByComparingTo("10000.00");
        assertThat(response.openPayable().totalAmount()).isEqualByComparingTo("2600.00");

        assertThatThrownBy(
                        () ->
                                financialAgendaService.summarize(
                                        filter(
                                                scenario.farm.getId(),
                                                FinancialAgendaStatusFilter.ALL,
                                                FinancialAgendaTypeFilter.ALL,
                                                null,
                                                List.of(scenario.otherFarmSeason.getId()))))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Harvest season does not belong to farm");
    }

    @Test
    void shouldListAgendaItemsWithCalculatedFieldsSortingPaginationAndFilters() {
        AgendaScenario scenario = saveAgendaScenario();

        PageResponse<FinancialAgendaItemResponse> firstPage =
                financialAgendaService.findAll(
                        filter(
                                scenario.farm.getId(),
                                FinancialAgendaStatusFilter.ALL,
                                FinancialAgendaTypeFilter.ALL,
                                null,
                                null,
                                PageRequest.of(0, 3)));

        assertThat(firstPage.totalElements()).isEqualTo(8);
        assertThat(firstPage.content()).hasSize(3);
        assertThat(firstPage.content())
                .extracting(FinancialAgendaItemResponse::dueDate)
                .containsExactly(TODAY.minusDays(1), TODAY.minusDays(1), TODAY.minusDays(1));
        assertThat(firstPage.content())
                .extracting(FinancialAgendaItemResponse::agendaStatus)
                .containsOnly(FinancialAgendaStatus.OVERDUE);

        FinancialAgendaItemResponse overduePendingIncome =
                firstPage.content().stream()
                        .filter(item -> item.amount().compareTo(new BigDecimal("2000.00")) == 0)
                        .findFirst()
                        .orElseThrow();
        assertThat(overduePendingIncome.agendaType()).isEqualTo(FinancialAgendaType.RECEIVABLE);
        assertThat(overduePendingIncome.transactionType()).isEqualTo(TransactionType.INCOME);
        assertThat(overduePendingIncome.paymentStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(overduePendingIncome.daysOverdue()).isEqualTo(1);
        assertThat(overduePendingIncome.daysUntilDue()).isNull();
        assertThat(overduePendingIncome.categoryName()).isEqualTo("Income Category");
        assertThat(overduePendingIncome.harvestSeasonName()).isEqualTo("First Season");

        PageResponse<FinancialAgendaItemResponse> secondPage =
                financialAgendaService.findAll(
                        filter(
                                scenario.farm.getId(),
                                FinancialAgendaStatusFilter.ALL,
                                FinancialAgendaTypeFilter.ALL,
                                null,
                                null,
                                PageRequest.of(1, 3)));
        assertThat(secondPage.content()).hasSize(3);
        assertThat(secondPage.content().get(1).dueDate()).isEqualTo(TODAY);
        assertThat(secondPage.content().get(1).agendaStatus())
                .isEqualTo(FinancialAgendaStatus.PENDING);
        assertThat(secondPage.content().get(1).daysUntilDue()).isZero();

        PageResponse<FinancialAgendaItemResponse> payableOverdue =
                financialAgendaService.findAll(
                        filter(
                                scenario.farm.getId(),
                                FinancialAgendaStatusFilter.OVERDUE,
                                FinancialAgendaTypeFilter.PAYABLE,
                                null,
                                null,
                                PageRequest.of(0, 20)));
        assertThat(payableOverdue.content()).hasSize(2);
        assertThat(payableOverdue.content())
                .extracting(FinancialAgendaItemResponse::agendaType)
                .containsOnly(FinancialAgendaType.PAYABLE);

        PageResponse<FinancialAgendaItemResponse> pendingNext7Days =
                financialAgendaService.findAll(
                        filter(
                                scenario.farm.getId(),
                                FinancialAgendaStatusFilter.PENDING,
                                FinancialAgendaTypeFilter.ALL,
                                7,
                                List.of(
                                        scenario.firstSeason.getId(),
                                        scenario.secondSeason.getId()),
                                PageRequest.of(0, 20)));
        assertThat(pendingNext7Days.content())
                .extracting(FinancialAgendaItemResponse::amount)
                .containsExactlyInAnyOrder(new BigDecimal("3000.00"), new BigDecimal("700.00"));
    }

    @Test
    void shouldRejectInvalidPeriodDays() {
        Farm farm = saveFarm("Farm");

        assertThatThrownBy(
                        () ->
                                financialAgendaService.summarize(
                                        filter(
                                                farm.getId(),
                                                FinancialAgendaStatusFilter.ALL,
                                                FinancialAgendaTypeFilter.ALL,
                                                0,
                                                null)))
                .isInstanceOf(br.com.gestaodireta.shared.exception.ValidationException.class)
                .hasMessage("Period days must be greater than zero");
    }

    private AgendaScenario saveAgendaScenario() {
        Farm farm = saveFarm("Farm");
        Farm otherFarm = saveFarm("Other Farm");
        User user = saveUser();
        FinancialCategory incomeCategory =
                saveCategory(farm, "Income Category", TransactionType.INCOME);
        FinancialCategory expenseCategory =
                saveCategory(farm, "Expense Category", TransactionType.EXPENSE);
        HarvestSeason firstSeason = saveSeason(farm, "First Season");
        HarvestSeason secondSeason = saveSeason(farm, "Second Season");
        HarvestSeason otherFarmSeason = saveSeason(otherFarm, "Other Farm Season");

        saveTransaction(
                farm,
                user,
                incomeCategory,
                firstSeason,
                TransactionType.INCOME,
                PaymentStatus.OVERDUE,
                "1000.00",
                TODAY.minusDays(1),
                FinancialRecordStatus.ACTIVE);
        saveTransaction(
                farm,
                user,
                incomeCategory,
                firstSeason,
                TransactionType.INCOME,
                PaymentStatus.PENDING,
                "2000.00",
                TODAY.minusDays(1),
                FinancialRecordStatus.ACTIVE);
        saveTransaction(
                farm,
                user,
                incomeCategory,
                firstSeason,
                TransactionType.INCOME,
                PaymentStatus.PENDING,
                "3000.00",
                TODAY,
                FinancialRecordStatus.ACTIVE);
        saveTransaction(
                farm,
                user,
                incomeCategory,
                secondSeason,
                TransactionType.INCOME,
                PaymentStatus.PENDING,
                "4000.00",
                TODAY.plusDays(10),
                FinancialRecordStatus.ACTIVE);
        saveTransaction(
                farm,
                user,
                expenseCategory,
                firstSeason,
                TransactionType.EXPENSE,
                PaymentStatus.OVERDUE,
                "500.00",
                TODAY.minusDays(1),
                FinancialRecordStatus.ACTIVE);
        saveTransaction(
                farm,
                user,
                expenseCategory,
                firstSeason,
                TransactionType.EXPENSE,
                PaymentStatus.PENDING,
                "600.00",
                TODAY.minusDays(1),
                FinancialRecordStatus.ACTIVE);
        saveTransaction(
                farm,
                user,
                expenseCategory,
                firstSeason,
                TransactionType.EXPENSE,
                PaymentStatus.PENDING,
                "700.00",
                TODAY,
                FinancialRecordStatus.ACTIVE);
        saveTransaction(
                farm,
                user,
                expenseCategory,
                secondSeason,
                TransactionType.EXPENSE,
                PaymentStatus.PENDING,
                "800.00",
                TODAY.plusDays(10),
                FinancialRecordStatus.ACTIVE);
        saveTransaction(
                farm,
                user,
                incomeCategory,
                firstSeason,
                TransactionType.INCOME,
                PaymentStatus.PAID,
                "9999.00",
                TODAY,
                FinancialRecordStatus.ACTIVE);
        saveTransaction(
                farm,
                user,
                expenseCategory,
                firstSeason,
                TransactionType.EXPENSE,
                PaymentStatus.CANCELED,
                "9999.00",
                TODAY,
                FinancialRecordStatus.ACTIVE);
        saveTransaction(
                farm,
                user,
                incomeCategory,
                firstSeason,
                TransactionType.INCOME,
                PaymentStatus.PENDING,
                "9999.00",
                TODAY,
                FinancialRecordStatus.DELETED);
        saveTransaction(
                farm,
                user,
                incomeCategory,
                firstSeason,
                TransactionType.INCOME,
                PaymentStatus.PENDING,
                "9999.00",
                null,
                FinancialRecordStatus.ACTIVE);
        saveTransaction(
                otherFarm,
                user,
                null,
                otherFarmSeason,
                TransactionType.INCOME,
                PaymentStatus.PENDING,
                "9999.00",
                TODAY,
                FinancialRecordStatus.ACTIVE);

        return new AgendaScenario(farm, firstSeason, secondSeason, otherFarmSeason);
    }

    private FinancialAgendaFilter filter(Long farmId) {
        return filter(
                farmId, FinancialAgendaStatusFilter.ALL, FinancialAgendaTypeFilter.ALL, null, null);
    }

    private FinancialAgendaFilter filter(
            Long farmId,
            FinancialAgendaStatusFilter status,
            FinancialAgendaTypeFilter type,
            Integer periodDays,
            List<Long> harvestSeasonIds) {
        return filter(farmId, status, type, periodDays, harvestSeasonIds, PageRequest.of(0, 20));
    }

    private FinancialAgendaFilter filter(
            Long farmId,
            FinancialAgendaStatusFilter status,
            FinancialAgendaTypeFilter type,
            Integer periodDays,
            List<Long> harvestSeasonIds,
            PageRequest pageable) {
        return new FinancialAgendaFilter(
                farmId, status, type, periodDays, harvestSeasonIds, pageable);
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
        user.setEmail("agenda-user@example.com");
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

    private HarvestSeason saveSeason(Farm farm, String name) {
        ProductionActivity activity = new ProductionActivity();
        activity.setFarm(farm);
        activity.setName(name + " Activity");
        activity.setStatus(ProductionActivityStatus.ACTIVE);
        productionActivityRepository.save(activity);

        HarvestSeason season = new HarvestSeason();
        season.setFarm(farm);
        season.setProductionActivity(activity);
        season.setName(name);
        season.setStartDate(TODAY.minusMonths(1));
        season.setStatus(HarvestSeasonStatus.PLANNED);

        return harvestSeasonRepository.save(season);
    }

    private FinancialTransaction saveTransaction(
            Farm farm,
            User user,
            FinancialCategory category,
            HarvestSeason harvestSeason,
            TransactionType type,
            PaymentStatus status,
            String amount,
            LocalDate dueDate,
            FinancialRecordStatus recordStatus) {
        FinancialTransaction transaction = new FinancialTransaction();
        transaction.setDescription("Transaction " + amount);
        transaction.setAmount(new BigDecimal(amount));
        transaction.setType(type);
        transaction.setStatus(status);
        transaction.setTransactionDate(dueDate == null ? TODAY : dueDate);
        transaction.setDueDate(dueDate);
        transaction.setPaidAt(
                PaymentStatus.PAID.equals(status) ? transaction.getTransactionDate() : null);
        transaction.setFarm(farm);
        transaction.setCategory(category);
        transaction.setHarvestSeason(harvestSeason);
        transaction.setCreatedByUser(user);
        transaction.setRecordStatus(recordStatus);

        return financialTransactionRepository.save(transaction);
    }

    private record AgendaScenario(
            Farm farm,
            HarvestSeason firstSeason,
            HarvestSeason secondSeason,
            HarvestSeason otherFarmSeason) {}

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(
                    Instant.parse("2026-07-07T03:00:00Z"), ZoneId.of("America/Sao_Paulo"));
        }
    }
}
