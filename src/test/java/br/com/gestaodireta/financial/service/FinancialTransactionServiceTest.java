package br.com.gestaodireta.financial.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.gestaodireta.farm.entity.Farm;
import br.com.gestaodireta.farm.enumeration.FarmStatus;
import br.com.gestaodireta.farm.repository.FarmRepository;
import br.com.gestaodireta.farm.repository.FarmUserRepository;
import br.com.gestaodireta.financial.dto.FinancialTransactionFilterRequest;
import br.com.gestaodireta.financial.dto.FinancialTransactionRequest;
import br.com.gestaodireta.financial.dto.FinancialTransactionResponse;
import br.com.gestaodireta.financial.dto.FinancialTransactionUpdateRequest;
import br.com.gestaodireta.financial.entity.FinancialCategory;
import br.com.gestaodireta.financial.entity.FinancialTransaction;
import br.com.gestaodireta.financial.enumeration.FinancialCategoryStatus;
import br.com.gestaodireta.financial.enumeration.FinancialRecordStatus;
import br.com.gestaodireta.financial.enumeration.PaymentMethod;
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
import br.com.gestaodireta.shared.exception.ResourceNotFoundException;
import br.com.gestaodireta.shared.exception.ValidationException;
import br.com.gestaodireta.shared.pagination.PaginationParams;
import br.com.gestaodireta.support.PostgresIntegrationTest;
import br.com.gestaodireta.user.entity.User;
import br.com.gestaodireta.user.enumeration.UserStatus;
import br.com.gestaodireta.user.enumeration.UserType;
import br.com.gestaodireta.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest
class FinancialTransactionServiceTest extends PostgresIntegrationTest {

    @Autowired private FinancialTransactionService financialTransactionService;

    @Autowired private FinancialTransactionRepository financialTransactionRepository;

    @Autowired private FinancialCategoryRepository financialCategoryRepository;

