package br.com.gestaodireta.financial.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.gestaodireta.farm.entity.Farm;
import br.com.gestaodireta.farm.entity.FarmUser;
import br.com.gestaodireta.farm.enumeration.FarmStatus;
import br.com.gestaodireta.farm.enumeration.FarmUserRole;
import br.com.gestaodireta.farm.repository.FarmRepository;
import br.com.gestaodireta.farm.repository.FarmUserRepository;
import br.com.gestaodireta.financial.entity.FinancialTransaction;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class FinancialAlertControllerTest extends PostgresIntegrationTest {

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
    void shouldReturnAlertsForAuthorizedUser() throws Exception {
        Farm farm = saveFarm();
        User accountant = saveUser("Accountant", "alerts-accountant@example.com");
        saveFarmUser(farm, accountant, FarmUserRole.ACCOUNTANT);
        saveTransaction(farm, accountant);

        mockMvc.perform(
                        get("/api/financial/alerts")
                                .contextPath(CONTEXT_PATH)
                                .param("farmId", String.valueOf(farm.getId()))
                                .with(user(String.valueOf(accountant.getId())).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.farmId").value(farm.getId()))
                .andExpect(jsonPath("$.overdueBills").isArray())
                .andExpect(jsonPath("$.dueToday").exists())
                .andExpect(jsonPath("$.dueToday.count").exists())
                .andExpect(jsonPath("$.dueToday.totalAmount").exists())
                .andExpect(jsonPath("$.dueNext7Days").exists())
                .andExpect(jsonPath("$.dueNext7Days.count").exists())
                .andExpect(jsonPath("$.dueNext7Days.totalAmount").exists());
    }

    @Test
    void shouldRejectUserWithoutFarmAccessForAlerts() throws Exception {
        Farm farm = saveFarm();
        User unlinked = saveUser("Unlinked", "alerts-unlinked@example.com");

        mockMvc.perform(
                        get("/api/financial/alerts")
                                .contextPath(CONTEXT_PATH)
                                .param("farmId", String.valueOf(farm.getId()))
                                .with(user(String.valueOf(unlinked.getId())).roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldRejectUnauthenticatedUserForAlerts() throws Exception {
        Farm farm = saveFarm();

        mockMvc.perform(
                        get("/api/financial/alerts")
                                .contextPath(CONTEXT_PATH)
                                .param("farmId", String.valueOf(farm.getId())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldUseStandardForbiddenResponseWhenFarmDoesNotExistForRegularUser() throws Exception {
        User user = saveUser("User", "alerts-missing-farm@example.com");

        mockMvc.perform(
                        get("/api/financial/alerts")
                                .contextPath(CONTEXT_PATH)
                                .param("farmId", "999999")
                                .with(user(String.valueOf(user.getId())).roles("USER")))
                .andExpect(status().isForbidden());
    }

    private Farm saveFarm() {
        Farm farm = new Farm();
        farm.setName("Farm");
        farm.setStatus(FarmStatus.ACTIVE);

        return farmRepository.save(farm);
    }

    private User saveUser(String name, String email) {
        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode("Strong1!"));
        user.setUserType(UserType.USER);
        user.setStatus(UserStatus.ACTIVE);

        return userRepository.save(user);
    }

    private FarmUser saveFarmUser(Farm farm, User user, FarmUserRole role) {
        FarmUser farmUser = new FarmUser();
        farmUser.setFarm(farm);
        farmUser.setUser(user);
        farmUser.setRole(role);

        return farmUserRepository.save(farmUser);
    }

    private FinancialTransaction saveTransaction(Farm farm, User user) {
        FinancialTransaction transaction = new FinancialTransaction();
        transaction.setDescription("Transaction");
        transaction.setAmount(BigDecimal.TEN);
        transaction.setType(TransactionType.EXPENSE);
        transaction.setStatus(PaymentStatus.PENDING);
        transaction.setTransactionDate(LocalDate.now());
        transaction.setDueDate(LocalDate.now());
        transaction.setFarm(farm);
        transaction.setCreatedByUser(user);
        transaction.setRecordStatus(FinancialRecordStatus.ACTIVE);

        return financialTransactionRepository.save(transaction);
    }
}
