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

    private FinancialTransaction saveTransaction(Farm farm, User user) {
        FinancialTransaction transaction = new FinancialTransaction();
        transaction.setDescription("Transaction");
        transaction.setAmount(BigDecimal.TEN);
        transaction.setType(TransactionType.EXPENSE);
        transaction.setStatus(PaymentStatus.PENDING);
        transaction.setTransactionDate(LocalDate.now());
        transaction.setFarm(farm);
        transaction.setCreatedByUser(user);
        transaction.setRecordStatus(FinancialRecordStatus.ACTIVE);

        return financialTransactionRepository.save(transaction);
    }
}
