package br.com.gestaodireta.financial.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.gestaodireta.farm.entity.Farm;
import br.com.gestaodireta.farm.enumeration.FarmStatus;
import br.com.gestaodireta.farm.repository.FarmRepository;
import br.com.gestaodireta.farm.repository.FarmUserRepository;
import br.com.gestaodireta.financial.dto.FinancialTransactionRequest;
import br.com.gestaodireta.financial.dto.FinancialTransactionResponse;
import br.com.gestaodireta.financial.dto.FinancialTransactionUpdateRequest;
import br.com.gestaodireta.financial.entity.FinancialCategory;
import br.com.gestaodireta.financial.entity.FinancialTransaction;
import br.com.gestaodireta.financial.enumeration.FinancialCategoryStatus;
import br.com.gestaodireta.financial.enumeration.FinancialRecordStatus;
import br.com.gestaodireta.financial.enumeration.PaymentStatus;
import br.com.gestaodireta.financial.enumeration.TransactionType;
import br.com.gestaodireta.financial.repository.FinancialCategoryRepository;
import br.com.gestaodireta.financial.repository.FinancialTransactionRepository;
import br.com.gestaodireta.shared.exception.BusinessException;
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
        FinancialCategory category = new FinancialCategory();
        category.setName(name);
        category.setFarm(defaultCategory ? null : farm);
        category.setType(type);
        category.setDefaultCategory(defaultCategory);
        category.setStatus(FinancialCategoryStatus.ACTIVE);

        return financialCategoryRepository.save(category);
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
}