    @Autowired private FarmUserRepository farmUserRepository;

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
        farmUserRepository.deleteAll();
        farmRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void shouldCreateTransactionWithAuthenticatedUser() {
        User admin = saveUser("Admin", "admin@example.com", UserType.ADMIN);
        Farm farm = saveFarm("Farm", FarmStatus.ACTIVE);
        authenticateAs(admin, "ROLE_ADMIN");

        FinancialTransactionResponse response =
                financialTransactionService.create(transactionRequest(farm, null, BigDecimal.TEN));

        assertThat(response.createdByUserId()).isEqualTo(admin.getId());
        assertThat(response.recordStatus()).isEqualTo(FinancialRecordStatus.ACTIVE);
        assertThat(response.status()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void shouldFillUpdatedByUserWhenUpdatingTransaction() {
        User creator = saveUser("Creator", "creator@example.com", UserType.USER);
        User updater = saveUser("Updater", "updater@example.com", UserType.USER);
        Farm farm = saveFarm("Farm", FarmStatus.ACTIVE);
        FinancialTransaction transaction =
                saveTransaction(farm, null, creator, TransactionType.EXPENSE);
        authenticateAs(updater, "ROLE_USER");

        FinancialTransactionResponse response =
                financialTransactionService.update(
                        transaction.getId(),
                        updateRequest(null, new BigDecimal("50.00"), TransactionType.EXPENSE));

        assertThat(response.updatedByUserId()).isEqualTo(updater.getId());
        assertThat(response.amount()).isEqualByComparingTo("50.00");
    }

    @Test
    void shouldRejectInvalidAmountMismatchedCategoryAndInactiveFarm() {
        User admin = saveUser("Admin", "admin@example.com", UserType.ADMIN);
        Farm activeFarm = saveFarm("Active Farm", FarmStatus.ACTIVE);
        Farm inactiveFarm = saveFarm("Inactive Farm", FarmStatus.INACTIVE);
        FinancialCategory incomeCategory =
                saveCategory("Income", activeFarm, TransactionType.INCOME, false);
        authenticateAs(admin, "ROLE_ADMIN");

        assertThatThrownBy(
                        () ->
                                financialTransactionService.create(
                                        transactionRequest(activeFarm, null, BigDecimal.ZERO)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Transaction amount must be greater than zero");

        assertThatThrownBy(
                        () ->
                                financialTransactionService.create(
                                        transactionRequest(
                                                activeFarm, incomeCategory, BigDecimal.TEN)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Financial category type must match transaction type");

        assertThatThrownBy(
                        () ->
                                financialTransactionService.create(
                                        transactionRequest(inactiveFarm, null, BigDecimal.TEN)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Inactive farm cannot receive financial transactions");
    }

    @Test
    void shouldRejectInactiveCategoryWhenCreatingOrUpdatingTransaction() {
        User admin = saveUser("Admin", "admin@example.com", UserType.ADMIN);
        Farm farm = saveFarm("Farm", FarmStatus.ACTIVE);
        FinancialCategory inactiveCategory =
                saveCategory(
                        "Inactive",
                        farm,
                        TransactionType.EXPENSE,
                        false,
                        FinancialCategoryStatus.INACTIVE);
        FinancialTransaction transaction =
                saveTransaction(farm, null, admin, TransactionType.EXPENSE);
        authenticateAs(admin, "ROLE_ADMIN");

        assertThatThrownBy(
                        () ->
                                financialTransactionService.create(
                                        transactionRequest(farm, inactiveCategory, BigDecimal.TEN)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Financial category is inactive");

        assertThatThrownBy(
                        () ->
                                financialTransactionService.update(
                                        transaction.getId(),
                                        updateRequest(
                                                inactiveCategory,
                                                BigDecimal.TEN,
                                                TransactionType.EXPENSE)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Financial category is inactive");
    }

    @Test
    void shouldCreateTransactionWithOptionalHarvestSeason() {
        User admin = saveUser("Admin", "admin-harvest@example.com", UserType.ADMIN);
        Farm farm = saveFarm("Farm", FarmStatus.ACTIVE);
        HarvestSeason harvestSeason = saveSeason(farm, "Safra Soja", HarvestSeasonStatus.PLANNED);
        authenticateAs(admin, "ROLE_ADMIN");

        FinancialTransactionResponse withoutSeason =
                financialTransactionService.create(transactionRequest(farm, null, BigDecimal.TEN));
        FinancialTransactionResponse withSeason =
                financialTransactionService.create(
                        transactionRequest(farm, null, BigDecimal.TEN, harvestSeason));

        assertThat(withoutSeason.harvestSeasonId()).isNull();
        assertThat(withoutSeason.harvestSeasonName()).isNull();
        assertThat(withSeason.harvestSeasonId()).isEqualTo(harvestSeason.getId());
        assertThat(withSeason.harvestSeasonName()).isEqualTo("Safra Soja");
    }

    @Test
    void shouldRejectInvalidHarvestSeasonWhenCreatingTransaction() {
        User admin = saveUser("Admin", "admin-invalid-harvest@example.com", UserType.ADMIN);
        Farm farm = saveFarm("Farm", FarmStatus.ACTIVE);
        Farm otherFarm = saveFarm("Other Farm", FarmStatus.ACTIVE);
        HarvestSeason otherFarmSeason =
                saveSeason(otherFarm, "Safra Milho", HarvestSeasonStatus.PLANNED);
        HarvestSeason inactiveSeason =
                saveSeason(farm, "Safra Inativa", HarvestSeasonStatus.INACTIVE);
        authenticateAs(admin, "ROLE_ADMIN");

        assertThatThrownBy(
                        () ->
                                financialTransactionService.create(
                                        transactionRequestWithHarvestSeasonId(farm, 999999L)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Harvest season not found");

        assertThatThrownBy(
                        () ->
                                financialTransactionService.create(
                                        transactionRequest(
                                                farm, null, BigDecimal.TEN, otherFarmSeason)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Harvest season does not belong to transaction farm");

        assertThatThrownBy(
                        () ->
                                financialTransactionService.create(
                                        transactionRequest(
                                                farm, null, BigDecimal.TEN, inactiveSeason)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Inactive harvest season cannot be linked to financial transaction");
    }

    @Test
    void shouldUpdateTransactionHarvestSeason() {
        User admin = saveUser("Admin", "admin-update-harvest@example.com", UserType.ADMIN);
        Farm farm = saveFarm("Farm", FarmStatus.ACTIVE);
        HarvestSeason firstSeason = saveSeason(farm, "Safra Soja", HarvestSeasonStatus.PLANNED);
        HarvestSeason secondSeason = saveSeason(farm, "Safra Milho", HarvestSeasonStatus.PLANNED);
        FinancialTransaction transaction =
                saveTransaction(farm, null, admin, TransactionType.EXPENSE, firstSeason);
        authenticateAs(admin, "ROLE_ADMIN");

        FinancialTransactionResponse keptSeason =
                financialTransactionService.update(
                        transaction.getId(),
                        updateRequest(null, BigDecimal.TEN, TransactionType.EXPENSE, firstSeason));
        FinancialTransactionResponse changedSeason =
                financialTransactionService.update(
                        transaction.getId(),
                        updateRequest(null, BigDecimal.TEN, TransactionType.EXPENSE, secondSeason));
        FinancialTransactionResponse removedSeason =
                financialTransactionService.update(
                        transaction.getId(),
                        updateRequest(null, BigDecimal.TEN, TransactionType.EXPENSE));

        assertThat(keptSeason.harvestSeasonId()).isEqualTo(firstSeason.getId());
        assertThat(changedSeason.harvestSeasonId()).isEqualTo(secondSeason.getId());
        assertThat(removedSeason.harvestSeasonId()).isNull();
        assertThat(removedSeason.harvestSeasonName()).isNull();
    }

    @Test
    void shouldRejectInvalidHarvestSeasonWhenUpdatingTransaction() {
        User admin = saveUser("Admin", "admin-invalid-update-harvest@example.com", UserType.ADMIN);
        Farm farm = saveFarm("Farm", FarmStatus.ACTIVE);
        Farm otherFarm = saveFarm("Other Farm", FarmStatus.ACTIVE);
        HarvestSeason otherFarmSeason =
                saveSeason(otherFarm, "Safra Milho", HarvestSeasonStatus.PLANNED);
        HarvestSeason inactiveSeason =
                saveSeason(farm, "Safra Inativa", HarvestSeasonStatus.INACTIVE);
        FinancialTransaction transaction =
                saveTransaction(farm, null, admin, TransactionType.EXPENSE);
        authenticateAs(admin, "ROLE_ADMIN");

        assertThatThrownBy(
                        () ->
                                financialTransactionService.update(
                                        transaction.getId(),
                                        updateRequestWithHarvestSeasonId(999999L)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Harvest season not found");

        assertThatThrownBy(
                        () ->
                                financialTransactionService.update(
                                        transaction.getId(),
                                        updateRequest(
                                                null,
                                                BigDecimal.TEN,
                                                TransactionType.EXPENSE,
                                                otherFarmSeason)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Harvest season does not belong to transaction farm");

        assertThatThrownBy(
                        () ->
                                financialTransactionService.update(
                                        transaction.getId(),
                                        updateRequest(
                                                null,
                                                BigDecimal.TEN,
                                                TransactionType.EXPENSE,
                                                inactiveSeason)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Inactive harvest season cannot be linked to financial transaction");
    }

    @Test
    void shouldFilterTransactionsByHarvestSeason() {
        User admin = saveUser("Admin", "admin-filter-harvest@example.com", UserType.ADMIN);
        Farm farm = saveFarm("Farm", FarmStatus.ACTIVE);
        Farm otherFarm = saveFarm("Other Farm", FarmStatus.ACTIVE);
        HarvestSeason firstSeason = saveSeason(farm, "Safra Soja", HarvestSeasonStatus.PLANNED);
        HarvestSeason secondSeason = saveSeason(farm, "Safra Milho", HarvestSeasonStatus.PLANNED);
        HarvestSeason otherFarmSeason =
                saveSeason(otherFarm, "Safra Outra", HarvestSeasonStatus.PLANNED);
        FinancialTransaction firstTransaction =
                saveTransaction(farm, null, admin, TransactionType.EXPENSE, firstSeason);
        FinancialTransaction secondTransaction =
                saveTransaction(farm, null, admin, TransactionType.EXPENSE, secondSeason);
        FinancialTransaction withoutSeason =
                saveTransaction(farm, null, admin, TransactionType.EXPENSE);
        FinancialTransaction otherFarmTransaction =
                saveTransaction(otherFarm, null, admin, TransactionType.EXPENSE, otherFarmSeason);

        List<FinancialTransactionResponse> allTransactions =
                financialTransactionService
                        .findAll(filter(farm.getId()), new PaginationParams())
                        .content();
        List<FinancialTransactionResponse> filteredTransactions =
                financialTransactionService
                        .findAll(
                                filterByHarvestSeason(farm.getId(), firstSeason.getId()),
                                new PaginationParams())
                        .content();

        assertThat(allTransactions)
                .extracting(FinancialTransactionResponse::id)
                .contains(
                        firstTransaction.getId(), secondTransaction.getId(), withoutSeason.getId())
                .doesNotContain(otherFarmTransaction.getId());
        assertThat(filteredTransactions)
                .extracting(FinancialTransactionResponse::id)
                .containsExactly(firstTransaction.getId());
        assertThat(filteredTransactions.getFirst().harvestSeasonId())
                .isEqualTo(firstSeason.getId());
        assertThat(filteredTransactions.getFirst().harvestSeasonName()).isEqualTo("Safra Soja");

        assertThatThrownBy(
                        () ->
                                financialTransactionService.findAll(
                                        filterByHarvestSeason(
                                                farm.getId(), otherFarmSeason.getId()),
                                        new PaginationParams()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Harvest season does not belong to transaction farm");
    }

    @Test
    void shouldShowInactiveHarvestSeasonAlreadyLinkedToTransaction() {
        User admin = saveUser("Admin", "admin-historical-harvest@example.com", UserType.ADMIN);
        Farm farm = saveFarm("Farm", FarmStatus.ACTIVE);
        HarvestSeason inactiveSeason =
                saveSeason(farm, "Safra Histórica", HarvestSeasonStatus.INACTIVE);
        FinancialTransaction transaction =
                saveTransaction(farm, null, admin, TransactionType.EXPENSE, inactiveSeason);

        FinancialTransactionResponse response =
                financialTransactionService.findById(transaction.getId());

        assertThat(response.harvestSeasonId()).isEqualTo(inactiveSeason.getId());
        assertThat(response.harvestSeasonName()).isEqualTo("Safra Histórica");
    }

    @Test
    void shouldDeleteTransactionLogicallyAndHideFromList() {
        User admin = saveUser("Admin", "admin@example.com", UserType.ADMIN);
        Farm farm = saveFarm("Farm", FarmStatus.ACTIVE);
        FinancialTransaction transaction =
                saveTransaction(farm, null, admin, TransactionType.EXPENSE);
        authenticateAs(admin, "ROLE_ADMIN");

        financialTransactionService.delete(transaction.getId());

        FinancialTransaction savedTransaction =
                financialTransactionRepository.findById(transaction.getId()).orElseThrow();
        assertThat(savedTransaction.getRecordStatus()).isEqualTo(FinancialRecordStatus.DELETED);
        assertThat(
                        financialTransactionService
                                .findAll(farm.getId(), new PaginationParams())
                                .content())
                .isEmpty();
    }

    @Test
    void shouldKeepCurrentListBehaviorWithoutExtraFilters() {
        FilterScenario scenario = saveFilterScenario();

        List<Long> ids = findIds(filter(scenario.farm.getId()));

        assertThat(ids)
                .containsExactlyInAnyOrder(
                        scenario.income.getId(), scenario.expense.getId(), scenario.paid.getId());
    }

    @Test
    void shouldFilterByTransactionDateStartEndAndRange() {
        FilterScenario scenario = saveFilterScenario();

        assertThat(
                        findIds(
                                filter(
                                        scenario.farm.getId(),
                                        LocalDate.of(2026, 6, 10),
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null)))
                .containsExactlyInAnyOrder(scenario.expense.getId(), scenario.paid.getId());
        assertThat(
                        findIds(
                                filter(
                                        scenario.farm.getId(),
                                        null,
                                        LocalDate.of(2026, 6, 10),
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null)))
                .containsExactlyInAnyOrder(scenario.income.getId(), scenario.expense.getId());
        assertThat(
                        findIds(
                                filter(
                                        scenario.farm.getId(),
                                        LocalDate.of(2026, 6, 10),
                                        LocalDate.of(2026, 6, 10),
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null)))
                .containsExactly(scenario.expense.getId());
    }

    @Test
    void shouldFilterByPaidAtStartEndAndRange() {
        FilterScenario scenario = saveFilterScenario();

        assertThat(
                        findIds(
                                filter(
                                        scenario.farm.getId(),
                                        null,
                                        null,
                                        LocalDate.of(2026, 6, 18),
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null)))
                .containsExactlyInAnyOrder(scenario.expense.getId(), scenario.paid.getId());
        assertThat(
                        findIds(
                                filter(
                                        scenario.farm.getId(),
                                        null,
                                        null,
                                        null,
                                        LocalDate.of(2026, 6, 18),
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null)))
                .containsExactly(scenario.expense.getId());
        assertThat(
                        findIds(
                                filter(
                                        scenario.farm.getId(),
                                        null,
                                        null,
                                        LocalDate.of(2026, 6, 18),
                                        LocalDate.of(2026, 6, 18),
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null)))
                .containsExactly(scenario.expense.getId());
    }

    @Test
    void shouldFilterByTypeCategoryPaymentStatusPaymentMethodAndRecordStatus() {
        FilterScenario scenario = saveFilterScenario();

        assertThat(
                        findIds(
                                filter(
                                        scenario.farm.getId(),
                                        null,
                                        null,
                                        null,
                                        null,
                                        TransactionType.INCOME,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null)))
                .containsExactly(scenario.income.getId());
        assertThat(
                        findIds(
                                filter(
                                        scenario.farm.getId(),
                                        null,
                                        null,
                                        null,
                                        null,
                                        TransactionType.EXPENSE,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null)))
                .containsExactlyInAnyOrder(scenario.expense.getId(), scenario.paid.getId());
        assertThat(
                        findIds(
                                filter(
                                        scenario.farm.getId(),
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        scenario.expenseCategory.getId(),
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null)))
                .containsExactlyInAnyOrder(scenario.expense.getId(), scenario.paid.getId());
        assertThat(
                        findIds(
                                filter(
                                        scenario.farm.getId(),
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        PaymentStatus.PAID,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null)))
                .containsExactly(scenario.paid.getId());
        assertThat(
                        findIds(
                                filter(
                                        scenario.farm.getId(),
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        PaymentStatus.PENDING,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null)))
                .containsExactlyInAnyOrder(scenario.income.getId(), scenario.expense.getId());
        assertThat(
                        findIds(
                                filter(
                                        scenario.farm.getId(),
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        PaymentMethod.PIX,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null)))
                .containsExactlyInAnyOrder(scenario.income.getId(), scenario.paid.getId());
        assertThat(
                        findIds(
                                filter(
                                        scenario.farm.getId(),
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        FinancialRecordStatus.ACTIVE,
                                        null,
                                        null,
                                        null,
                                        null)))
                .containsExactlyInAnyOrder(
                        scenario.income.getId(), scenario.expense.getId(), scenario.paid.getId());
        assertThat(
                        findIds(
                                filter(
                                        scenario.farm.getId(),
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        FinancialRecordStatus.DELETED,
                                        null,
                                        null,
                                        null,
                                        null)))
                .containsExactly(scenario.deleted.getId());
    }

    @Test
    void shouldPreferPluralCategoryStatusAndPaymentMethodFilters() {
        FilterScenario scenario = saveFilterScenario();

        assertThat(
                        findIds(
                                pluralFilter(
                                        scenario.farm.getId(),
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        scenario.incomeCategory.getId(),
                                        List.of(
                                                scenario.expenseCategory.getId(),
                                                scenario.expenseCategory.getId()),
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null)))
                .containsExactlyInAnyOrder(scenario.expense.getId(), scenario.paid.getId());
        assertThat(
                        findIds(
                                pluralFilter(
                                        scenario.farm.getId(),
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        PaymentStatus.PENDING,
                                        List.of(PaymentStatus.PAID, PaymentStatus.PAID),
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null)))
                .containsExactly(scenario.paid.getId());
        assertThat(
                        findIds(
                                pluralFilter(
                                        scenario.farm.getId(),
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        PaymentMethod.CASH,
                                        List.of(PaymentMethod.PIX, PaymentMethod.PIX),
                                        null,
                                        null,
                                        null,
                                        null,
                                        null)))
                .containsExactlyInAnyOrder(scenario.income.getId(), scenario.paid.getId());
    }

    @Test
    void shouldCombinePluralTransactionFiltersWithDefaultActiveRecordStatus() {
        FilterScenario scenario = saveFilterScenario();

        List<Long> ids =
                findIds(
                        pluralFilter(
                                scenario.farm.getId(),
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                List.of(
                                        scenario.incomeCategory.getId(),
                                        scenario.expenseCategory.getId()),
                                null,
                                List.of(PaymentStatus.PENDING, PaymentStatus.PAID),
                                null,
                                List.of(PaymentMethod.PIX),
                                null,
                                null,
                                null,
                                null,
                                null));

        assertThat(ids).containsExactlyInAnyOrder(scenario.income.getId(), scenario.paid.getId());
        assertThat(ids).doesNotContain(scenario.deleted.getId());
    }

    @Test
    void shouldFilterByDescriptionCreatedByUserAndAmountRange() {
        FilterScenario scenario = saveFilterScenario();

        assertThat(
                        findIds(
                                filter(
                                        scenario.farm.getId(),
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        "  SOJA ",
                                        null,
                                        null,
                                        null)))
                .containsExactlyInAnyOrder(scenario.income.getId(), scenario.expense.getId());
        assertThat(
                        findIds(
                                filter(
                                        scenario.farm.getId(),
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        scenario.otherUser.getId(),
                                        null,
                                        null)))
                .containsExactly(scenario.paid.getId());
        assertThat(
                        findIds(
                                filter(
                                        scenario.farm.getId(),
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        new BigDecimal("100.00"),
                                        null)))
                .containsExactlyInAnyOrder(scenario.expense.getId(), scenario.paid.getId());
        assertThat(
                        findIds(
                                filter(
                                        scenario.farm.getId(),
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        new BigDecimal("100.00"))))
                .containsExactlyInAnyOrder(scenario.income.getId(), scenario.expense.getId());
        assertThat(
                        findIds(
                                filter(
                                        scenario.farm.getId(),
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        new BigDecimal("100.00"),
                                        new BigDecimal("5000.00"))))
                .containsExactlyInAnyOrder(scenario.expense.getId(), scenario.paid.getId());
    }

    @Test
    void shouldCombineFiltersAndNeverReturnTransactionsFromAnotherFarm() {
        FilterScenario scenario = saveFilterScenario();

        List<Long> ids =
                findIds(
                        filter(
                                scenario.farm.getId(),
                                LocalDate.of(2026, 6, 1),
                                LocalDate.of(2026, 6, 30),
                                null,
                                null,
                                TransactionType.EXPENSE,
                                scenario.expenseCategory.getId(),
                                PaymentStatus.PAID,
                                PaymentMethod.PIX,
                                FinancialRecordStatus.ACTIVE,
                                "adubo",
                                scenario.otherUser.getId(),
                                new BigDecimal("100.00"),
                                new BigDecimal("5000.00")));

        assertThat(ids).containsExactly(scenario.paid.getId());
        assertThat(ids).doesNotContain(scenario.otherFarmTransaction.getId());
    }

    @Test
    void shouldRejectInvalidFilterRanges() {
        FilterScenario scenario = saveFilterScenario();

        assertThatThrownBy(
                        () ->
                                financialTransactionService.findAll(
                                        filter(
                                                scenario.farm.getId(),
                                                LocalDate.of(2026, 6, 30),
                                                LocalDate.of(2026, 6, 1),
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null),
                                        new PaginationParams()))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Transaction date start cannot be after transaction date end");

        assertThatThrownBy(
                        () ->
                                financialTransactionService.findAll(
                                        filter(
                                                scenario.farm.getId(),
                                                null,
                                                null,
                                                LocalDate.of(2026, 6, 30),
                                                LocalDate.of(2026, 6, 1),
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null),
                                        new PaginationParams()))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Paid at start cannot be after paid at end");

        assertThatThrownBy(
                        () ->
                                financialTransactionService.findAll(
                                        filter(
                                                scenario.farm.getId(),
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                new BigDecimal("5000.00"),
                                                new BigDecimal("100.00")),
                                        new PaginationParams()))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Minimum amount cannot be greater than maximum amount");

        assertThatThrownBy(
                        () ->
                                financialTransactionService.findAll(
                                        filter(
                                                scenario.farm.getId(),
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                new BigDecimal("-1.00"),
                                                null),
                                        new PaginationParams()))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Minimum amount cannot be negative");
    }

    @Test
    void shouldMarkOnlyActivePendingOverdueExpenses() {
        User admin = saveUser("Admin", "admin-overdue@example.com", UserType.ADMIN);
        Farm farm = saveFarm("Overdue Farm", FarmStatus.ACTIVE);
        FinancialTransaction pastPendingExpense =
                saveTransaction(
                        farm,
                        admin,
                        TransactionType.EXPENSE,
                        PaymentStatus.PENDING,
                        FinancialRecordStatus.ACTIVE,
                        LocalDate.of(2026, 6, 28));
        FinancialTransaction todayPendingExpense =
                saveTransaction(
                        farm,
                        admin,
                        TransactionType.EXPENSE,
                        PaymentStatus.PENDING,
                        FinancialRecordStatus.ACTIVE,
                        LocalDate.of(2026, 6, 29));
        FinancialTransaction futurePendingExpense =
                saveTransaction(
                        farm,
                        admin,
                        TransactionType.EXPENSE,
                        PaymentStatus.PENDING,
                        FinancialRecordStatus.ACTIVE,
                        LocalDate.of(2026, 6, 30));
        FinancialTransaction paidExpense =
                saveTransaction(
                        farm,
                        admin,
                        TransactionType.EXPENSE,
                        PaymentStatus.PAID,
                        FinancialRecordStatus.ACTIVE,
                        LocalDate.of(2026, 6, 28));
        FinancialTransaction canceledExpense =
                saveTransaction(
                        farm,
                        admin,
                        TransactionType.EXPENSE,
                        PaymentStatus.CANCELED,
                        FinancialRecordStatus.ACTIVE,
                        LocalDate.of(2026, 6, 28));
        FinancialTransaction alreadyOverdueExpense =
                saveTransaction(
                        farm,
                        admin,
                        TransactionType.EXPENSE,
                        PaymentStatus.OVERDUE,
                        FinancialRecordStatus.ACTIVE,
                        LocalDate.of(2026, 6, 28));
        FinancialTransaction income =
                saveTransaction(
                        farm,
                        admin,
                        TransactionType.INCOME,
                        PaymentStatus.PENDING,
                        FinancialRecordStatus.ACTIVE,
                        LocalDate.of(2026, 6, 28));
        FinancialTransaction deletedExpense =
                saveTransaction(
                        farm,
                        admin,
                        TransactionType.EXPENSE,
                        PaymentStatus.PENDING,
                        FinancialRecordStatus.DELETED,
                        LocalDate.of(2026, 6, 28));
        FinancialTransaction expenseWithoutDueDate =
                saveTransaction(
                        farm,
                        admin,
                        TransactionType.EXPENSE,
                        PaymentStatus.PENDING,
                        FinancialRecordStatus.ACTIVE,
                        null);

        int updatedTransactions = financialTransactionService.markOverdueTransactions();

        assertThat(updatedTransactions).isEqualTo(1);
        assertTransactionStatus(pastPendingExpense, PaymentStatus.OVERDUE);
        assertThat(
                        financialTransactionRepository
                                .findById(pastPendingExpense.getId())
                                .orElseThrow()
                                .getUpdatedAt())
                .isEqualTo(LocalDateTime.of(2026, 6, 29, 0, 0));
        assertTransactionStatus(todayPendingExpense, PaymentStatus.PENDING);
        assertTransactionStatus(futurePendingExpense, PaymentStatus.PENDING);
        assertTransactionStatus(paidExpense, PaymentStatus.PAID);
        assertTransactionStatus(canceledExpense, PaymentStatus.CANCELED);
        assertTransactionStatus(alreadyOverdueExpense, PaymentStatus.OVERDUE);
        assertTransactionStatus(income, PaymentStatus.PENDING);
        assertTransactionStatus(deletedExpense, PaymentStatus.PENDING);
        assertTransactionStatus(expenseWithoutDueDate, PaymentStatus.PENDING);
    }

    private FinancialTransactionRequest transactionRequest(
            Farm farm, FinancialCategory category, BigDecimal amount) {
        return new FinancialTransactionRequest(
                "Compra de insumos",
                amount,
                TransactionType.EXPENSE,
                null,
                null,
                LocalDate.now(),
                LocalDate.now().plusDays(10),
                null,
                null,
                farm.getId(),
                category == null ? null : category.getId(),
                null);
    }

    private FinancialTransactionUpdateRequest updateRequest(
            FinancialCategory category, BigDecimal amount, TransactionType type) {
        return new FinancialTransactionUpdateRequest(
                "Atualizada",
                amount,
                type,
                PaymentStatus.PENDING,
                null,
                LocalDate.now(),
                LocalDate.now().plusDays(10),
                null,
                null,
                category == null ? null : category.getId(),
                null);
    }

    private FinancialTransactionRequest transactionRequest(
            Farm farm, FinancialCategory category, BigDecimal amount, HarvestSeason harvestSeason) {
        return new FinancialTransactionRequest(
                "Compra de insumos",
                amount,
                TransactionType.EXPENSE,
                null,
                null,
                LocalDate.now(),
                LocalDate.now().plusDays(10),
                null,
                null,
                farm.getId(),
                category == null ? null : category.getId(),
                harvestSeason == null ? null : harvestSeason.getId());
    }

    private FinancialTransactionRequest transactionRequestWithHarvestSeasonId(
            Farm farm, Long harvestSeasonId) {
        return new FinancialTransactionRequest(
                "Compra de insumos",
                BigDecimal.TEN,
                TransactionType.EXPENSE,
                null,
                null,
                LocalDate.now(),
                LocalDate.now().plusDays(10),
                null,
                null,
                farm.getId(),
                null,
                harvestSeasonId);
    }

    private FinancialTransactionUpdateRequest updateRequest(
            FinancialCategory category,
            BigDecimal amount,
            TransactionType type,
            HarvestSeason harvestSeason) {
        return new FinancialTransactionUpdateRequest(
                "Atualizada",
                amount,
                type,
                PaymentStatus.PENDING,
                null,
                LocalDate.now(),
                LocalDate.now().plusDays(10),
                null,
                null,
                category == null ? null : category.getId(),
                harvestSeason == null ? null : harvestSeason.getId());
    }

    private FinancialTransactionUpdateRequest updateRequestWithHarvestSeasonId(
            Long harvestSeasonId) {
        return new FinancialTransactionUpdateRequest(
                "Atualizada",
                BigDecimal.TEN,
                TransactionType.EXPENSE,
                PaymentStatus.PENDING,
                null,
                LocalDate.now(),
                LocalDate.now().plusDays(10),
                null,
                null,
                null,
                harvestSeasonId);
    }

    private User saveUser(String name, String email, UserType userType) {
        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode("Strong1!"));
        user.setUserType(userType);
        user.setStatus(UserStatus.ACTIVE);

        return userRepository.save(user);
    }

    private Farm saveFarm(String name, FarmStatus status) {
        Farm farm = new Farm();
        farm.setName(name);
        farm.setStatus(status);

        return farmRepository.save(farm);
    }

    private FinancialCategory saveCategory(
            String name, Farm farm, TransactionType type, boolean defaultCategory) {
        return saveCategory(name, farm, type, defaultCategory, FinancialCategoryStatus.ACTIVE);
    }

    private FinancialCategory saveCategory(
            String name,
            Farm farm,
            TransactionType type,
            boolean defaultCategory,
            FinancialCategoryStatus status) {
        FinancialCategory category = new FinancialCategory();
        category.setName(name);
        category.setFarm(defaultCategory ? null : farm);
        category.setType(type);
        category.setDefaultCategory(defaultCategory);
        category.setStatus(status);

        return financialCategoryRepository.save(category);
    }

    private List<Long> findIds(FinancialTransactionFilterRequest filterRequest) {
        return financialTransactionService
                .findAll(filterRequest, new PaginationParams())
                .content()
                .stream()
                .map(FinancialTransactionResponse::id)
                .toList();
    }

    private FinancialTransactionFilterRequest filter(Long farmId) {
        return filter(
                farmId, null, null, null, null, null, null, null, null, null, null, null, null,
                null);
    }

    private FinancialTransactionFilterRequest filter(
            Long farmId,
            LocalDate transactionDateStart,
            LocalDate transactionDateEnd,
            LocalDate paidAtStart,
            LocalDate paidAtEnd,
            TransactionType type,
            Long categoryId,
            PaymentStatus paymentStatus,
            PaymentMethod paymentMethod,
            FinancialRecordStatus recordStatus,
            String description,
            Long createdByUserId,
            BigDecimal minAmount,
            BigDecimal maxAmount) {
        return pluralFilter(
                farmId,
                transactionDateStart,
                transactionDateEnd,
                paidAtStart,
                paidAtEnd,
                type,
                categoryId,
                null,
                paymentStatus,
                null,
                paymentMethod,
                null,
                recordStatus,
                description,
                createdByUserId,
                minAmount,
                maxAmount);
    }

    private FinancialTransactionFilterRequest pluralFilter(
            Long farmId,
            LocalDate transactionDateStart,
            LocalDate transactionDateEnd,
            LocalDate paidAtStart,
            LocalDate paidAtEnd,
            TransactionType type,
            Long categoryId,
            List<Long> categoryIds,
            PaymentStatus paymentStatus,
            List<PaymentStatus> paymentStatuses,
            PaymentMethod paymentMethod,
            List<PaymentMethod> paymentMethods,
            FinancialRecordStatus recordStatus,
            String description,
            Long createdByUserId,
            BigDecimal minAmount,
            BigDecimal maxAmount) {
        return new FinancialTransactionFilterRequest(
                farmId,
                transactionDateStart,
                transactionDateEnd,
                paidAtStart,
                paidAtEnd,
                type,
                categoryId,
                categoryIds,
                null,
                paymentStatus,
                paymentStatuses,
                paymentMethod,
                paymentMethods,
                recordStatus,
                description,
                createdByUserId,
                minAmount,
                maxAmount);
    }

    private FinancialTransactionFilterRequest filterByHarvestSeason(
            Long farmId, Long harvestSeasonId) {
        return new FinancialTransactionFilterRequest(
                farmId,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                harvestSeasonId,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    private FilterScenario saveFilterScenario() {
        User creator = saveUser("Creator", "creator-filter@example.com", UserType.ADMIN);
        User otherUser = saveUser("Other", "other-filter@example.com", UserType.ADMIN);
        Farm farm = saveFarm("Filter Farm", FarmStatus.ACTIVE);
        Farm otherFarm = saveFarm("Other Farm", FarmStatus.ACTIVE);
        FinancialCategory incomeCategory =
                saveCategory("Sales", farm, TransactionType.INCOME, false);
        FinancialCategory expenseCategory =
                saveCategory("Inputs", farm, TransactionType.EXPENSE, false);

        FinancialTransaction income =
                saveTransaction(
                        farm,
                        incomeCategory,
                        creator,
                        "Venda de soja",
                        new BigDecimal("50.00"),
                        TransactionType.INCOME,
                        PaymentStatus.PENDING,
                        PaymentMethod.PIX,
                        LocalDate.of(2026, 6, 1),
                        null,
                        FinancialRecordStatus.ACTIVE);
        FinancialTransaction expense =
                saveTransaction(
                        farm,
                        expenseCategory,
                        creator,
                        "Sementes de Soja",
                        new BigDecimal("100.00"),
                        TransactionType.EXPENSE,
                        PaymentStatus.PENDING,
                        PaymentMethod.CASH,
                        LocalDate.of(2026, 6, 10),
                        LocalDate.of(2026, 6, 18),
                        FinancialRecordStatus.ACTIVE);
        FinancialTransaction paid =
                saveTransaction(
                        farm,
                        expenseCategory,
                        otherUser,
                        "Adubo premium",
                        new BigDecimal("5000.00"),
                        TransactionType.EXPENSE,
                        PaymentStatus.PAID,
                        PaymentMethod.PIX,
                        LocalDate.of(2026, 6, 30),
                        LocalDate.of(2026, 6, 20),
                        FinancialRecordStatus.ACTIVE);
        FinancialTransaction deleted =
                saveTransaction(
                        farm,
                        expenseCategory,
                        creator,
                        "Deleted",
                        new BigDecimal("200.00"),
                        TransactionType.EXPENSE,
                        PaymentStatus.PENDING,
                        PaymentMethod.BOLETO,
                        LocalDate.of(2026, 6, 15),
                        null,
                        FinancialRecordStatus.DELETED);
        FinancialTransaction otherFarmTransaction =
                saveTransaction(
                        otherFarm,
                        null,
                        creator,
                        "Adubo premium",
                        new BigDecimal("5000.00"),
                        TransactionType.EXPENSE,
                        PaymentStatus.PAID,
                        PaymentMethod.PIX,
                        LocalDate.of(2026, 6, 30),
                        LocalDate.of(2026, 6, 20),
                        FinancialRecordStatus.ACTIVE);

        return new FilterScenario(
                farm,
                incomeCategory,
                expenseCategory,
                otherUser,
                income,
                expense,
                paid,
                deleted,
                otherFarmTransaction);
    }

    private ProductionActivity saveActivity(Farm farm, String name) {
        ProductionActivity activity = new ProductionActivity();
        activity.setFarm(farm);
        activity.setName(name);
        activity.setStatus(ProductionActivityStatus.ACTIVE);

        return productionActivityRepository.save(activity);
    }

    private HarvestSeason saveSeason(Farm farm, String name, HarvestSeasonStatus status) {
        HarvestSeason season = new HarvestSeason();
        season.setFarm(farm);
        season.setProductionActivity(saveActivity(farm, name + " Activity"));
        season.setName(name);
        season.setStartDate(LocalDate.of(2026, 1, 1));
        season.setExpectedRevenue(BigDecimal.ZERO);
        season.setExpectedCost(BigDecimal.ZERO);
        season.setAreaHectares(BigDecimal.ZERO);
        season.setStatus(status);

        return harvestSeasonRepository.save(season);
    }

    private FinancialTransaction saveTransaction(
            Farm farm,
            FinancialCategory category,
            User user,
            String description,
            BigDecimal amount,
            TransactionType type,
            PaymentStatus status,
            PaymentMethod paymentMethod,
            LocalDate transactionDate,
            LocalDate paidAt,
            FinancialRecordStatus recordStatus) {
        FinancialTransaction transaction = new FinancialTransaction();
        transaction.setDescription(description);
        transaction.setAmount(amount);
        transaction.setType(type);
        transaction.setStatus(status);
        transaction.setPaymentMethod(paymentMethod);
        transaction.setTransactionDate(transactionDate);
        transaction.setDueDate(transactionDate.plusDays(5));
        transaction.setPaidAt(paidAt);
        transaction.setFarm(farm);
        transaction.setCategory(category);
        transaction.setCreatedByUser(user);
        transaction.setRecordStatus(recordStatus);

        return financialTransactionRepository.save(transaction);
    }

    private FinancialTransaction saveTransaction(
            Farm farm, FinancialCategory category, User user, TransactionType type) {
        return saveTransaction(farm, category, user, type, null);
    }

    private FinancialTransaction saveTransaction(
            Farm farm,
            FinancialCategory category,
            User user,
            TransactionType type,
            HarvestSeason harvestSeason) {
        FinancialTransaction transaction = new FinancialTransaction();
        transaction.setDescription("Transaction");
        transaction.setAmount(BigDecimal.TEN);
        transaction.setType(type);
        transaction.setStatus(PaymentStatus.PENDING);
        transaction.setTransactionDate(LocalDate.now());
        transaction.setDueDate(LocalDate.now().plusDays(5));
        transaction.setFarm(farm);
        transaction.setCategory(category);
        transaction.setHarvestSeason(harvestSeason);
        transaction.setCreatedByUser(user);
        transaction.setRecordStatus(FinancialRecordStatus.ACTIVE);

        return financialTransactionRepository.save(transaction);
    }

    private FinancialTransaction saveTransaction(
            Farm farm,
            User user,
            TransactionType type,
            PaymentStatus status,
            FinancialRecordStatus recordStatus,
            LocalDate dueDate) {
        FinancialTransaction transaction = new FinancialTransaction();
        transaction.setDescription("Overdue transaction");
        transaction.setAmount(BigDecimal.TEN);
        transaction.setType(type);
        transaction.setStatus(status);
        transaction.setTransactionDate(LocalDate.of(2026, 6, 1));
        transaction.setDueDate(dueDate);
        transaction.setFarm(farm);
        transaction.setCreatedByUser(user);
        transaction.setRecordStatus(recordStatus);

        return financialTransactionRepository.save(transaction);
    }

    private void assertTransactionStatus(
            FinancialTransaction transaction, PaymentStatus expectedStatus) {
        assertThat(
                        financialTransactionRepository
                                .findById(transaction.getId())
                                .orElseThrow()
                                .getStatus())
                .isEqualTo(expectedStatus);
    }

    private void authenticateAs(User user, String role) {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        String.valueOf(user.getId()),
                        null,
                        List.of(new SimpleGrantedAuthority(role)));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private record FilterScenario(
            Farm farm,
            FinancialCategory incomeCategory,
            FinancialCategory expenseCategory,
            User otherUser,
            FinancialTransaction income,
            FinancialTransaction expense,
            FinancialTransaction paid,
            FinancialTransaction deleted,
            FinancialTransaction otherFarmTransaction) {}

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(
                    Instant.parse("2026-06-29T03:00:00Z"), ZoneId.of("America/Sao_Paulo"));
        }
    }
}
