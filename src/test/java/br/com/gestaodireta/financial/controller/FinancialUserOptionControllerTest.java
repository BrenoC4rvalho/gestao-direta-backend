package br.com.gestaodireta.financial.controller;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
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
class FinancialUserOptionControllerTest extends PostgresIntegrationTest {

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
    void shouldListUserOptionsForTransactionFilterWithLeanPayload() throws Exception {
        Farm farm = saveFarm("Farm");
        Farm otherFarm = saveFarm("Other Farm");
        User requester = saveUser("Requester", "requester@example.com", UserType.USER);
        User activeLinked = saveUser("A Active Linked", "active-linked@example.com", UserType.USER);
        User inactiveCreator =
                saveUser("B Inactive Creator", "inactive-creator@example.com", UserType.USER);
        User inactiveLinked =
                saveUser("Inactive Linked", "inactive-linked@example.com", UserType.USER);
        User deletedOnly = saveUser("Deleted Only", "deleted-only@example.com", UserType.USER);
        User otherFarmCreator =
                saveUser("Other Farm Creator", "other-farm@example.com", UserType.USER);

        inactiveCreator.setStatus(UserStatus.INACTIVE);
        inactiveLinked.setStatus(UserStatus.INACTIVE);
        userRepository.save(inactiveCreator);
        userRepository.save(inactiveLinked);

        saveFarmUser(farm, requester, FarmUserRole.PRODUCER);
        saveFarmUser(farm, activeLinked, FarmUserRole.EMPLOYEE);
        saveFarmUser(farm, inactiveLinked, FarmUserRole.PRODUCER);
        saveTransaction(farm, inactiveCreator, FinancialRecordStatus.ACTIVE);
        saveTransaction(farm, deletedOnly, FinancialRecordStatus.DELETED);
        saveTransaction(otherFarm, otherFarmCreator, FinancialRecordStatus.ACTIVE);

        mockMvc.perform(
                        get("/api/farms/{farmId}/users/options", farm.getId())
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(requester.getId())).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$[*].name")
                                .value(
                                        contains(
                                                "A Active Linked",
                                                "B Inactive Creator",
                                                "Requester")))
                .andExpect(jsonPath("$[*].name").value(not(hasItem("Inactive Linked"))))
                .andExpect(jsonPath("$[*].name").value(not(hasItem("Deleted Only"))))
                .andExpect(jsonPath("$[*].name").value(not(hasItem("Other Farm Creator"))))
                .andExpect(jsonPath("$[0].id").value(activeLinked.getId()))
                .andExpect(jsonPath("$[0].email").doesNotExist())
                .andExpect(jsonPath("$[0].document").doesNotExist())
                .andExpect(jsonPath("$[0].userType").doesNotExist())
                .andExpect(jsonPath("$[0].status").doesNotExist())
                .andExpect(jsonPath("$[0].role").doesNotExist())
                .andExpect(jsonPath("$[0].farmId").doesNotExist());
    }

    @Test
    void shouldAllowAdminProducerEmployeeAndAccountantToListOptions() throws Exception {
        Farm farm = saveFarm("Farm");
        User admin = saveUser("Admin", "admin@example.com", UserType.ADMIN);
        User producer = saveUser("Producer", "producer@example.com", UserType.USER);
        User employee = saveUser("Employee", "employee@example.com", UserType.USER);
        User accountant = saveUser("Accountant", "accountant@example.com", UserType.USER);
        saveFarmUser(farm, producer, FarmUserRole.PRODUCER);
        saveFarmUser(farm, employee, FarmUserRole.EMPLOYEE);
        saveFarmUser(farm, accountant, FarmUserRole.ACCOUNTANT);

        expectCanListOptions(admin, farm, "ADMIN");
        expectCanListOptions(producer, farm, "USER");
        expectCanListOptions(employee, farm, "USER");
        expectCanListOptions(accountant, farm, "USER");
    }

    @Test
    void shouldDenyUserOptionsWithoutAuthentication() throws Exception {
        Farm farm = saveFarm("Farm");

        mockMvc.perform(
                        get("/api/farms/{farmId}/users/options", farm.getId())
                                .contextPath(CONTEXT_PATH))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldDenyUserOptionsForUserWithoutActiveFarmLink() throws Exception {
        Farm farm = saveFarm("Farm");
        User unlinked = saveUser("Unlinked", "unlinked@example.com", UserType.USER);
        User inactiveLink = saveUser("Inactive Link", "inactive-link@example.com", UserType.USER);
        saveFarmUser(farm, inactiveLink, FarmUserRole.INACTIVE);

        expectCannotListOptions(unlinked, farm);
        expectCannotListOptions(inactiveLink, farm);
    }

    @Test
    void shouldReturnNotFoundWhenOptionsFarmDoesNotExist() throws Exception {
        User user = saveUser("User", "user@example.com", UserType.USER);

        mockMvc.perform(
                        get("/api/farms/{farmId}/users/options", 999L)
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(user.getId())).roles("USER")))
                .andExpect(status().isNotFound());
    }

    private void expectCanListOptions(User user, Farm farm, String role) throws Exception {
        mockMvc.perform(
                        get("/api/farms/{farmId}/users/options", farm.getId())
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(user.getId())).roles(role)))
                .andExpect(status().isOk());
    }

    private void expectCannotListOptions(User user, Farm farm) throws Exception {
        mockMvc.perform(
                        get("/api/farms/{farmId}/users/options", farm.getId())
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(user.getId())).roles("USER")))
                .andExpect(status().isForbidden());
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
            Farm farm, User user, FinancialRecordStatus recordStatus) {
        FinancialTransaction transaction = new FinancialTransaction();
        transaction.setDescription("Transaction " + user.getName());
        transaction.setAmount(BigDecimal.TEN);
        transaction.setType(TransactionType.EXPENSE);
        transaction.setStatus(PaymentStatus.PENDING);
        transaction.setTransactionDate(LocalDate.now());
        transaction.setFarm(farm);
        transaction.setCreatedByUser(user);
        transaction.setRecordStatus(recordStatus);

        return financialTransactionRepository.save(transaction);
    }
}
