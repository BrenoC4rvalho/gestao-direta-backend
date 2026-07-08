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
import br.com.gestaodireta.financial.entity.FinancialCategory;
import br.com.gestaodireta.financial.entity.FinancialTransaction;
import br.com.gestaodireta.financial.enumeration.FinancialCategoryStatus;
import br.com.gestaodireta.financial.enumeration.FinancialRecordStatus;
import br.com.gestaodireta.financial.enumeration.PaymentStatus;
import br.com.gestaodireta.financial.enumeration.TransactionType;
import br.com.gestaodireta.financial.repository.FinancialCategoryRepository;
import br.com.gestaodireta.financial.repository.FinancialTransactionRepository;
import br.com.gestaodireta.harvest.entity.HarvestSeason;
import br.com.gestaodireta.harvest.entity.ProductionActivity;
import br.com.gestaodireta.harvest.enumeration.HarvestSeasonStatus;
import br.com.gestaodireta.harvest.enumeration.ProductionActivityStatus;
import br.com.gestaodireta.harvest.repository.HarvestSeasonRepository;
import br.com.gestaodireta.harvest.repository.ProductionActivityRepository;
import br.com.gestaodireta.support.PostgresIntegrationTest;
import br.com.gestaodireta.user.entity.User;
import br.com.gestaodireta.user.enumeration.UserStatus;
import br.com.gestaodireta.user.enumeration.UserType;
import br.com.gestaodireta.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class FinancialAgendaControllerTest extends PostgresIntegrationTest {

    private static final String CONTEXT_PATH = "/api";

    private static final LocalDate TODAY = LocalDate.of(2026, 7, 7);

    @Autowired private MockMvc mockMvc;

    @Autowired private FinancialTransactionRepository financialTransactionRepository;

    @Autowired private FinancialCategoryRepository financialCategoryRepository;

    @Autowired private HarvestSeasonRepository harvestSeasonRepository;

    @Autowired private ProductionActivityRepository productionActivityRepository;

    @Autowired private FarmUserRepository farmUserRepository;

    @Autowired private FarmRepository farmRepository;

    @Autowired private UserRepository userRepository;

    @Autowired private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        financialTransactionRepository.deleteAll();
        financialCategoryRepository.deleteAll();
        harvestSeasonRepository.deleteAll();
        productionActivityRepository.deleteAll();
        farmUserRepository.deleteAll();
        farmRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void shouldReturnSummaryAndPagedAgendaForAuthorizedUser() throws Exception {
        Farm farm = saveFarm();
        User accountant = saveUser("Accountant", "agenda-accountant@example.com", UserType.USER);
        saveFarmUser(farm, accountant, FarmUserRole.ACCOUNTANT);
        FinancialCategory incomeCategory = saveCategory(farm, "Income", TransactionType.INCOME);
        FinancialCategory expenseCategory = saveCategory(farm, "Expense", TransactionType.EXPENSE);
        HarvestSeason firstSeason = saveSeason(farm, "First Season");
        HarvestSeason secondSeason = saveSeason(farm, "Second Season");
        saveTransaction(
                farm,
                accountant,
                incomeCategory,
                firstSeason,
                TransactionType.INCOME,
                PaymentStatus.PENDING,
                "100.00",
                TODAY.minusDays(2));
        saveTransaction(
                farm,
                accountant,
                expenseCategory,
                secondSeason,
                TransactionType.EXPENSE,
                PaymentStatus.PENDING,
                "50.00",
                TODAY);

        mockMvc.perform(
                        get("/api/financial/agenda/summary")
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(accountant.getId())).roles("USER"))
                                .param("farmId", String.valueOf(farm.getId()))
                                .param("status", "ALL")
                                .param("type", "ALL")
                                .param("periodDays", "7")
                                .param("harvestSeasonIds", String.valueOf(firstSeason.getId()))
                                .param("harvestSeasonIds", String.valueOf(secondSeason.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.farmId").value(farm.getId()))
                .andExpect(jsonPath("$.overdueReceivable.count").value(1))
                .andExpect(jsonPath("$.overdueReceivable.totalAmount").value(100.00))
                .andExpect(jsonPath("$.pendingPayable.count").value(1))
                .andExpect(jsonPath("$.pendingPayable.totalAmount").value(50.00))
                .andExpect(jsonPath("$.openReceivable.totalAmount").value(100.00))
                .andExpect(jsonPath("$.openPayable.totalAmount").value(50.00));

        mockMvc.perform(
                        get("/api/financial/agenda")
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(accountant.getId())).roles("USER"))
                                .param("farmId", String.valueOf(farm.getId()))
                                .param("page", "0")
                                .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].agendaType").value("RECEIVABLE"))
                .andExpect(jsonPath("$.content[0].agendaStatus").value("OVERDUE"))
                .andExpect(jsonPath("$.content[0].paymentStatus").value("PENDING"))
                .andExpect(jsonPath("$.content[0].daysOverdue").value(2))
                .andExpect(jsonPath("$.content[0].daysUntilDue").doesNotExist())
                .andExpect(jsonPath("$.content[0].harvestSeasonName").value("First Season"))
                .andExpect(jsonPath("$.content[1].agendaType").value("PAYABLE"))
                .andExpect(jsonPath("$.content[1].agendaStatus").value("PENDING"))
                .andExpect(jsonPath("$.content[1].daysUntilDue").value(0));
    }

    @Test
    void shouldRejectInactiveOrUnlinkedUserForAgenda() throws Exception {
        Farm farm = saveFarm();
        User inactive = saveUser("Inactive", "agenda-inactive@example.com", UserType.USER);
        User unlinked = saveUser("Unlinked", "agenda-unlinked@example.com", UserType.USER);
        saveFarmUser(farm, inactive, FarmUserRole.INACTIVE);

        mockMvc.perform(
                        get("/api/financial/agenda/summary")
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(inactive.getId())).roles("USER"))
                                .param("farmId", String.valueOf(farm.getId())))
                .andExpect(status().isForbidden());

        mockMvc.perform(
                        get("/api/financial/agenda")
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(unlinked.getId())).roles("USER"))
                                .param("farmId", String.valueOf(farm.getId())))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldRejectUnauthenticatedMissingFarmAndInvalidEnums() throws Exception {
        Farm farm = saveFarm();
        User admin = saveUser("Admin", "agenda-admin@example.com", UserType.ADMIN);

        mockMvc.perform(
                        get("/api/financial/agenda/summary")
                                .contextPath(CONTEXT_PATH)
                                .param("farmId", String.valueOf(farm.getId())))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(
                        get("/api/financial/agenda")
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(admin.getId())).roles("ADMIN")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(
                        get("/api/financial/agenda/summary")
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(admin.getId())).roles("ADMIN"))
                                .param("farmId", String.valueOf(farm.getId()))
                                .param("status", "INVALID"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(
                        get("/api/financial/agenda")
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(admin.getId())).roles("ADMIN"))
                                .param("farmId", String.valueOf(farm.getId()))
                                .param("type", "INVALID"))
                .andExpect(status().isBadRequest());
    }

    private Farm saveFarm() {
        Farm farm = new Farm();
        farm.setName("Farm");
        farm.setStatus(FarmStatus.ACTIVE);

        return farmRepository.save(farm);
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

    private FarmUser saveFarmUser(Farm farm, User user, FarmUserRole role) {
        FarmUser farmUser = new FarmUser();
        farmUser.setFarm(farm);
        farmUser.setUser(user);
        farmUser.setRole(role);

        return farmUserRepository.save(farmUser);
    }

    private FinancialCategory saveCategory(Farm farm, String name, TransactionType type) {
        FinancialCategory category = new FinancialCategory();
        category.setFarm(farm);
        category.setName(name);
        category.setType(type);
        category.setStatus(FinancialCategoryStatus.ACTIVE);

        return financialCategoryRepository.save(category);
    }

    private HarvestSeason saveSeason(Farm farm, String name) {
        ProductionActivity activity = new ProductionActivity();
        activity.setFarm(farm);
        activity.setName(name + " Activity");
        activity.setStatus(ProductionActivityStatus.ACTIVE);
        productionActivityRepository.save(activity);

        HarvestSeason season = new HarvestSeason();
        season.setFarm(farm);
        season.setProductionActivity(activity);
        season.setName(name);
        season.setStartDate(TODAY.minusMonths(1));
        season.setStatus(HarvestSeasonStatus.PLANNED);

        return harvestSeasonRepository.save(season);
    }

    private FinancialTransaction saveTransaction(
            Farm farm,
            User user,
            FinancialCategory category,
            HarvestSeason harvestSeason,
            TransactionType type,
            PaymentStatus status,
            String amount,
            LocalDate dueDate) {
        FinancialTransaction transaction = new FinancialTransaction();
        transaction.setDescription("Transaction");
        transaction.setAmount(new BigDecimal(amount));
        transaction.setType(type);
        transaction.setStatus(status);
        transaction.setTransactionDate(dueDate);
        transaction.setDueDate(dueDate);
        transaction.setFarm(farm);
        transaction.setCategory(category);
        transaction.setHarvestSeason(harvestSeason);
        transaction.setCreatedByUser(user);
        transaction.setRecordStatus(FinancialRecordStatus.ACTIVE);

        return financialTransactionRepository.save(transaction);
    }

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(
                    Instant.parse("2026-07-07T03:00:00Z"), ZoneId.of("America/Sao_Paulo"));
        }
    }
}
