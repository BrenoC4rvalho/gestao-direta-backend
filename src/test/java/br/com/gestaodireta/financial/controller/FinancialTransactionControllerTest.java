package br.com.gestaodireta.financial.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.gestaodireta.farm.entity.Farm;
import br.com.gestaodireta.farm.entity.FarmUser;
import br.com.gestaodireta.farm.enumeration.FarmStatus;
import br.com.gestaodireta.farm.enumeration.FarmUserRole;
import br.com.gestaodireta.farm.repository.FarmRepository;
import br.com.gestaodireta.farm.repository.FarmUserRepository;
import br.com.gestaodireta.financial.entity.FinancialCategory;
import br.com.gestaodireta.financial.entity.FinancialTransaction;
import br.com.gestaodireta.financial.enumeration.FinancialCategoryStatus;
import br.com.gestaodireta.financial.enumeration.FinancialRecordStatus;
import br.com.gestaodireta.financial.enumeration.PaymentMethod;
import br.com.gestaodireta.financial.enumeration.PaymentStatus;
import br.com.gestaodireta.financial.enumeration.TransactionType;
import br.com.gestaodireta.financial.repository.FinancialCategoryRepository;
import br.com.gestaodireta.financial.repository.FinancialTransactionRepository;
import br.com.gestaodireta.support.PostgresIntegrationTest;
import br.com.gestaodireta.user.entity.User;
import br.com.gestaodireta.user.enumeration.UserStatus;
import br.com.gestaodireta.user.enumeration.UserType;
import br.com.gestaodireta.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class FinancialTransactionControllerTest extends PostgresIntegrationTest {

    private static final String CONTEXT_PATH = "/api";

    @Autowired private MockMvc mockMvc;

    @Autowired private FinancialTransactionRepository financialTransactionRepository;

    @Autowired private FinancialCategoryRepository financialCategoryRepository;

    @Autowired private FarmUserRepository farmUserRepository;

    @Autowired private FarmRepository farmRepository;

    @Autowired private UserRepository userRepository;

    @Autowired private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        financialTransactionRepository.deleteAll();
        financialCategoryRepository.deleteAll();
        farmUserRepository.deleteAll();
        farmRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void shouldCreateTransactionAsAdminProducerAndEmployee() throws Exception {
        Farm farm = saveFarm(FarmStatus.ACTIVE);
        User admin = saveUser("Admin", "admin@example.com", UserType.ADMIN);
        User producer = saveUser("Producer", "producer@example.com", UserType.USER);
        User employee = saveUser("Employee", "employee@example.com", UserType.USER);
        saveFarmUser(farm, producer, FarmUserRole.PRODUCER);
        saveFarmUser(farm, employee, FarmUserRole.EMPLOYEE);

        expectCanCreate(String.valueOf(admin.getId()), "ADMIN", farm);
        expectCanCreate(String.valueOf(producer.getId()), "USER", farm);
        expectCanCreate(String.valueOf(employee.getId()), "USER", farm);
    }

    @Test
    void shouldRejectTransactionCreationForAccountantInactiveOrUnlinkedUser() throws Exception {
        Farm farm = saveFarm(FarmStatus.ACTIVE);
        User accountant = saveUser("Accountant", "accountant@example.com", UserType.USER);
        User inactive = saveUser("Inactive", "inactive@example.com", UserType.USER);
        User unlinked = saveUser("Unlinked", "unlinked@example.com", UserType.USER);
        saveFarmUser(farm, accountant, FarmUserRole.ACCOUNTANT);
        saveFarmUser(farm, inactive, FarmUserRole.INACTIVE);

        expectCannotCreate(accountant, farm);
        expectCannotCreate(inactive, farm);
        expectCannotCreate(unlinked, farm);
    }

    @Test
    void shouldValidateDescriptionAndDeleteLogically() throws Exception {
        Farm farm = saveFarm(FarmStatus.ACTIVE);
        User admin = saveUser("Admin", "admin@example.com", UserType.ADMIN);
        FinancialTransaction transaction = saveTransaction(farm, admin);

        mockMvc.perform(
                        post("/api/financial/transactions")
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(admin.getId())).roles("ADMIN"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(transactionBody(farm.getId(), "")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(
                        delete("/api/financial/transactions/{id}", transaction.getId())
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(admin.getId())).roles("ADMIN"))
                                .with(csrf()))
                .andExpect(status().isNoContent());

        FinancialTransaction savedTransaction =
                financialTransactionRepository.findById(transaction.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(savedTransaction.getRecordStatus())
                .isEqualTo(FinancialRecordStatus.DELETED);
    }

    @Test
    void shouldListTransactionsWithFarmIdOnly() throws Exception {
        Farm farm = saveFarm(FarmStatus.ACTIVE);
        User admin = saveUser("Admin", "list-admin@example.com", UserType.ADMIN);
        FinancialTransaction transaction = saveTransaction(farm, admin);

        mockMvc.perform(
                        get("/api/financial/transactions")
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(admin.getId())).roles("ADMIN"))
                                .param("farmId", String.valueOf(farm.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(transaction.getId()))
                .andExpect(jsonPath("$.content[0].recordStatus").value("ACTIVE"));
    }

    @Test
    void shouldListTransactionsWithAllFilters() throws Exception {
        Farm farm = saveFarm(FarmStatus.ACTIVE);
        User admin = saveUser("Admin", "all-filters-admin@example.com", UserType.ADMIN);
        FinancialCategory category = saveCategory(farm, TransactionType.EXPENSE);
        FinancialCategory otherCategory = saveCategory(farm, TransactionType.EXPENSE);
        FinancialTransaction transaction =
                saveTransaction(
                        farm,
                        category,
                        admin,
                        "Compra de soja",
                        new BigDecimal("250.00"),
                        TransactionType.EXPENSE,
                        PaymentStatus.PAID,
                        PaymentMethod.PIX,
                        LocalDate.of(2026, 6, 15),
                        LocalDate.of(2026, 6, 20));

        mockMvc.perform(
                        get("/api/financial/transactions")
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(admin.getId())).roles("ADMIN"))
                                .param("farmId", String.valueOf(farm.getId()))
                                .param("transactionDateStart", "2026-06-01")
                                .param("transactionDateEnd", "2026-06-30")
                                .param("paidAtStart", "2026-06-01")
                                .param("paidAtEnd", "2026-06-30")
                                .param("type", "EXPENSE")
                                .param("categoryId", String.valueOf(category.getId()))
                                .param("paymentStatus", "PAID")
                                .param("paymentMethod", "PIX")
                                .param("recordStatus", "ACTIVE")
                                .param("description", "soja")
                                .param("createdByUserId", String.valueOf(admin.getId()))
                                .param("minAmount", "100")
                                .param("maxAmount", "5000")
                                .param("page", "0")
                                .param("size", "10")
                                .param("sort", "transactionDate")
                                .param("direction", "DESC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(transaction.getId()))
                .andExpect(jsonPath("$.content[0].categoryId").value(category.getId()))
                .andExpect(jsonPath("$.content[0].status").value("PAID"))
                .andExpect(jsonPath("$.content[0].paymentMethod").value("PIX"));

        mockMvc.perform(
                        get("/api/financial/transactions")
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(admin.getId())).roles("ADMIN"))
                                .param("farmId", String.valueOf(farm.getId()))
                                .param("categoryId", String.valueOf(otherCategory.getId()))
                                .param("categoryIds", String.valueOf(category.getId()))
                                .param("categoryIds", String.valueOf(category.getId()))
                                .param("paymentStatus", "PENDING")
                                .param("paymentStatuses", "PAID")
                                .param("paymentMethod", "CASH")
                                .param("paymentMethods", "PIX"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(transaction.getId()));
    }

    @Test
    void shouldRejectInvalidListParams() throws Exception {
        Farm farm = saveFarm(FarmStatus.ACTIVE);
        User admin = saveUser("Admin", "invalid-list-admin@example.com", UserType.ADMIN);

        mockMvc.perform(
                        get("/api/financial/transactions")
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(admin.getId())).roles("ADMIN"))
                                .param("farmId", String.valueOf(farm.getId()))
                                .param("type", "INVALID"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(
                        get("/api/financial/transactions")
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(admin.getId())).roles("ADMIN"))
                                .param("farmId", String.valueOf(farm.getId()))
                                .param("transactionDateStart", "invalid-date"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRequireAuthenticationAndFinancialPermissionWhenListingTransactions()
            throws Exception {
        Farm farm = saveFarm(FarmStatus.ACTIVE);
        User unlinked = saveUser("Unlinked", "unlinked-list@example.com", UserType.USER);

        mockMvc.perform(
                        get("/api/financial/transactions")
                                .contextPath(CONTEXT_PATH)
                                .param("farmId", String.valueOf(farm.getId())))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(
                        get("/api/financial/transactions")
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(unlinked.getId())).roles("USER"))
                                .param("farmId", String.valueOf(farm.getId())))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldAllowAccountantToListTransactions() throws Exception {
        Farm farm = saveFarm(FarmStatus.ACTIVE);
        User accountant = saveUser("Accountant", "accountant-list@example.com", UserType.USER);
        saveFarmUser(farm, accountant, FarmUserRole.ACCOUNTANT);
        FinancialTransaction transaction = saveTransaction(farm, accountant);

        mockMvc.perform(
                        get("/api/financial/transactions")
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(accountant.getId())).roles("USER"))
                                .param("farmId", String.valueOf(farm.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(transaction.getId()));
    }

    private void expectCanCreate(String username, String role, Farm farm) throws Exception {
        mockMvc.perform(
                        post("/api/financial/transactions")
                                .contextPath(CONTEXT_PATH)
                                .with(user(username).roles(role))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(transactionBody(farm.getId(), "Compra de insumos")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.farmId").value(farm.getId()))
                .andExpect(jsonPath("$.recordStatus").value("ACTIVE"));
    }

    private void expectCannotCreate(User user, Farm farm) throws Exception {
        mockMvc.perform(
                        post("/api/financial/transactions")
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(user.getId())).roles("USER"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(transactionBody(farm.getId(), "Compra de insumos")))
                .andExpect(status().isForbidden());
    }

    private String transactionBody(Long farmId, String description) {
        return """
                {
                  "description": "%s",
                  "amount": 10.00,
                  "type": "EXPENSE",
                  "transactionDate": "%s",
                  "farmId": %d
                }
                """
                .formatted(description, LocalDate.now(), farmId);
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

    private Farm saveFarm(FarmStatus status) {
        Farm farm = new Farm();
        farm.setName("Farm");
        farm.setStatus(status);

        return farmRepository.save(farm);
    }

    private FarmUser saveFarmUser(Farm farm, User user, FarmUserRole role) {
        FarmUser farmUser = new FarmUser();
        farmUser.setFarm(farm);
        farmUser.setUser(user);
        farmUser.setRole(role);

        return farmUserRepository.save(farmUser);
    }

    private FinancialCategory saveCategory(Farm farm, TransactionType type) {
        FinancialCategory category = new FinancialCategory();
        category.setName("Category");
        category.setFarm(farm);
        category.setType(type);
        category.setDefaultCategory(false);
        category.setStatus(FinancialCategoryStatus.ACTIVE);

        return financialCategoryRepository.save(category);
    }

    private FinancialTransaction saveTransaction(Farm farm, User user) {
        return saveTransaction(
                farm,
                null,
                user,
                "Transaction",
                BigDecimal.TEN,
                TransactionType.EXPENSE,
                PaymentStatus.PENDING,
                null,
                LocalDate.now(),
                null);
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
            LocalDate paidAt) {
        FinancialTransaction transaction = new FinancialTransaction();
        transaction.setDescription(description);
        transaction.setAmount(amount);
        transaction.setType(type);
        transaction.setStatus(status);
        transaction.setPaymentMethod(paymentMethod);
        transaction.setTransactionDate(transactionDate);
        transaction.setPaidAt(paidAt);
        transaction.setFarm(farm);
        transaction.setCategory(category);
        transaction.setCreatedByUser(user);
        transaction.setRecordStatus(FinancialRecordStatus.ACTIVE);

        return financialTransactionRepository.save(transaction);
    }
}
