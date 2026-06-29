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
import br.com.gestaodireta.shared.exception.BusinessException;
import br.com.gestaodireta.shared.exception.ValidationException;
import br.com.gestaodireta.shared.pagination.PaginationParams;
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

    @Autowired private FarmRepository farmRepository;

    @Autowired private UserRepository userRepository;

    @Autowired private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        financialTransactionRepository.deleteAll();
        financialCategoryRepository.deleteAll();
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
                category == null ? null : category.getId());
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
                category == null ? null : category.getId());
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
        return new FinancialTransactionFilterRequest(
                farmId,
                transactionDateStart,
                transactionDateEnd,
                paidAtStart,
                paidAtEnd,
                type,
                categoryId,
                paymentStatus,
                paymentMethod,
                recordStatus,
                description,
                createdByUserId,
                minAmount,
                maxAmount);
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
                expenseCategory,
                otherUser,
                income,
                expense,
                paid,
                deleted,
                otherFarmTransaction);
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
        FinancialTransaction transaction = new FinancialTransaction();
        transaction.setDescription("Transaction");
        transaction.setAmount(BigDecimal.TEN);
        transaction.setType(type);
        transaction.setStatus(PaymentStatus.PENDING);
        transaction.setTransactionDate(LocalDate.now());
        transaction.setDueDate(LocalDate.now().plusDays(5));
        transaction.setFarm(farm);
        transaction.setCategory(category);
        transaction.setCreatedByUser(user);
        transaction.setRecordStatus(FinancialRecordStatus.ACTIVE);

        return financialTransactionRepository.save(transaction);
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
            FinancialCategory expenseCategory,
            User otherUser,
            FinancialTransaction income,
            FinancialTransaction expense,
            FinancialTransaction paid,
            FinancialTransaction deleted,
            FinancialTransaction otherFarmTransaction) {}
}
