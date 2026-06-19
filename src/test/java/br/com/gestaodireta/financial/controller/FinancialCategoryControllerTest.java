package br.com.gestaodireta.financial.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import br.com.gestaodireta.financial.enumeration.FinancialCategoryStatus;
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
    void shouldCreateCategoryAsAdminOrProducerOnlyAndDeleteLogically() throws Exception {
        Farm farm = saveFarm();
        User admin = saveUser("Admin", "admin@example.com", UserType.ADMIN);
        User producer = saveUser("Producer", "producer@example.com", UserType.USER);
        User employee = saveUser("Employee", "employee@example.com", UserType.USER);
        User accountant = saveUser("Accountant", "accountant@example.com", UserType.USER);
        saveFarmUser(farm, producer, FarmUserRole.PRODUCER);
        saveFarmUser(farm, employee, FarmUserRole.EMPLOYEE);
        saveFarmUser(farm, accountant, FarmUserRole.ACCOUNTANT);

        mockMvc.perform(
                        post("/api/financial/categories")
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(admin.getId())).roles("ADMIN"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(categoryBody(farm.getId(), false)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mockMvc.perform(
                        post("/api/financial/categories")
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(producer.getId())).roles("USER"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(categoryBody(farm.getId(), false)))
                .andExpect(status().isCreated());

        expectCannotCreate(employee, farm);
        expectCannotCreate(accountant, farm);

        FinancialCategory category = financialCategoryRepository.findAll().getFirst();

        mockMvc.perform(
                        delete("/api/financial/categories/{id}", category.getId())
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(admin.getId())).roles("ADMIN"))
                                .with(csrf()))
                .andExpect(status().isNoContent());

        FinancialCategory savedCategory =
                financialCategoryRepository.findById(category.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(savedCategory.getStatus())
                .isEqualTo(FinancialCategoryStatus.INACTIVE);
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

    private Farm saveFarm() {
        Farm farm = new Farm();
        farm.setName("Farm");
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
}
