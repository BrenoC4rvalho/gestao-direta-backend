package br.com.gestaodireta.ai.web.rest;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.gestaodireta.farm.entity.Farm;
import br.com.gestaodireta.farm.entity.FarmUser;
import br.com.gestaodireta.farm.enumeration.FarmStatus;
import br.com.gestaodireta.farm.enumeration.FarmUserRole;
import br.com.gestaodireta.farm.repository.FarmRepository;
import br.com.gestaodireta.farm.repository.FarmUserRepository;
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
class AiTransactionControllerTest extends PostgresIntegrationTest {

    private static final String CONTEXT_PATH = "/api";

    @Autowired private MockMvc mockMvc;

    @Autowired private FinancialTransactionRepository financialTransactionRepository;

    @Autowired private FarmUserRepository farmUserRepository;

    @Autowired private FarmRepository farmRepository;

    @Autowired private UserRepository userRepository;

    @Autowired private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        financialTransactionRepository.deleteAll();
        farmUserRepository.deleteAll();
        farmRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void shouldParseTransactionTextAsAdminProducerAndEmployeeWithoutSavingTransaction()
            throws Exception {
        Farm farm = saveFarm(FarmStatus.ACTIVE);
        User admin = saveUser("Admin", "ai-admin@example.com", UserType.ADMIN, UserStatus.ACTIVE);
        User producer =
                saveUser("Producer", "ai-producer@example.com", UserType.USER, UserStatus.ACTIVE);
        User employee =
                saveUser("Employee", "ai-employee@example.com", UserType.USER, UserStatus.ACTIVE);
        saveFarmUser(farm, producer, FarmUserRole.PRODUCER);
        saveFarmUser(farm, employee, FarmUserRole.EMPLOYEE);

        expectCanParse(admin, "ADMIN", farm);
        expectCanParse(producer, "USER", farm);
        expectCanParse(employee, "USER", farm);
    }

    @Test
    void shouldRejectAccountantInactiveOrUnlinkedUser() throws Exception {
        Farm farm = saveFarm(FarmStatus.ACTIVE);
        User accountant =
                saveUser(
                        "Accountant",
                        "ai-accountant@example.com",
                        UserType.USER,
                        UserStatus.ACTIVE);
        User inactiveRole =
                saveUser("Inactive", "ai-inactive@example.com", UserType.USER, UserStatus.ACTIVE);
        User unlinked =
                saveUser("Unlinked", "ai-unlinked@example.com", UserType.USER, UserStatus.ACTIVE);
        saveFarmUser(farm, accountant, FarmUserRole.ACCOUNTANT);
        saveFarmUser(farm, inactiveRole, FarmUserRole.INACTIVE);

        expectCannotParse(accountant, farm);
        expectCannotParse(inactiveRole, farm);
        expectCannotParse(unlinked, farm);
    }

    @Test
    void shouldValidateRequiredFieldsAndTextSize() throws Exception {
        Farm farm = saveFarm(FarmStatus.ACTIVE);
        User admin =
                saveUser(
                        "Admin Validation",
                        "ai-admin-validation@example.com",
                        UserType.ADMIN,
                        UserStatus.ACTIVE);

        mockMvc.perform(
                        post("/api/ai/transactions/parse")
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(admin.getId())).roles("ADMIN"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"text\":\"paguei 250 no pix\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(
                        post("/api/ai/transactions/parse")
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(admin.getId())).roles("ADMIN"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(parseBody(farm.getId(), "")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(
                        post("/api/ai/transactions/parse")
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(admin.getId())).roles("ADMIN"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(parseBody(farm.getId(), "a".repeat(2001))))
                .andExpect(status().isBadRequest());
    }

    private void expectCanParse(User user, String role, Farm farm) throws Exception {
        long transactionCount = financialTransactionRepository.count();

        mockMvc.perform(
                        post("/api/ai/transactions/parse")
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(user.getId())).roles(role))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        parseBody(
                                                farm.getId(),
                                                "paguei 250 reais de adubo ontem no pix")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.farmId").value(farm.getId()))
                .andExpect(jsonPath("$.type").value("EXPENSE"))
                .andExpect(jsonPath("$.amount").value(250.00))
                .andExpect(jsonPath("$.paymentStatus").value("PAID"))
                .andExpect(jsonPath("$.paymentMethod").value("PIX"))
                .andExpect(jsonPath("$.categoryName").value("Insumos"))
                .andExpect(jsonPath("$.harvestSeasonName").value("Milho"));

        org.assertj.core.api.Assertions.assertThat(financialTransactionRepository.count())
                .isEqualTo(transactionCount);
    }

    private void expectCannotParse(User user, Farm farm) throws Exception {
        mockMvc.perform(
                        post("/api/ai/transactions/parse")
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(user.getId())).roles("USER"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(parseBody(farm.getId(), "paguei 250 no pix")))
                .andExpect(status().isForbidden());
    }

    private String parseBody(Long farmId, String text) {
        return """
                {
                  "farmId": %d,
                  "text": "%s"
                }
                """
                .formatted(farmId, text);
    }

    private User saveUser(String name, String email, UserType userType, UserStatus status) {
        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode("Strong1!"));
        user.setUserType(userType);
        user.setStatus(status);

        return userRepository.save(user);
    }

    private Farm saveFarm(FarmStatus status) {
        Farm farm = new Farm();
        farm.setName("AI Farm");
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
}
