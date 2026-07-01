package br.com.gestaodireta.financial.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class FinancialCategoryControllerTest extends PostgresIntegrationTest {

    private static final String CONTEXT_PATH = "/api";

    private static final String DUPLICATE_CATEGORY_MESSAGE =
            "A category with this name already exists.";

    @Autowired private MockMvc mockMvc;

    @Autowired private FinancialCategoryRepository financialCategoryRepository;

    @Autowired private FinancialTransactionRepository financialTransactionRepository;

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
    void shouldListGlobalCategoriesAsAdminIncludingActiveAndInactive() throws Exception {
        User admin = saveUser("Admin", "admin@example.com", UserType.ADMIN);
        saveCategory("Global Active", null, true, FinancialCategoryStatus.ACTIVE);
        saveCategory("Global Inactive", null, true, FinancialCategoryStatus.INACTIVE);

        mockMvc.perform(
                        get("/api/financial/categories/global")
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(admin.getId())).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.content[*].name")
                                .value(containsInAnyOrder("Global Active", "Global Inactive")))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void shouldDenyGlobalCategoryListForNonAdminRoles() throws Exception {
        Farm farm = saveFarm("Farm");
        User producer = saveUser("Producer", "producer@example.com", UserType.USER);
        User employee = saveUser("Employee", "employee@example.com", UserType.USER);
        User accountant = saveUser("Accountant", "accountant@example.com", UserType.USER);
        saveFarmUser(farm, producer, FarmUserRole.PRODUCER);
        saveFarmUser(farm, employee, FarmUserRole.EMPLOYEE);
        saveFarmUser(farm, accountant, FarmUserRole.ACCOUNTANT);

        expectCannotListGlobalCategories(producer);
        expectCannotListGlobalCategories(employee);
        expectCannotListGlobalCategories(accountant);
    }

    @Test
    void shouldDenyGlobalCategoryListWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/financial/categories/global").contextPath(CONTEXT_PATH))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldListActiveGlobalAndFarmCategoriesByFarmId() throws Exception {
        Farm farm = saveFarm("Farm");
        Farm otherFarm = saveFarm("Other Farm");
        User producer = saveUser("Producer", "producer@example.com", UserType.USER);
        saveFarmUser(farm, producer, FarmUserRole.PRODUCER);
        saveCategory("Global Active", null, true, FinancialCategoryStatus.ACTIVE);
        saveCategory("Farm Active", farm, false, FinancialCategoryStatus.ACTIVE);
        saveCategory("Global Inactive", null, true, FinancialCategoryStatus.INACTIVE);
        saveCategory("Farm Inactive", farm, false, FinancialCategoryStatus.INACTIVE);
        saveCategory("Other Farm Active", otherFarm, false, FinancialCategoryStatus.ACTIVE);

        mockMvc.perform(
                        get("/api/financial/categories")
                                .contextPath(CONTEXT_PATH)
                                .param("farmId", String.valueOf(farm.getId()))
                                .with(user(String.valueOf(producer.getId())).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.content[*].name")
                                .value(containsInAnyOrder("Global Active", "Farm Active")))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void shouldListActiveAndInactiveGlobalAndFarmCategoriesWhenIncludeInactiveIsTrue()
            throws Exception {
        Farm farm = saveFarm("Farm");
        Farm otherFarm = saveFarm("Other Farm");
        User producer = saveUser("Producer", "producer@example.com", UserType.USER);
        saveFarmUser(farm, producer, FarmUserRole.PRODUCER);
        saveCategory("Global Active", null, true, FinancialCategoryStatus.ACTIVE);
        saveCategory("Farm Active", farm, false, FinancialCategoryStatus.ACTIVE);
        saveCategory("Global Inactive", null, true, FinancialCategoryStatus.INACTIVE);
        saveCategory("Farm Inactive", farm, false, FinancialCategoryStatus.INACTIVE);
        saveCategory("Other Farm Inactive", otherFarm, false, FinancialCategoryStatus.INACTIVE);

        mockMvc.perform(
                        get("/api/financial/categories")
                                .contextPath(CONTEXT_PATH)
                                .param("farmId", String.valueOf(farm.getId()))
                                .param("includeInactive", "true")
                                .with(user(String.valueOf(producer.getId())).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.content[*].name")
                                .value(
                                        containsInAnyOrder(
                                                "Global Active",
                                                "Farm Active",
                                                "Global Inactive",
                                                "Farm Inactive")))
                .andExpect(
                        jsonPath("$.content[*].status")
                                .value(
                                        containsInAnyOrder(
                                                "ACTIVE", "ACTIVE", "INACTIVE", "INACTIVE")))
                .andExpect(jsonPath("$.totalElements").value(4));
    }

    @Test
    void shouldListCategoriesUsedInActiveTransactionsFilter() throws Exception {
        Farm farm = saveFarm("Farm");
        Farm otherFarm = saveFarm("Other Farm");
        User producer = saveUser("Producer", "producer@example.com", UserType.USER);
        saveFarmUser(farm, producer, FarmUserRole.PRODUCER);
        FinancialCategory globalUsed =
                saveCategory("A Global Used", null, true, FinancialCategoryStatus.ACTIVE);
        FinancialCategory farmUsed =
                saveCategory("B Farm Used", farm, false, FinancialCategoryStatus.ACTIVE);
        FinancialCategory inactiveUsed =
                saveCategory("C Inactive Used", farm, false, FinancialCategoryStatus.INACTIVE);
        FinancialCategory deletedOnly =
                saveCategory("Deleted Only", farm, false, FinancialCategoryStatus.ACTIVE);
        saveCategory("Unused", farm, false, FinancialCategoryStatus.ACTIVE);
        FinancialCategory otherFarmUsed =
                saveCategory("Other Farm Used", otherFarm, false, FinancialCategoryStatus.ACTIVE);
        saveTransaction(farm, globalUsed, producer, FinancialRecordStatus.ACTIVE);
        saveTransaction(farm, farmUsed, producer, FinancialRecordStatus.ACTIVE);
        saveTransaction(farm, inactiveUsed, producer, FinancialRecordStatus.ACTIVE);
        saveTransaction(farm, deletedOnly, producer, FinancialRecordStatus.DELETED);
        saveTransaction(otherFarm, otherFarmUsed, producer, FinancialRecordStatus.ACTIVE);

        mockMvc.perform(
                        get("/api/financial/categories/used-in-transactions")
                                .contextPath(CONTEXT_PATH)
                                .param("farmId", String.valueOf(farm.getId()))
                                .with(user(String.valueOf(producer.getId())).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$[*].name")
                                .value(
                                        containsInAnyOrder(
                                                "A Global Used", "B Farm Used", "C Inactive Used")))
                .andExpect(
                        jsonPath("$[*].status")
                                .value(containsInAnyOrder("ACTIVE", "ACTIVE", "INACTIVE")))
                .andExpect(jsonPath("$[0].farmId").value(nullValue()))
                .andExpect(jsonPath("$[0].isDefault").value(true));
    }

    @Test
    void shouldDenyUsedCategoriesFilterWithoutAuthentication() throws Exception {
        Farm farm = saveFarm("Farm");

        mockMvc.perform(
                        get("/api/financial/categories/used-in-transactions")
                                .contextPath(CONTEXT_PATH)
                                .param("farmId", String.valueOf(farm.getId())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldDenyUsedCategoriesFilterForUserWithoutFinancialAccess() throws Exception {
        Farm farm = saveFarm("Farm");
        User unlinked = saveUser("Unlinked", "unlinked@example.com", UserType.USER);

        mockMvc.perform(
                        get("/api/financial/categories/used-in-transactions")
                                .contextPath(CONTEXT_PATH)
                                .param("farmId", String.valueOf(farm.getId()))
                                .with(user(String.valueOf(unlinked.getId())).roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldDenyUsedCategoriesFilterForInactiveLinkAndInactiveFarm() throws Exception {
        Farm farm = saveFarm("Farm");
        Farm inactiveFarm = saveFarm("Inactive Farm");
        inactiveFarm.setStatus(FarmStatus.INACTIVE);
        farmRepository.save(inactiveFarm);
        User inactiveLinkUser =
                saveUser("Inactive Link", "inactive-link@example.com", UserType.USER);
        User inactiveFarmUser =
                saveUser("Inactive Farm User", "inactive-farm@example.com", UserType.USER);
        saveFarmUser(farm, inactiveLinkUser, FarmUserRole.INACTIVE);
        saveFarmUser(inactiveFarm, inactiveFarmUser, FarmUserRole.PRODUCER);

        expectCannotListUsedCategories(inactiveLinkUser, farm);
        expectCannotListUsedCategories(inactiveFarmUser, inactiveFarm);
    }

    @Test
    void shouldReturnBadRequestWhenUsedCategoriesFarmIdIsMissing() throws Exception {
        User admin = saveUser("Admin", "admin@example.com", UserType.ADMIN);

        mockMvc.perform(
                        get("/api/financial/categories/used-in-transactions")
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(admin.getId())).roles("ADMIN")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnBadRequestWhenUsedCategoriesFarmIdIsInvalid() throws Exception {
        User admin = saveUser("Admin", "admin@example.com", UserType.ADMIN);

        mockMvc.perform(
                        get("/api/financial/categories/used-in-transactions")
                                .contextPath(CONTEXT_PATH)
                                .param("farmId", "invalid")
                                .with(user(String.valueOf(admin.getId())).roles("ADMIN")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnNotFoundWhenUsedCategoriesFarmDoesNotExist() throws Exception {
        User user = saveUser("User", "user@example.com", UserType.USER);

        mockMvc.perform(
                        get("/api/financial/categories/used-in-transactions")
                                .contextPath(CONTEXT_PATH)
                                .param("farmId", "999")
                                .with(user(String.valueOf(user.getId())).roles("USER")))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldAllowEmployeeAndAccountantToListCategoriesWithFinancialAccess() throws Exception {
        Farm farm = saveFarm("Farm");
        User employee = saveUser("Employee", "employee@example.com", UserType.USER);
        User accountant = saveUser("Accountant", "accountant@example.com", UserType.USER);
        saveFarmUser(farm, employee, FarmUserRole.EMPLOYEE);
        saveFarmUser(farm, accountant, FarmUserRole.ACCOUNTANT);
        saveCategory("Farm Active", farm, false, FinancialCategoryStatus.ACTIVE);

        expectCanListCategories(employee, farm);
        expectCanListCategories(accountant, farm);
    }

    @Test
    void shouldDenyCategoryListWithoutAuthentication() throws Exception {
        Farm farm = saveFarm("Farm");

        mockMvc.perform(
                        get("/api/financial/categories")
                                .contextPath(CONTEXT_PATH)
                                .param("farmId", String.valueOf(farm.getId())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldDenyCategoryListForUserWithoutFinancialAccess() throws Exception {
        Farm farm = saveFarm("Farm");
        User unlinked = saveUser("Unlinked", "unlinked@example.com", UserType.USER);

        mockMvc.perform(
                        get("/api/financial/categories")
                                .contextPath(CONTEXT_PATH)
                                .param("farmId", String.valueOf(farm.getId()))
                                .param("includeInactive", "true")
                                .with(user(String.valueOf(unlinked.getId())).roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldCreateGlobalCategoryAsAdminOnly() throws Exception {
        User admin = saveUser("Admin", "admin@example.com", UserType.ADMIN);
        Farm farm = saveFarm("Farm");
        User producer = saveUser("Producer", "producer@example.com", UserType.USER);
        saveFarmUser(farm, producer, FarmUserRole.PRODUCER);

        mockMvc.perform(
                        post("/api/financial/categories")
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(producer.getId())).roles("USER"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(categoryBody(null, true)))
                .andExpect(status().isForbidden());

        mockMvc.perform(
                        post("/api/financial/categories")
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(admin.getId())).roles("ADMIN"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(categoryBody(null, true)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.farmId").value(nullValue()))
                .andExpect(jsonPath("$.isDefault").value(true))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void shouldCreateFarmCategoryAsProducerOnly() throws Exception {
        Farm farm = saveFarm("Farm");
        User producer = saveUser("Producer", "producer@example.com", UserType.USER);
        User employee = saveUser("Employee", "employee@example.com", UserType.USER);
        User accountant = saveUser("Accountant", "accountant@example.com", UserType.USER);
        saveFarmUser(farm, producer, FarmUserRole.PRODUCER);
        saveFarmUser(farm, employee, FarmUserRole.EMPLOYEE);
        saveFarmUser(farm, accountant, FarmUserRole.ACCOUNTANT);

        mockMvc.perform(
                        post("/api/financial/categories")
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(producer.getId())).roles("USER"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(categoryBody(farm.getId(), false)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.farmId").value(farm.getId()))
                .andExpect(jsonPath("$.isDefault").value(false));

        expectCannotCreate(employee, farm);
        expectCannotCreate(accountant, farm);
    }

    @Test
    void shouldReturnBadRequestWhenCreatingDuplicateCategoryName() throws Exception {
        User admin = saveUser("Admin", "admin@example.com", UserType.ADMIN);
        saveCategory("Insumos", null, true, FinancialCategoryStatus.ACTIVE);

        mockMvc.perform(
                        post("/api/financial/categories")
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(admin.getId())).roles("ADMIN"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(categoryBody(" insumos ", null, true)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(DUPLICATE_CATEGORY_MESSAGE));
    }

    @Test
    void shouldReturnBadRequestWhenUpdatingDuplicateCategoryName() throws Exception {
        User admin = saveUser("Admin", "admin@example.com", UserType.ADMIN);
        FinancialCategory category =
                saveCategory("Insumos", null, true, FinancialCategoryStatus.ACTIVE);
        saveCategory("Frete", null, true, FinancialCategoryStatus.ACTIVE);

        mockMvc.perform(
                        put("/api/financial/categories/{id}", category.getId())
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(admin.getId())).roles("ADMIN"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(categoryBody(" frete ", null, true)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(DUPLICATE_CATEGORY_MESSAGE));
    }

    @Test
    void shouldDeleteCategoryLogicallyAsAdmin() throws Exception {
        User admin = saveUser("Admin", "admin@example.com", UserType.ADMIN);
        FinancialCategory category =
                saveCategory(
                        "Farm Category", saveFarm("Farm"), false, FinancialCategoryStatus.ACTIVE);

        mockMvc.perform(
                        delete("/api/financial/categories/{id}", category.getId())
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(admin.getId())).roles("ADMIN"))
                                .with(csrf()))
                .andExpect(status().isNoContent());

        FinancialCategory savedCategory =
                financialCategoryRepository.findById(category.getId()).orElseThrow();
        assertThat(savedCategory.getStatus()).isEqualTo(FinancialCategoryStatus.INACTIVE);
    }

    @Test
    void shouldActivateCategoryAsAdmin() throws Exception {
        User admin = saveUser("Admin", "admin@example.com", UserType.ADMIN);
        FinancialCategory category =
                saveCategory(
                        "Farm Category", saveFarm("Farm"), false, FinancialCategoryStatus.INACTIVE);

        mockMvc.perform(
                        patch("/api/financial/categories/{id}/activate", category.getId())
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(admin.getId())).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        FinancialCategory savedCategory =
                financialCategoryRepository.findById(category.getId()).orElseThrow();
        assertThat(savedCategory.getStatus()).isEqualTo(FinancialCategoryStatus.ACTIVE);
    }

    @Test
    void shouldActivateOwnFarmCategoryAsProducer() throws Exception {
        Farm farm = saveFarm("Farm");
        User producer = saveUser("Producer", "producer@example.com", UserType.USER);
        saveFarmUser(farm, producer, FarmUserRole.PRODUCER);
        FinancialCategory category =
                saveCategory("Farm Category", farm, false, FinancialCategoryStatus.INACTIVE);

        mockMvc.perform(
                        patch("/api/financial/categories/{id}/activate", category.getId())
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(producer.getId())).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        FinancialCategory savedCategory =
                financialCategoryRepository.findById(category.getId()).orElseThrow();
        assertThat(savedCategory.getStatus()).isEqualTo(FinancialCategoryStatus.ACTIVE);
    }

    @Test
    void shouldDenyActivateCategoryForEmployeeAccountantAndUserWithoutFarmLink() throws Exception {
        Farm farm = saveFarm("Farm");
        User employee = saveUser("Employee", "employee@example.com", UserType.USER);
        User accountant = saveUser("Accountant", "accountant@example.com", UserType.USER);
        User unlinkedUser = saveUser("Unlinked", "unlinked@example.com", UserType.USER);
        saveFarmUser(farm, employee, FarmUserRole.EMPLOYEE);
        saveFarmUser(farm, accountant, FarmUserRole.ACCOUNTANT);
        FinancialCategory category =
                saveCategory("Farm Category", farm, false, FinancialCategoryStatus.INACTIVE);

        expectCannotActivate(employee, category);
        expectCannotActivate(accountant, category);
        expectCannotActivate(unlinkedUser, category);
    }

    @Test
    void shouldDenyActivateGlobalCategoryForNonAdmin() throws Exception {
        User user = saveUser("User", "user@example.com", UserType.USER);
        FinancialCategory category =
                saveCategory("Global Category", null, true, FinancialCategoryStatus.INACTIVE);

        expectCannotActivate(user, category);
    }

    @Test
    void shouldDenyActivateCategoryWithoutAuthentication() throws Exception {
        FinancialCategory category =
                saveCategory(
                        "Farm Category", saveFarm("Farm"), false, FinancialCategoryStatus.INACTIVE);

        mockMvc.perform(
                        patch("/api/financial/categories/{id}/activate", category.getId())
                                .contextPath(CONTEXT_PATH))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturnNotFoundWhenActivatingMissingCategoryAsAdmin() throws Exception {
        User admin = saveUser("Admin", "admin@example.com", UserType.ADMIN);

        mockMvc.perform(
                        patch("/api/financial/categories/{id}/activate", 999L)
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(admin.getId())).roles("ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldDenyProducerUpdatingFarmCategoryToGlobal() throws Exception {
        Farm farm = saveFarm("Farm");
        User producer = saveUser("Producer", "producer@example.com", UserType.USER);
        saveFarmUser(farm, producer, FarmUserRole.PRODUCER);
        FinancialCategory category =
                saveCategory("Farm Category", farm, false, FinancialCategoryStatus.ACTIVE);

        mockMvc.perform(
                        put("/api/financial/categories/{id}", category.getId())
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(producer.getId())).roles("USER"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(categoryBody(null, true)))
                .andExpect(status().isForbidden());

        FinancialCategory savedCategory =
                financialCategoryRepository.findById(category.getId()).orElseThrow();
        assertThat(savedCategory.getFarm().getId()).isEqualTo(farm.getId());
        assertThat(savedCategory.isDefaultCategory()).isFalse();
    }

    private void expectCannotListUsedCategories(User user, Farm farm) throws Exception {
        mockMvc.perform(
                        get("/api/financial/categories/used-in-transactions")
                                .contextPath(CONTEXT_PATH)
                                .param("farmId", String.valueOf(farm.getId()))
                                .with(user(String.valueOf(user.getId())).roles("USER")))
                .andExpect(status().isForbidden());
    }

    private void expectCanListCategories(User user, Farm farm) throws Exception {
        mockMvc.perform(
                        get("/api/financial/categories")
                                .contextPath(CONTEXT_PATH)
                                .param("farmId", String.valueOf(farm.getId()))
                                .with(user(String.valueOf(user.getId())).roles("USER")))
                .andExpect(status().isOk());
    }

    private void expectCannotListGlobalCategories(User user) throws Exception {
        mockMvc.perform(
                        get("/api/financial/categories/global")
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(user.getId())).roles("USER")))
                .andExpect(status().isForbidden());
    }

    private void expectCannotCreate(User user, Farm farm) throws Exception {
        mockMvc.perform(
                        post("/api/financial/categories")
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(user.getId())).roles("USER"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(categoryBody(farm.getId(), false)))
                .andExpect(status().isForbidden());
    }

    private void expectCannotActivate(User user, FinancialCategory category) throws Exception {
        mockMvc.perform(
                        patch("/api/financial/categories/{id}/activate", category.getId())
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(user.getId())).roles("USER")))
                .andExpect(status().isForbidden());
    }

    private String categoryBody(Long farmId, boolean defaultCategory) {
        return categoryBody("Insumos", farmId, defaultCategory);
    }

    private String categoryBody(String name, Long farmId, boolean defaultCategory) {
        return """
                {
                  "name": "%s",
                  "type": "EXPENSE",
                  "farmId": %s,
                  "isDefault": %s
                }
                """
                .formatted(name, farmId == null ? "null" : farmId, defaultCategory);
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

    private Farm saveFarm(String name) {
        Farm farm = new Farm();
        farm.setName(name);
        farm.setStatus(FarmStatus.ACTIVE);

        return farmRepository.save(farm);
    }

    private FarmUser saveFarmUser(Farm farm, User user, FarmUserRole role) {
        FarmUser farmUser = new FarmUser();
        farmUser.setFarm(farm);
        farmUser.setUser(user);
        farmUser.setRole(role);

        return farmUserRepository.save(farmUser);
    }

    private FinancialTransaction saveTransaction(
            Farm farm, FinancialCategory category, User user, FinancialRecordStatus recordStatus) {
        FinancialTransaction transaction = new FinancialTransaction();
        transaction.setDescription("Transaction " + category.getName());
        transaction.setAmount(BigDecimal.TEN);
        transaction.setType(category.getType());
        transaction.setStatus(PaymentStatus.PENDING);
        transaction.setTransactionDate(LocalDate.now());
        transaction.setFarm(farm);
        transaction.setCategory(category);
        transaction.setCreatedByUser(user);
        transaction.setRecordStatus(recordStatus);

        return financialTransactionRepository.save(transaction);
    }

    private FinancialCategory saveCategory(
            String name, Farm farm, boolean defaultCategory, FinancialCategoryStatus status) {
        FinancialCategory category = new FinancialCategory();
        category.setName(name);
        category.setType(TransactionType.EXPENSE);
        category.setFarm(farm);
        category.setDefaultCategory(defaultCategory);
        category.setStatus(status);

        return financialCategoryRepository.save(category);
    }
}
