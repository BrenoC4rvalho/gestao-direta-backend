package br.com.gestaodireta.financial.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import br.com.gestaodireta.financial.enumeration.FinancialCategoryStatus;
import br.com.gestaodireta.financial.enumeration.TransactionType;
import br.com.gestaodireta.financial.repository.FinancialCategoryRepository;
import br.com.gestaodireta.financial.repository.FinancialTransactionRepository;
import br.com.gestaodireta.support.PostgresIntegrationTest;
import br.com.gestaodireta.user.entity.User;
import br.com.gestaodireta.user.enumeration.UserStatus;
import br.com.gestaodireta.user.enumeration.UserType;
import br.com.gestaodireta.user.repository.UserRepository;
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

    private String categoryBody(Long farmId, boolean defaultCategory) {
        return """
                {
                  "name": "Insumos",
                  "type": "EXPENSE",
                  "farmId": %s,
                  "isDefault": %s
                }
                """
                .formatted(farmId == null ? "null" : farmId, defaultCategory);
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
