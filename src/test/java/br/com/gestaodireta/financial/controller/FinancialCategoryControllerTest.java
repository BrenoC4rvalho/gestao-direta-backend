package br.com.gestaodireta.financial.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
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
            "A category with this name and type already exists for this farm.";

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
    void shouldReturnNotFoundForRemovedGlobalRoute() throws Exception {
        User admin = saveUser("Admin", "admin@example.com", UserType.ADMIN);

        mockMvc.perform(
                        get("/api/financial/categories/global")
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(admin.getId())).roles("ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldListOnlyFarmCategoriesByFarmId() throws Exception {
        Farm farm = saveFarm("Farm");
        Farm otherFarm = saveFarm("Other Farm");
        User producer = saveUser("Producer", "producer@example.com", UserType.USER);
        saveFarmUser(farm, producer, FarmUserRole.PRODUCER);
        saveCategory("Farm Active", farm, TransactionType.EXPENSE, FinancialCategoryStatus.ACTIVE);
        saveCategory(
                "Farm Inactive", farm, TransactionType.EXPENSE, FinancialCategoryStatus.INACTIVE);
        saveCategory(
                "Other Farm Active",
                otherFarm,
                TransactionType.EXPENSE,
                FinancialCategoryStatus.ACTIVE);

        mockMvc.perform(
                        get("/api/financial/categories")
                                .contextPath(CONTEXT_PATH)
                                .param("farmId", String.valueOf(farm.getId()))
                                .with(user(String.valueOf(producer.getId())).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].name").value(containsInAnyOrder("Farm Active")))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void shouldListActiveAndInactiveFarmCategoriesWhenIncludeInactiveIsTrue() throws Exception {
        Farm farm = saveFarm("Farm");
        Farm otherFarm = saveFarm("Other Farm");
        User producer = saveUser("Producer", "producer@example.com", UserType.USER);
        saveFarmUser(farm, producer, FarmUserRole.PRODUCER);
        saveCategory("Farm Active", farm, TransactionType.EXPENSE, FinancialCategoryStatus.ACTIVE);
        saveCategory(
                "Farm Inactive", farm, TransactionType.EXPENSE, FinancialCategoryStatus.INACTIVE);
        saveCategory(
                "Other Farm Inactive",
                otherFarm,
                TransactionType.EXPENSE,
                FinancialCategoryStatus.INACTIVE);

        mockMvc.perform(
                        get("/api/financial/categories")
                                .contextPath(CONTEXT_PATH)
                                .param("farmId", String.valueOf(farm.getId()))
                                .param("includeInactive", "true")
                                .with(user(String.valueOf(producer.getId())).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.content[*].name")
                                .value(containsInAnyOrder("Farm Active", "Farm Inactive")))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void shouldRequireFarmIdToListCategories() throws Exception {
        User admin = saveUser("Admin", "admin@example.com", UserType.ADMIN);

        mockMvc.perform(
                        get("/api/financial/categories")
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(admin.getId())).roles("ADMIN")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldListCategoriesUsedInActiveTransactionsByFarm() throws Exception {
        Farm farm = saveFarm("Farm");
        Farm otherFarm = saveFarm("Other Farm");
        User producer = saveUser("Producer", "producer@example.com", UserType.USER);
        saveFarmUser(farm, producer, FarmUserRole.PRODUCER);
        FinancialCategory farmUsed =
                saveCategory(
                        "A Farm Used",
                        farm,
                        TransactionType.EXPENSE,
                        FinancialCategoryStatus.ACTIVE);
        FinancialCategory inactiveUsed =
                saveCategory(
                        "B Inactive Used",
                        farm,
                        TransactionType.EXPENSE,
                        FinancialCategoryStatus.INACTIVE);
        FinancialCategory deletedOnly =
                saveCategory(
                        "Deleted Only",
                        farm,
                        TransactionType.EXPENSE,
                        FinancialCategoryStatus.ACTIVE);
        saveCategory("Unused", farm, TransactionType.EXPENSE, FinancialCategoryStatus.ACTIVE);
        FinancialCategory otherFarmUsed =
                saveCategory(
                        "Other Farm Used",
                        otherFarm,
                        TransactionType.EXPENSE,
                        FinancialCategoryStatus.ACTIVE);
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
                                .value(containsInAnyOrder("A Farm Used", "B Inactive Used")))
                .andExpect(jsonPath("$[*].status").value(containsInAnyOrder("ACTIVE", "INACTIVE")))
                .andExpect(jsonPath("$[0].farmId").value(farm.getId()));
    }

    @Test
    void shouldAllowEmployeeAndAccountantToListCategoriesWithFinancialAccess() throws Exception {
        Farm farm = saveFarm("Farm");
        User employee = saveUser("Employee", "employee@example.com", UserType.USER);
        User accountant = saveUser("Accountant", "accountant@example.com", UserType.USER);
        saveFarmUser(farm, employee, FarmUserRole.EMPLOYEE);
        saveFarmUser(farm, accountant, FarmUserRole.ACCOUNTANT);
        saveCategory("Farm Active", farm, TransactionType.EXPENSE, FinancialCategoryStatus.ACTIVE);

        expectCanListCategories(employee, farm);
        expectCanListCategories(accountant, farm);
    }

    @Test
    void shouldDenyCategoryListForUserWithoutFinancialAccess() throws Exception {
        Farm farm = saveFarm("Farm");
        User unlinked = saveUser("Unlinked", "unlinked@example.com", UserType.USER);

        mockMvc.perform(
                        get("/api/financial/categories")
                                .contextPath(CONTEXT_PATH)
                                .param("farmId", String.valueOf(farm.getId()))
                                .with(user(String.valueOf(unlinked.getId())).roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldCreateFarmCategoryAsAdminAndProducer() throws Exception {
        User admin = saveUser("Admin", "admin@example.com", UserType.ADMIN);
        Farm farm = saveFarm("Farm");
        User producer = saveUser("Producer", "producer@example.com", UserType.USER);
        saveFarmUser(farm, producer, FarmUserRole.PRODUCER);

        mockMvc.perform(
                        post("/api/financial/categories")
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(admin.getId())).roles("ADMIN"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(categoryBody("Insumos", farm.getId(), "EXPENSE")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.farmId").value(farm.getId()))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mockMvc.perform(
                        post("/api/financial/categories")
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(producer.getId())).roles("USER"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(categoryBody("Venda", farm.getId(), "INCOME")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.farmId").value(farm.getId()));
    }

    @Test
    void shouldDenyCreateForEmployeeAccountantInactiveLinkAndUnlinkedUser() throws Exception {
        Farm farm = saveFarm("Farm");
        User employee = saveUser("Employee", "employee@example.com", UserType.USER);
        User accountant = saveUser("Accountant", "accountant@example.com", UserType.USER);
        User inactive = saveUser("Inactive", "inactive@example.com", UserType.USER);
        User unlinked = saveUser("Unlinked", "unlinked@example.com", UserType.USER);
        saveFarmUser(farm, employee, FarmUserRole.EMPLOYEE);
        saveFarmUser(farm, accountant, FarmUserRole.ACCOUNTANT);
        saveFarmUser(farm, inactive, FarmUserRole.INACTIVE);

        expectCannotCreate(employee, farm);
        expectCannotCreate(accountant, farm);
        expectCannotCreate(inactive, farm);
        expectCannotCreate(unlinked, farm);
    }

    @Test
    void shouldRequireFarmIdToCreateCategory() throws Exception {
        User admin = saveUser("Admin", "admin@example.com", UserType.ADMIN);

        mockMvc.perform(
                        post("/api/financial/categories")
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(admin.getId())).roles("ADMIN"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "name": "Insumos",
                                          "type": "EXPENSE"
                                        }
                                        """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnBadRequestWhenCreatingDuplicateNameAndTypeInSameFarm() throws Exception {
        User admin = saveUser("Admin", "admin@example.com", UserType.ADMIN);
        Farm farm = saveFarm("Farm");
        saveCategory("Insumos", farm, TransactionType.EXPENSE, FinancialCategoryStatus.ACTIVE);

        mockMvc.perform(
                        post("/api/financial/categories")
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(admin.getId())).roles("ADMIN"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(categoryBody(" insumos ", farm.getId(), "EXPENSE")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(DUPLICATE_CATEGORY_MESSAGE));
    }

    @Test
    void shouldAllowSameNameAndTypeInDifferentFarms() throws Exception {
        User admin = saveUser("Admin", "admin@example.com", UserType.ADMIN);
        Farm farm = saveFarm("Farm");
        Farm otherFarm = saveFarm("Other Farm");
        saveCategory("Insumos", farm, TransactionType.EXPENSE, FinancialCategoryStatus.ACTIVE);

        mockMvc.perform(
                        post("/api/financial/categories")
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(admin.getId())).roles("ADMIN"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(categoryBody(" insumos ", otherFarm.getId(), "EXPENSE")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.farmId").value(otherFarm.getId()));
    }

    @Test
    void shouldUpdateCategoryWithoutChangingFarm() throws Exception {
        Farm farm = saveFarm("Farm");
        Farm otherFarm = saveFarm("Other Farm");
        User producer = saveUser("Producer", "producer@example.com", UserType.USER);
        saveFarmUser(farm, producer, FarmUserRole.PRODUCER);
        FinancialCategory category =
                saveCategory(
                        "Insumos", farm, TransactionType.EXPENSE, FinancialCategoryStatus.ACTIVE);

        mockMvc.perform(
                        put("/api/financial/categories/{id}", category.getId())
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(producer.getId())).roles("USER"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(updateBody("Venda", otherFarm.getId(), "INCOME")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Venda"))
                .andExpect(jsonPath("$.type").value("INCOME"))
                .andExpect(jsonPath("$.farmId").value(farm.getId()));

        FinancialCategory savedCategory =
                financialCategoryRepository.findById(category.getId()).orElseThrow();
        assertThat(savedCategory.getFarm().getId()).isEqualTo(farm.getId());
    }

    @Test
    void shouldReturnBadRequestWhenUpdatingDuplicateNameAndTypeInSameFarm() throws Exception {
        User admin = saveUser("Admin", "admin@example.com", UserType.ADMIN);
        Farm farm = saveFarm("Farm");
        FinancialCategory category =
                saveCategory(
                        "Insumos", farm, TransactionType.EXPENSE, FinancialCategoryStatus.ACTIVE);
        saveCategory("Frete", farm, TransactionType.EXPENSE, FinancialCategoryStatus.ACTIVE);

        mockMvc.perform(
                        put("/api/financial/categories/{id}", category.getId())
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(admin.getId())).roles("ADMIN"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(updateBody(" frete ", farm.getId(), "EXPENSE")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(DUPLICATE_CATEGORY_MESSAGE));
    }

    @Test
    void shouldGetCategoryByIdWhenUserCanViewFarm() throws Exception {
        Farm farm = saveFarm("Farm");
        User accountant = saveUser("Accountant", "accountant@example.com", UserType.USER);
        saveFarmUser(farm, accountant, FarmUserRole.ACCOUNTANT);
        FinancialCategory category =
                saveCategory(
                        "Insumos", farm, TransactionType.EXPENSE, FinancialCategoryStatus.ACTIVE);

        mockMvc.perform(
                        get("/api/financial/categories/{id}", category.getId())
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(accountant.getId())).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(category.getId()))
                .andExpect(jsonPath("$.farmId").value(farm.getId()));
    }

    @Test
    void shouldDenyGetCategoryByIdWhenUserCannotViewFarm() throws Exception {
        Farm farm = saveFarm("Farm");
        User unlinked = saveUser("Unlinked", "unlinked@example.com", UserType.USER);
        FinancialCategory category =
                saveCategory(
                        "Insumos", farm, TransactionType.EXPENSE, FinancialCategoryStatus.ACTIVE);

        mockMvc.perform(
                        get("/api/financial/categories/{id}", category.getId())
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(unlinked.getId())).roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldActivateAndDeleteCategoryAsProducer() throws Exception {
        Farm farm = saveFarm("Farm");
        User producer = saveUser("Producer", "producer@example.com", UserType.USER);
        saveFarmUser(farm, producer, FarmUserRole.PRODUCER);
        FinancialCategory category =
                saveCategory(
                        "Insumos", farm, TransactionType.EXPENSE, FinancialCategoryStatus.INACTIVE);

        mockMvc.perform(
                        patch("/api/financial/categories/{id}/activate", category.getId())
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(producer.getId())).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mockMvc.perform(
                        delete("/api/financial/categories/{id}", category.getId())
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(producer.getId())).roles("USER"))
                                .with(csrf()))
                .andExpect(status().isNoContent());

        FinancialCategory savedCategory =
                financialCategoryRepository.findById(category.getId()).orElseThrow();
        assertThat(savedCategory.getStatus()).isEqualTo(FinancialCategoryStatus.INACTIVE);
    }

    @Test
    void shouldDenyActivateAndDeleteForEmployeeAccountantAndUnlinkedUser() throws Exception {
        Farm farm = saveFarm("Farm");
        User employee = saveUser("Employee", "employee@example.com", UserType.USER);
        User accountant = saveUser("Accountant", "accountant@example.com", UserType.USER);
        User unlinked = saveUser("Unlinked", "unlinked@example.com", UserType.USER);
        saveFarmUser(farm, employee, FarmUserRole.EMPLOYEE);
        saveFarmUser(farm, accountant, FarmUserRole.ACCOUNTANT);
        FinancialCategory category =
                saveCategory(
                        "Insumos", farm, TransactionType.EXPENSE, FinancialCategoryStatus.INACTIVE);

        expectCannotActivate(employee, category);
        expectCannotActivate(accountant, category);
        expectCannotActivate(unlinked, category);
        expectCannotDelete(employee, category);
        expectCannotDelete(accountant, category);
        expectCannotDelete(unlinked, category);
    }

    @Test
    void shouldDenyManagementWhenFarmIsInactive() throws Exception {
        Farm farm = saveFarm("Farm");
        farm.setStatus(FarmStatus.INACTIVE);
        farmRepository.save(farm);
        User admin = saveUser("Admin", "admin@example.com", UserType.ADMIN);
        FinancialCategory category =
                saveCategory(
                        "Insumos", farm, TransactionType.EXPENSE, FinancialCategoryStatus.INACTIVE);

        mockMvc.perform(
                        post("/api/financial/categories")
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(admin.getId())).roles("ADMIN"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(categoryBody("Venda", farm.getId(), "INCOME")))
                .andExpect(status().isForbidden());

        mockMvc.perform(
                        patch("/api/financial/categories/{id}/activate", category.getId())
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(admin.getId())).roles("ADMIN")))
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

    private void expectCannotCreate(User user, Farm farm) throws Exception {
        mockMvc.perform(
                        post("/api/financial/categories")
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(user.getId())).roles("USER"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(categoryBody("Insumos", farm.getId(), "EXPENSE")))
                .andExpect(status().isForbidden());
    }

    private void expectCannotActivate(User user, FinancialCategory category) throws Exception {
        mockMvc.perform(
                        patch("/api/financial/categories/{id}/activate", category.getId())
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(user.getId())).roles("USER")))
                .andExpect(status().isForbidden());
    }

    private void expectCannotDelete(User user, FinancialCategory category) throws Exception {
        mockMvc.perform(
                        delete("/api/financial/categories/{id}", category.getId())
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(user.getId())).roles("USER"))
                                .with(csrf()))
                .andExpect(status().isForbidden());
    }

    private String categoryBody(String name, Long farmId, String type) {
        return """
                {
                  "farmId": %s,
                  "name": "%s",
                  "type": "%s",
                  "color": "#ff0000",
                  "icon": "package"
                }
                """
                .formatted(farmId, name, type);
    }

    private String updateBody(String name, Long ignoredFarmId, String type) {
        return """
                {
                  "farmId": %s,
                  "name": "%s",
                  "type": "%s",
                  "color": "#00ff00",
                  "icon": "wallet"
                }
                """
                .formatted(ignoredFarmId, name, type);
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
            String name, Farm farm, TransactionType type, FinancialCategoryStatus status) {
        FinancialCategory category = new FinancialCategory();
        category.setName(name);
        category.setType(type);
        category.setFarm(farm);
        category.setStatus(status);

        return financialCategoryRepository.save(category);
    }
}
