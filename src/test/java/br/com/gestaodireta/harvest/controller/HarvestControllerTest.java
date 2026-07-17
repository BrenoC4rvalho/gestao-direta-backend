package br.com.gestaodireta.harvest.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
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
import br.com.gestaodireta.financial.entity.FinancialTransaction;
import br.com.gestaodireta.financial.enumeration.FinancialRecordStatus;
import br.com.gestaodireta.financial.enumeration.PaymentStatus;
import br.com.gestaodireta.financial.enumeration.TransactionType;
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
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class HarvestControllerTest extends PostgresIntegrationTest {

    private static final String CONTEXT_PATH = "/api";

    @Autowired private MockMvc mockMvc;

    @Autowired private FinancialTransactionRepository financialTransactionRepository;

    @Autowired private HarvestSeasonRepository harvestSeasonRepository;

    @Autowired private ProductionActivityRepository productionActivityRepository;

    @Autowired private FarmUserRepository farmUserRepository;

    @Autowired private FarmRepository farmRepository;

    @Autowired private UserRepository userRepository;

    @Autowired private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        financialTransactionRepository.deleteAll();
        harvestSeasonRepository.deleteAll();
        productionActivityRepository.deleteAll();
        farmUserRepository.deleteAll();
        farmRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void shouldManageProductionActivitiesAsAdmin() throws Exception {
        Farm farm = saveFarm("Farm", FarmStatus.ACTIVE);
        User admin = saveUser("Admin", "admin@example.com", UserType.ADMIN);

        mockMvc.perform(
                        post("/api/harvest/production-activities")
                                .contextPath(CONTEXT_PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(activityBody(farm.getId(), "Soja"))
                                .with(user(String.valueOf(admin.getId())).roles("ADMIN")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.farmId").value(farm.getId()))
                .andExpect(jsonPath("$.farmName").value("Farm"))
                .andExpect(jsonPath("$.name").value("Soja"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        ProductionActivity activity = productionActivityRepository.findAll().getFirst();

        mockMvc.perform(
                        put("/api/harvest/production-activities/{id}", activity.getId())
                                .contextPath(CONTEXT_PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(updateActivityBody("Soja verão"))
                                .with(user(String.valueOf(admin.getId())).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.farmId").value(farm.getId()))
                .andExpect(jsonPath("$.name").value("Soja verão"));

        mockMvc.perform(
                        delete("/api/harvest/production-activities/{id}", activity.getId())
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(admin.getId())).roles("ADMIN")))
                .andExpect(status().isNoContent());

        assertThat(productionActivityRepository.findById(activity.getId()))
                .get()
                .extracting(ProductionActivity::getStatus)
                .isEqualTo(ProductionActivityStatus.INACTIVE);

        mockMvc.perform(
                        patch("/api/harvest/production-activities/{id}/activate", activity.getId())
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(admin.getId())).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void shouldAllowProducerAndDenyReadOnlyUsersToCreateProductionActivities() throws Exception {
        Farm farm = saveFarm("Farm", FarmStatus.ACTIVE);
        User producer = saveUser("Producer", "producer@example.com", UserType.USER);
        User employee = saveUser("Employee", "employee@example.com", UserType.USER);
        User accountant = saveUser("Accountant", "accountant@example.com", UserType.USER);
        User unlinked = saveUser("Unlinked", "unlinked@example.com", UserType.USER);
        saveFarmUser(farm, producer, FarmUserRole.PRODUCER);
        saveFarmUser(farm, employee, FarmUserRole.EMPLOYEE);
        saveFarmUser(farm, accountant, FarmUserRole.ACCOUNTANT);

        mockMvc.perform(
                        post("/api/harvest/production-activities")
                                .contextPath(CONTEXT_PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(activityBody(farm.getId(), "Milho"))
                                .with(user(String.valueOf(producer.getId())).roles("USER")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.farmId").value(farm.getId()))
                .andExpect(jsonPath("$.name").value("Milho"));

        expectCannotCreateActivity(farm, employee, "Trigo");
        expectCannotCreateActivity(farm, accountant, "Arroz");
        expectCannotCreateActivity(farm, unlinked, "Café");
    }

    @Test
    void shouldListOnlyFarmProductionActivitiesForLinkedUsers() throws Exception {
        Farm farm = saveFarm("Farm", FarmStatus.ACTIVE);
        Farm otherFarm = saveFarm("Other Farm", FarmStatus.ACTIVE);
        User producer = saveUser("Producer", "producer@example.com", UserType.USER);
        User unlinked = saveUser("Unlinked", "unlinked@example.com", UserType.USER);
        saveFarmUser(farm, producer, FarmUserRole.PRODUCER);
        saveActivity(farm, "Soja", ProductionActivityStatus.ACTIVE);
        saveActivity(farm, "Milho", ProductionActivityStatus.ACTIVE);
        saveActivity(farm, "Café", ProductionActivityStatus.INACTIVE);
        saveActivity(otherFarm, "Tomate", ProductionActivityStatus.ACTIVE);

        mockMvc.perform(
                        get("/api/harvest/production-activities/active")
                                .contextPath(CONTEXT_PATH)
                                .param("farmId", String.valueOf(farm.getId()))
                                .with(user(String.valueOf(producer.getId())).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name").value(containsInAnyOrder("Soja", "Milho")));

        mockMvc.perform(
                        get("/api/harvest/production-activities")
                                .contextPath(CONTEXT_PATH)
                                .param("farmId", String.valueOf(farm.getId()))
                                .with(user(String.valueOf(producer.getId())).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.content[*].name")
                                .value(containsInAnyOrder("Soja", "Milho", "Café")))
                .andExpect(jsonPath("$.totalElements").value(3));

        mockMvc.perform(
                        get("/api/harvest/production-activities")
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(producer.getId())).roles("USER")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(
                        get("/api/harvest/production-activities/active")
                                .contextPath(CONTEXT_PATH)
                                .param("farmId", String.valueOf(farm.getId()))
                                .with(user(String.valueOf(unlinked.getId())).roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldValidateProductionActivityDuplicateNameByFarm() throws Exception {
        Farm farm = saveFarm("Farm", FarmStatus.ACTIVE);
        Farm otherFarm = saveFarm("Other Farm", FarmStatus.ACTIVE);
        User admin = saveUser("Admin", "admin@example.com", UserType.ADMIN);
        saveActivity(farm, "Soja", ProductionActivityStatus.ACTIVE);

        mockMvc.perform(
                        post("/api/harvest/production-activities")
                                .contextPath(CONTEXT_PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(activityBody(farm.getId(), " soja "))
                                .with(user(String.valueOf(admin.getId())).roles("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "A production activity with this name already exists for this farm."));

        mockMvc.perform(
                        post("/api/harvest/production-activities")
                                .contextPath(CONTEXT_PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(activityBody(otherFarm.getId(), "Soja"))
                                .with(user(String.valueOf(admin.getId())).roles("ADMIN")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.farmId").value(otherFarm.getId()))
                .andExpect(jsonPath("$.name").value("Soja"));
    }

    @Test
    void shouldSummarizeProductionActivitiesByFarm() throws Exception {
        Farm farm = saveFarm("Farm", FarmStatus.ACTIVE);
        Farm otherFarm = saveFarm("Other Farm", FarmStatus.ACTIVE);
        User producer = saveUser("Producer", "producer@example.com", UserType.USER);
        User unlinked = saveUser("Unlinked", "unlinked@example.com", UserType.USER);
        saveFarmUser(farm, producer, FarmUserRole.PRODUCER);
        ProductionActivity soy = saveActivity(farm, "Soja", ProductionActivityStatus.ACTIVE);
        ProductionActivity corn = saveActivity(farm, "Milho", ProductionActivityStatus.ACTIVE);
        saveActivity(farm, "Café", ProductionActivityStatus.INACTIVE);
        ProductionActivity otherSoy =
                saveActivity(otherFarm, "Soja", ProductionActivityStatus.ACTIVE);
        saveSeason(farm, soy, "Safra Soja 1", HarvestSeasonStatus.IN_PROGRESS);
        saveSeason(farm, soy, "Safra Soja 2", HarvestSeasonStatus.IN_PROGRESS);
        saveSeason(farm, corn, "Safra Milho", HarvestSeasonStatus.PLANNED);
        saveSeason(otherFarm, otherSoy, "Outra Safra", HarvestSeasonStatus.IN_PROGRESS);

        mockMvc.perform(
                        get("/api/harvest/production-activities/summary")
                                .contextPath(CONTEXT_PATH)
                                .param("farmId", String.valueOf(farm.getId()))
                                .with(user(String.valueOf(producer.getId())).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.farmId").value(farm.getId()))
                .andExpect(jsonPath("$.totalCount").value(3))
                .andExpect(jsonPath("$.activeCount").value(2))
                .andExpect(jsonPath("$.inactiveCount").value(1))
                .andExpect(jsonPath("$.inProgressCount").value(1));

        mockMvc.perform(
                        get("/api/harvest/production-activities/summary")
                                .contextPath(CONTEXT_PATH)
                                .param("farmId", String.valueOf(farm.getId()))
                                .with(user(String.valueOf(unlinked.getId())).roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldRejectProductionActivityChangesForInactiveFarm() throws Exception {
        Farm farm = saveFarm("Farm", FarmStatus.INACTIVE);
        User admin = saveUser("Admin", "admin@example.com", UserType.ADMIN);
        ProductionActivity activity = saveActivity(farm, "Soja", ProductionActivityStatus.ACTIVE);

        mockMvc.perform(
                        post("/api/harvest/production-activities")
                                .contextPath(CONTEXT_PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(activityBody(farm.getId(), "Milho"))
                                .with(user(String.valueOf(admin.getId())).roles("ADMIN")))
                .andExpect(status().isForbidden());

        mockMvc.perform(
                        put("/api/harvest/production-activities/{id}", activity.getId())
                                .contextPath(CONTEXT_PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(updateActivityBody("Soja verão"))
                                .with(user(String.valueOf(admin.getId())).roles("ADMIN")))
                .andExpect(status().isForbidden());

        mockMvc.perform(
                        delete("/api/harvest/production-activities/{id}", activity.getId())
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(admin.getId())).roles("ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldCreateAndListHarvestSeasonWithFarmAccessRules() throws Exception {
        Farm farm = saveFarm("Farm", FarmStatus.ACTIVE);
        ProductionActivity activity = saveActivity("Soja", ProductionActivityStatus.ACTIVE);
        User producer = saveUser("Producer", "producer@example.com", UserType.USER);
        User employee = saveUser("Employee", "employee@example.com", UserType.USER);
        User accountant = saveUser("Accountant", "accountant@example.com", UserType.USER);
        saveFarmUser(farm, producer, FarmUserRole.PRODUCER);
        saveFarmUser(farm, employee, FarmUserRole.EMPLOYEE);
        saveFarmUser(farm, accountant, FarmUserRole.ACCOUNTANT);

        mockMvc.perform(
                        post("/api/harvest/seasons")
                                .contextPath(CONTEXT_PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(seasonBody(farm.getId(), activity.getId(), "Safra Soja"))
                                .with(user(String.valueOf(producer.getId())).roles("USER")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Safra Soja"))
                .andExpect(jsonPath("$.status").value("PLANNED"));

        mockMvc.perform(
                        get("/api/harvest/seasons")
                                .contextPath(CONTEXT_PATH)
                                .param("farmId", String.valueOf(farm.getId()))
                                .with(user(String.valueOf(employee.getId())).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Safra Soja"));

        HarvestSeason season = harvestSeasonRepository.findAll().getFirst();

        mockMvc.perform(
                        get("/api/harvest/seasons/{id}", season.getId())
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(accountant.getId())).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productionActivityName").value("Soja"));
    }

    @Test
    void shouldDenyHarvestSeasonManagementForEmployeeAccountantInactiveAndUnlinkedUsers()
            throws Exception {
        Farm farm = saveFarm("Farm", FarmStatus.ACTIVE);
        ProductionActivity activity = saveActivity("Soja", ProductionActivityStatus.ACTIVE);
        User employee = saveUser("Employee", "employee@example.com", UserType.USER);
        User accountant = saveUser("Accountant", "accountant@example.com", UserType.USER);
        User inactive = saveUser("Inactive", "inactive@example.com", UserType.USER);
        User unlinked = saveUser("Unlinked", "unlinked@example.com", UserType.USER);
        saveFarmUser(farm, employee, FarmUserRole.EMPLOYEE);
        saveFarmUser(farm, accountant, FarmUserRole.ACCOUNTANT);
        saveFarmUser(farm, inactive, FarmUserRole.INACTIVE);

        expectCannotCreateSeason(farm, activity, employee);
        expectCannotCreateSeason(farm, activity, accountant);
        expectCannotCreateSeason(farm, activity, inactive);
        expectCannotCreateSeason(farm, activity, unlinked);
    }

    @Test
    void shouldRejectHarvestSeasonForInactiveFarmAndInactiveProductionActivity() throws Exception {
        User admin = saveUser("Admin", "admin@example.com", UserType.ADMIN);
        Farm inactiveFarm = saveFarm("Inactive Farm", FarmStatus.INACTIVE);
        Farm activeFarm = saveFarm("Active Farm", FarmStatus.ACTIVE);
        ProductionActivity inactiveActivity =
                saveActivity(activeFarm, "Café", ProductionActivityStatus.INACTIVE);

        mockMvc.perform(
                        post("/api/harvest/seasons")
                                .contextPath(CONTEXT_PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        seasonBody(
                                                inactiveFarm.getId(),
                                                inactiveActivity.getId(),
                                                "Ciclo"))
                                .with(user(String.valueOf(admin.getId())).roles("ADMIN")))
                .andExpect(status().isForbidden());

        mockMvc.perform(
                        post("/api/harvest/seasons")
                                .contextPath(CONTEXT_PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        seasonBody(
                                                activeFarm.getId(),
                                                inactiveActivity.getId(),
                                                "Ciclo"))
                                .with(user(String.valueOf(admin.getId())).roles("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "Inactive production activity cannot be used in a harvest season."));
    }

    @Test
    void shouldValidateHarvestSeasonDatesAndDuplicateNameByFarm() throws Exception {
        User admin = saveUser("Admin", "admin@example.com", UserType.ADMIN);
        Farm farm = saveFarm("Farm", FarmStatus.ACTIVE);
        Farm otherFarm = saveFarm("Other Farm", FarmStatus.ACTIVE);
        ProductionActivity activity = saveActivity(farm, "Soja", ProductionActivityStatus.ACTIVE);
        ProductionActivity otherActivity =
                saveActivity(otherFarm, "Soja", ProductionActivityStatus.ACTIVE);
        saveSeason(farm, activity, "Safra Soja", HarvestSeasonStatus.PLANNED);
        saveSeason(otherFarm, otherActivity, "Safra Soja", HarvestSeasonStatus.PLANNED);

        mockMvc.perform(
                        post("/api/harvest/seasons")
                                .contextPath(CONTEXT_PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(seasonBody(farm.getId(), activity.getId(), "Safra Soja"))
                                .with(user(String.valueOf(admin.getId())).roles("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "A harvest season with this name already exists for this farm."));

        mockMvc.perform(
                        post("/api/harvest/seasons")
                                .contextPath(CONTEXT_PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "farmId": %s,
                                          "productionActivityId": %s,
                                          "name": "Invalid dates",
                                          "startDate": "2026-02-01",
                                          "endDate": "2026-01-01"
                                        }
                                        """
                                                .formatted(farm.getId(), activity.getId()))
                                .with(user(String.valueOf(admin.getId())).roles("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("End date cannot be before start date."));
    }

    @Test
    void shouldReturnHarvestSeasonSummaryForAuthorizedUser() throws Exception {
        Farm farm = saveFarm("Farm", FarmStatus.ACTIVE);
        ProductionActivity activity = saveActivity("Soja", ProductionActivityStatus.ACTIVE);
        User accountant = saveUser("Accountant", "accountant@example.com", UserType.USER);
        saveFarmUser(farm, accountant, FarmUserRole.ACCOUNTANT);
        HarvestSeason season =
                saveSeason(farm, activity, "Safra Soja", HarvestSeasonStatus.PLANNED);
        season.setExpectedRevenue(new BigDecimal("210000.00"));
        season.setExpectedCost(new BigDecimal("96500.00"));
        season.setAreaHectares(new BigDecimal("120.00"));
        harvestSeasonRepository.save(season);
        saveTransaction(
                farm, accountant, season, TransactionType.INCOME, PaymentStatus.PAID, "150000.00");
        saveTransaction(
                farm, accountant, season, TransactionType.EXPENSE, PaymentStatus.PAID, "72500.00");
        saveTransaction(
                farm,
                accountant,
                season,
                TransactionType.EXPENSE,
                PaymentStatus.PENDING,
                "18000.00");

        mockMvc.perform(
                        get("/api/harvest/seasons/{id}/summary", season.getId())
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(accountant.getId())).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.harvestSeasonId").value(season.getId()))
                .andExpect(jsonPath("$.harvestSeasonName").value("Safra Soja"))
                .andExpect(jsonPath("$.productionActivityId").value(activity.getId()))
                .andExpect(jsonPath("$.productionActivityName").value("Soja"))
                .andExpect(jsonPath("$.farmId").value(farm.getId()))
                .andExpect(jsonPath("$.farmName").value("Farm"))
                .andExpect(jsonPath("$.planning.plannedCost").value(96500.00))
                .andExpect(jsonPath("$.planning.plannedRevenue").value(210000.00))
                .andExpect(jsonPath("$.planning.plannedProfit").value(113500.00))
                .andExpect(jsonPath("$.realized.realizedCost").value(72500.00))
                .andExpect(jsonPath("$.realized.realizedRevenue").value(150000.00))
                .andExpect(jsonPath("$.projection.projectedProfit").value(59500.00))
                .andExpect(jsonPath("$.openAmounts.pending.payableAmount").value(18000.00))
                .andExpect(jsonPath("$.comparison.profitPerformanceAmount").value(-54000.00))
                .andExpect(jsonPath("$.transactionCount").value(3))
                .andExpect(jsonPath("$.expectedCost").doesNotExist());
    }

    @Test
    void shouldReturnHarvestSeasonSummaryListForAuthorizedUser() throws Exception {
        Farm farm = saveFarm("Farm", FarmStatus.ACTIVE);
        ProductionActivity activity = saveActivity("Soja", ProductionActivityStatus.ACTIVE);
        User accountant = saveUser("Accountant", "accountant@example.com", UserType.USER);
        saveFarmUser(farm, accountant, FarmUserRole.ACCOUNTANT);
        HarvestSeason season =
                saveSeason(farm, activity, "Safra Soja", HarvestSeasonStatus.PLANNED);
        season.setExpectedRevenue(new BigDecimal("210000.00"));
        season.setExpectedCost(new BigDecimal("96500.00"));
        season.setAreaHectares(new BigDecimal("120.00"));
        harvestSeasonRepository.save(season);
        saveTransaction(
                farm, accountant, season, TransactionType.INCOME, PaymentStatus.PAID, "150000.00");
        saveTransaction(
                farm, accountant, season, TransactionType.EXPENSE, PaymentStatus.PAID, "72500.00");
        saveTransaction(
                farm,
                accountant,
                season,
                TransactionType.EXPENSE,
                PaymentStatus.PENDING,
                "18000.00");

        mockMvc.perform(
                        get("/api/harvest/seasons/summary-list")
                                .contextPath(CONTEXT_PATH)
                                .param("farmId", String.valueOf(farm.getId()))
                                .with(user(String.valueOf(accountant.getId())).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(season.getId()))
                .andExpect(jsonPath("$.content[0].name").value("Safra Soja"))
                .andExpect(jsonPath("$.content[0].farmId").value(farm.getId()))
                .andExpect(jsonPath("$.content[0].productionActivityName").value("Soja"))
                .andExpect(jsonPath("$.content[0].expectedCost").value(96500.00))
                .andExpect(jsonPath("$.content[0].expectedRevenue").value(210000.00))
                .andExpect(jsonPath("$.content[0].expectedProfit").value(113500.00))
                .andExpect(jsonPath("$.content[0].realizedCost").value(72500.00))
                .andExpect(jsonPath("$.content[0].realizedRevenue").value(150000.00))
                .andExpect(jsonPath("$.content[0].realizedProfit").value(77500.00))
                .andExpect(jsonPath("$.content[0].pendingExpenses").value(18000.00))
                .andExpect(jsonPath("$.content[0].transactionCount").value(3))
                .andExpect(jsonPath("$.content[0].incomeCount").value(1))
                .andExpect(jsonPath("$.content[0].expenseCount").value(2))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void shouldFilterHarvestSeasonListByStatuses() throws Exception {
        Farm farm = saveFarm("Farm", FarmStatus.ACTIVE);
        ProductionActivity activity = saveActivity("Soja", ProductionActivityStatus.ACTIVE);
        User admin = saveUser("Admin", "admin@example.com", UserType.ADMIN);
        saveSeason(farm, activity, "Planejada", HarvestSeasonStatus.PLANNED);
        saveSeason(farm, activity, "Andamento", HarvestSeasonStatus.IN_PROGRESS);
        saveSeason(farm, activity, "Finalizada", HarvestSeasonStatus.FINISHED);
        saveSeason(farm, activity, "Inativa", HarvestSeasonStatus.INACTIVE);

        mockMvc.perform(
                        get("/api/harvest/seasons")
                                .contextPath(CONTEXT_PATH)
                                .param("farmId", String.valueOf(farm.getId()))
                                .with(user(String.valueOf(admin.getId())).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.content[*].name")
                                .value(containsInAnyOrder("Planejada", "Andamento", "Finalizada")))
                .andExpect(jsonPath("$.totalElements").value(3));

        mockMvc.perform(
                        get("/api/harvest/seasons")
                                .contextPath(CONTEXT_PATH)
                                .param("farmId", String.valueOf(farm.getId()))
                                .param("statuses", "PLANNED")
                                .with(user(String.valueOf(admin.getId())).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].name").value(containsInAnyOrder("Planejada")))
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(
                        get("/api/harvest/seasons")
                                .contextPath(CONTEXT_PATH)
                                .param("farmId", String.valueOf(farm.getId()))
                                .param("statuses", "PLANNED,IN_PROGRESS")
                                .with(user(String.valueOf(admin.getId())).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.content[*].name")
                                .value(containsInAnyOrder("Planejada", "Andamento")))
                .andExpect(jsonPath("$.totalElements").value(2));

        mockMvc.perform(
                        get("/api/harvest/seasons")
                                .contextPath(CONTEXT_PATH)
                                .param("farmId", String.valueOf(farm.getId()))
                                .param("statuses", "FINISHED", "INACTIVE")
                                .with(user(String.valueOf(admin.getId())).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.content[*].name")
                                .value(containsInAnyOrder("Finalizada", "Inativa")))
                .andExpect(jsonPath("$.totalElements").value(2));

        mockMvc.perform(
                        get("/api/harvest/seasons")
                                .contextPath(CONTEXT_PATH)
                                .param("farmId", String.valueOf(farm.getId()))
                                .param("statuses", "PLANNED,IN_PROGRESS")
                                .with(user(String.valueOf(admin.getId())).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.content[*].name")
                                .value(containsInAnyOrder("Planejada", "Andamento")))
                .andExpect(jsonPath("$.totalElements").value(2));

        mockMvc.perform(
                        get("/api/harvest/seasons")
                                .contextPath(CONTEXT_PATH)
                                .param("farmId", String.valueOf(farm.getId()))
                                .param("statuses", "")
                                .with(user(String.valueOf(admin.getId())).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3));

        mockMvc.perform(
                        get("/api/harvest/seasons")
                                .contextPath(CONTEXT_PATH)
                                .param("farmId", String.valueOf(farm.getId()))
                                .param("statuses", "INVALID")
                                .with(user(String.valueOf(admin.getId())).roles("ADMIN")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldFilterHarvestSeasonSummaryListByStatuses() throws Exception {
        Farm farm = saveFarm("Farm", FarmStatus.ACTIVE);
        ProductionActivity activity = saveActivity("Soja", ProductionActivityStatus.ACTIVE);
        User accountant = saveUser("Accountant", "accountant@example.com", UserType.USER);
        saveFarmUser(farm, accountant, FarmUserRole.ACCOUNTANT);
        HarvestSeason planned =
                saveSeason(farm, activity, "Planejada", HarvestSeasonStatus.PLANNED);
        HarvestSeason inProgress =
                saveSeason(farm, activity, "Andamento", HarvestSeasonStatus.IN_PROGRESS);
        saveSeason(farm, activity, "Finalizada", HarvestSeasonStatus.FINISHED);
        saveSeason(farm, activity, "Inativa", HarvestSeasonStatus.INACTIVE);
        saveTransaction(
                farm, accountant, planned, TransactionType.INCOME, PaymentStatus.PAID, "100.00");
        saveTransaction(
                farm, accountant, planned, TransactionType.EXPENSE, PaymentStatus.PAID, "40.00");
        saveTransaction(
                farm, accountant, planned, TransactionType.INCOME, PaymentStatus.PENDING, "10.00");
        saveTransaction(
                farm,
                accountant,
                planned,
                TransactionType.INCOME,
                PaymentStatus.CANCELED,
                "999.00");
        FinancialTransaction deleted =
                saveTransaction(
                        farm,
                        accountant,
                        planned,
                        TransactionType.EXPENSE,
                        PaymentStatus.PAID,
                        "999.00");
        deleted.setRecordStatus(FinancialRecordStatus.DELETED);
        financialTransactionRepository.save(deleted);

        mockMvc.perform(
                        get("/api/harvest/seasons/summary-list")
                                .contextPath(CONTEXT_PATH)
                                .param("farmId", String.valueOf(farm.getId()))
                                .with(user(String.valueOf(accountant.getId())).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.content[*].name")
                                .value(containsInAnyOrder("Planejada", "Andamento", "Finalizada")))
                .andExpect(jsonPath("$.totalElements").value(3));

        mockMvc.perform(
                        get("/api/harvest/seasons/summary-list")
                                .contextPath(CONTEXT_PATH)
                                .param("farmId", String.valueOf(farm.getId()))
                                .param("statuses", "IN_PROGRESS")
                                .with(user(String.valueOf(accountant.getId())).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].name").value(containsInAnyOrder("Andamento")))
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(
                        get("/api/harvest/seasons/summary-list")
                                .contextPath(CONTEXT_PATH)
                                .param("farmId", String.valueOf(farm.getId()))
                                .param("statuses", "PLANNED,IN_PROGRESS")
                                .with(user(String.valueOf(accountant.getId())).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.content[*].name")
                                .value(containsInAnyOrder("Planejada", "Andamento")))
                .andExpect(jsonPath("$.content[0].id").value(planned.getId()))
                .andExpect(jsonPath("$.content[0].realizedRevenue").value(100.00))
                .andExpect(jsonPath("$.content[0].realizedCost").value(40.00))
                .andExpect(jsonPath("$.content[0].pendingRevenue").value(10.00))
                .andExpect(jsonPath("$.content[0].transactionCount").value(3))
                .andExpect(jsonPath("$.content[0].incomeCount").value(2))
                .andExpect(jsonPath("$.content[0].expenseCount").value(1))
                .andExpect(jsonPath("$.content[1].id").value(inProgress.getId()))
                .andExpect(jsonPath("$.content[1].transactionCount").value(0))
                .andExpect(jsonPath("$.totalElements").value(2));

        mockMvc.perform(
                        get("/api/harvest/seasons/summary-list")
                                .contextPath(CONTEXT_PATH)
                                .param("farmId", String.valueOf(farm.getId()))
                                .param("statuses", "")
                                .with(user(String.valueOf(accountant.getId())).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void shouldFilterHarvestSeasonListByProductionActivityAndPeriod() throws Exception {
        Farm farm = saveFarm("Farm", FarmStatus.ACTIVE);
        ProductionActivity soy = saveActivity("Soja", ProductionActivityStatus.ACTIVE);
        ProductionActivity corn = saveActivity("Milho", ProductionActivityStatus.ACTIVE);
        ProductionActivity coffee = saveActivity("Cafe", ProductionActivityStatus.ACTIVE);
        User admin = saveUser("Admin", "admin@example.com", UserType.ADMIN);
        HarvestSeason soySeason = saveSeason(farm, soy, "Safra Soja", HarvestSeasonStatus.PLANNED);
        HarvestSeason cornSeason =
                saveSeason(farm, corn, "Safra Milho", HarvestSeasonStatus.PLANNED);
        HarvestSeason coffeeSeason =
                saveSeason(farm, coffee, "Safra Cafe", HarvestSeasonStatus.PLANNED);
        HarvestSeason openEndedSeason =
                saveSeason(farm, soy, "Safra Permanente", HarvestSeasonStatus.PLANNED);
        soySeason.setStartDate(LocalDate.of(2026, 1, 1));
        soySeason.setEndDate(LocalDate.of(2026, 3, 31));
        cornSeason.setStartDate(LocalDate.of(2026, 4, 1));
        cornSeason.setEndDate(LocalDate.of(2026, 6, 30));
        coffeeSeason.setStartDate(LocalDate.of(2026, 8, 1));
        coffeeSeason.setEndDate(LocalDate.of(2026, 9, 30));
        openEndedSeason.setStartDate(LocalDate.of(2025, 11, 1));
        openEndedSeason.setEndDate(null);
        harvestSeasonRepository.saveAll(
                java.util.List.of(soySeason, cornSeason, coffeeSeason, openEndedSeason));

        mockMvc.perform(
                        get("/api/harvest/seasons")
                                .contextPath(CONTEXT_PATH)
                                .param("farmId", String.valueOf(farm.getId()))
                                .param("productionActivityId", String.valueOf(corn.getId()))
                                .with(user(String.valueOf(admin.getId())).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].name").value(containsInAnyOrder("Safra Milho")))
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(
                        get("/api/harvest/seasons")
                                .contextPath(CONTEXT_PATH)
                                .param("farmId", String.valueOf(farm.getId()))
                                .param("productionActivityId", String.valueOf(coffee.getId()))
                                .param(
                                        "productionActivityIds",
                                        String.valueOf(soy.getId()),
                                        String.valueOf(corn.getId()))
                                .with(user(String.valueOf(admin.getId())).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.content[*].name")
                                .value(
                                        containsInAnyOrder(
                                                "Safra Soja", "Safra Milho", "Safra Permanente")))
                .andExpect(jsonPath("$.totalElements").value(3));

        mockMvc.perform(
                        get("/api/harvest/seasons")
                                .contextPath(CONTEXT_PATH)
                                .param("farmId", String.valueOf(farm.getId()))
                                .param("periodStart", "2026-02-01")
                                .param("periodEnd", "2026-04-30")
                                .with(user(String.valueOf(admin.getId())).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.content[*].name")
                                .value(
                                        containsInAnyOrder(
                                                "Safra Soja", "Safra Milho", "Safra Permanente")))
                .andExpect(jsonPath("$.totalElements").value(3));

        mockMvc.perform(
                        get("/api/harvest/seasons")
                                .contextPath(CONTEXT_PATH)
                                .param("farmId", String.valueOf(farm.getId()))
                                .param("periodStart", "2026-07-01")
                                .with(user(String.valueOf(admin.getId())).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.content[*].name")
                                .value(containsInAnyOrder("Safra Cafe", "Safra Permanente")))
                .andExpect(jsonPath("$.totalElements").value(2));

        mockMvc.perform(
                        get("/api/harvest/seasons")
                                .contextPath(CONTEXT_PATH)
                                .param("farmId", String.valueOf(farm.getId()))
                                .param("periodEnd", "2026-02-15")
                                .with(user(String.valueOf(admin.getId())).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.content[*].name")
                                .value(containsInAnyOrder("Safra Soja", "Safra Permanente")))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void shouldRejectInvalidHarvestSeasonListPeriod() throws Exception {
        Farm farm = saveFarm("Farm", FarmStatus.ACTIVE);
        User admin = saveUser("Admin", "admin@example.com", UserType.ADMIN);

        mockMvc.perform(
                        get("/api/harvest/seasons")
                                .contextPath(CONTEXT_PATH)
                                .param("farmId", String.valueOf(farm.getId()))
                                .param("periodStart", "2026-02-01")
                                .param("periodEnd", "2026-01-31")
                                .with(user(String.valueOf(admin.getId())).roles("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "A data inicial do período não pode ser posterior à data final."));
    }

    @Test
    void shouldFilterHarvestSeasonSummaryListByCombinedFilters() throws Exception {
        Farm farm = saveFarm("Farm", FarmStatus.ACTIVE);
        ProductionActivity soy = saveActivity("Soja", ProductionActivityStatus.ACTIVE);
        ProductionActivity corn = saveActivity("Milho", ProductionActivityStatus.ACTIVE);
        User accountant = saveUser("Accountant", "accountant@example.com", UserType.USER);
        saveFarmUser(farm, accountant, FarmUserRole.ACCOUNTANT);
        HarvestSeason plannedSoy = saveSeason(farm, soy, "Safra Soja", HarvestSeasonStatus.PLANNED);
        HarvestSeason progressSoy =
                saveSeason(farm, soy, "Segunda Soja", HarvestSeasonStatus.IN_PROGRESS);
        HarvestSeason plannedCorn =
                saveSeason(farm, corn, "Safra Milho", HarvestSeasonStatus.PLANNED);
        plannedSoy.setDescription("Ciclo especial");
        plannedSoy.setStartDate(LocalDate.of(2026, 1, 1));
        plannedSoy.setEndDate(LocalDate.of(2026, 3, 31));
        progressSoy.setStartDate(LocalDate.of(2026, 2, 1));
        progressSoy.setEndDate(LocalDate.of(2026, 5, 31));
        plannedCorn.setStartDate(LocalDate.of(2026, 2, 1));
        plannedCorn.setEndDate(LocalDate.of(2026, 5, 31));
        harvestSeasonRepository.saveAll(java.util.List.of(plannedSoy, progressSoy, plannedCorn));

        mockMvc.perform(
                        get("/api/harvest/seasons/summary-list")
                                .contextPath(CONTEXT_PATH)
                                .param("farmId", String.valueOf(farm.getId()))
                                .param("search", "ciclo")
                                .param("statuses", "PLANNED,IN_PROGRESS")
                                .param("productionActivityIds", String.valueOf(soy.getId()))
                                .param("periodStart", "2026-02-15")
                                .param("periodEnd", "2026-04-15")
                                .with(user(String.valueOf(accountant.getId())).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].name").value(containsInAnyOrder("Safra Soja")))
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(
                        get("/api/harvest/seasons/summary-list")
                                .contextPath(CONTEXT_PATH)
                                .param("farmId", String.valueOf(farm.getId()))
                                .param("periodStart", "2026-06-01")
                                .param("periodEnd", "2026-05-31")
                                .with(user(String.valueOf(accountant.getId())).roles("USER")))
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "A data inicial do período não pode ser posterior à data final."));
    }

    @Test
    void shouldProtectHarvestSeasonSummaryListEndpoint() throws Exception {
        Farm farm = saveFarm("Farm", FarmStatus.ACTIVE);
        ProductionActivity activity = saveActivity("Soja", ProductionActivityStatus.ACTIVE);
        User admin = saveUser("Admin", "admin@example.com", UserType.ADMIN);
        User unlinked = saveUser("Unlinked", "unlinked@example.com", UserType.USER);
        User inactive = saveUser("Inactive", "inactive@example.com", UserType.USER);
        saveFarmUser(farm, inactive, FarmUserRole.INACTIVE);
        saveSeason(farm, activity, "Safra Soja", HarvestSeasonStatus.PLANNED);

        mockMvc.perform(
                        get("/api/harvest/seasons/summary-list")
                                .contextPath(CONTEXT_PATH)
                                .param("farmId", String.valueOf(farm.getId())))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(
                        get("/api/harvest/seasons/summary-list")
                                .contextPath(CONTEXT_PATH)
                                .param("farmId", String.valueOf(farm.getId()))
                                .with(user(String.valueOf(unlinked.getId())).roles("USER")))
                .andExpect(status().isForbidden());

        mockMvc.perform(
                        get("/api/harvest/seasons/summary-list")
                                .contextPath(CONTEXT_PATH)
                                .param("farmId", String.valueOf(farm.getId()))
                                .with(user(String.valueOf(inactive.getId())).roles("USER")))
                .andExpect(status().isForbidden());

        mockMvc.perform(
                        get("/api/harvest/seasons/summary-list")
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(admin.getId())).roles("ADMIN")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(
                        get("/api/harvest/seasons/summary-list")
                                .contextPath(CONTEXT_PATH)
                                .param("farmId", String.valueOf(farm.getId()))
                                .with(user(String.valueOf(admin.getId())).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Safra Soja"));
    }

    @Test
    void shouldProtectHarvestSeasonSummaryEndpoint() throws Exception {
        Farm farm = saveFarm("Farm", FarmStatus.ACTIVE);
        ProductionActivity activity = saveActivity("Soja", ProductionActivityStatus.ACTIVE);
        User producer = saveUser("Producer", "producer@example.com", UserType.USER);
        User unlinked = saveUser("Unlinked", "unlinked@example.com", UserType.USER);
        User inactive = saveUser("Inactive", "inactive@example.com", UserType.USER);
        saveFarmUser(farm, producer, FarmUserRole.PRODUCER);
        saveFarmUser(farm, inactive, FarmUserRole.INACTIVE);
        HarvestSeason season =
                saveSeason(farm, activity, "Safra Soja", HarvestSeasonStatus.PLANNED);

        mockMvc.perform(
                        get("/api/harvest/seasons/{id}/summary", season.getId())
                                .contextPath(CONTEXT_PATH))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(
                        get("/api/harvest/seasons/{id}/summary", season.getId())
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(unlinked.getId())).roles("USER")))
                .andExpect(status().isForbidden());

        mockMvc.perform(
                        get("/api/harvest/seasons/{id}/summary", season.getId())
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(inactive.getId())).roles("USER")))
                .andExpect(status().isForbidden());

        mockMvc.perform(
                        get("/api/harvest/seasons/{id}/summary", 999L)
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(producer.getId())).roles("USER")))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldInactivateHarvestSeasonAndDenyCommonUserUpdate() throws Exception {
        Farm farm = saveFarm("Farm", FarmStatus.ACTIVE);
        ProductionActivity activity = saveActivity("Soja", ProductionActivityStatus.ACTIVE);
        User producer = saveUser("Producer", "producer@example.com", UserType.USER);
        saveFarmUser(farm, producer, FarmUserRole.PRODUCER);
        HarvestSeason season =
                saveSeason(farm, activity, "Safra Soja", HarvestSeasonStatus.PLANNED);

        mockMvc.perform(
                        delete("/api/harvest/seasons/{id}", season.getId())
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(producer.getId())).roles("USER")))
                .andExpect(status().isNoContent());

        assertThat(harvestSeasonRepository.findById(season.getId()))
                .get()
                .extracting(HarvestSeason::getStatus)
                .isEqualTo(HarvestSeasonStatus.INACTIVE);

        mockMvc.perform(
                        put("/api/harvest/seasons/{id}", season.getId())
                                .contextPath(CONTEXT_PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(updateSeasonBody(activity.getId(), "Updated"))
                                .with(user(String.valueOf(producer.getId())).roles("USER")))
                .andExpect(status().isForbidden());
    }

    private void expectCannotCreateSeason(
            Farm farm, ProductionActivity activity, User authenticatedUser) throws Exception {
        mockMvc.perform(
                        post("/api/harvest/seasons")
                                .contextPath(CONTEXT_PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(seasonBody(farm.getId(), activity.getId(), "Denied"))
                                .with(
                                        user(String.valueOf(authenticatedUser.getId()))
                                                .roles("USER")))
                .andExpect(status().isForbidden());
    }

    private void expectCannotCreateActivity(Farm farm, User authenticatedUser, String name)
            throws Exception {
        mockMvc.perform(
                        post("/api/harvest/production-activities")
                                .contextPath(CONTEXT_PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(activityBody(farm.getId(), name))
                                .with(
                                        user(String.valueOf(authenticatedUser.getId()))
                                                .roles("USER")))
                .andExpect(status().isForbidden());
    }

    private String activityBody(Long farmId, String name) {
        return """
                {
                  "farmId": %s,
                  "name": "%s",
                  "description": "Activity description"
                }
                """
                .formatted(farmId, name);
    }

    private String updateActivityBody(String name) {
        return """
                {
                  "name": "%s",
                  "description": "Activity description"
                }
                """
                .formatted(name);
    }

    private String seasonBody(Long farmId, Long activityId, String name) {
        return """
                {
                  "farmId": %s,
                  "productionActivityId": %s,
                  "name": "%s",
                  "description": "Season description",
                  "startDate": "2026-01-01",
                  "expectedRevenue": 1000.00,
                  "expectedCost": 500.00,
                  "areaHectares": 0.00
                }
                """
                .formatted(farmId, activityId, name);
    }

    private String updateSeasonBody(Long activityId, String name) {
        return """
                {
                  "productionActivityId": %s,
                  "name": "%s",
                  "description": "Updated description",
                  "startDate": "2026-01-01",
                  "expectedRevenue": 1000.00,
                  "expectedCost": 500.00,
                  "areaHectares": 0.00
                }
                """
                .formatted(activityId, name);
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

    @Test
    void shouldProtectAndReturnDashboardHarvestSeasons() throws Exception {
        Farm farm = saveFarm("Farm", FarmStatus.ACTIVE);
        ProductionActivity activity = saveActivity(farm, "Coffee", ProductionActivityStatus.ACTIVE);
        User producer = saveUser("Producer", "producer-dashboard@example.com", UserType.USER);
        User employee = saveUser("Employee", "employee-dashboard@example.com", UserType.USER);
        User accountant = saveUser("Accountant", "accountant-dashboard@example.com", UserType.USER);
        User unlinked = saveUser("Unlinked", "unlinked-dashboard@example.com", UserType.USER);
        User admin = saveUser("Admin", "admin-dashboard@example.com", UserType.ADMIN);
        saveFarmUser(farm, producer, FarmUserRole.PRODUCER);
        saveFarmUser(farm, employee, FarmUserRole.EMPLOYEE);
        saveFarmUser(farm, accountant, FarmUserRole.ACCOUNTANT);
        saveSeason(farm, activity, "In progress", HarvestSeasonStatus.IN_PROGRESS);

        mockMvc.perform(
                        get("/api/harvest/seasons/dashboard")
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(admin.getId())).roles("ADMIN")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(
                        get("/api/harvest/seasons/dashboard")
                                .contextPath(CONTEXT_PATH)
                                .param("farmId", "999999")
                                .with(user(String.valueOf(admin.getId())).roles("ADMIN")))
                .andExpect(status().isNotFound());
        mockMvc.perform(
                        get("/api/harvest/seasons/dashboard")
                                .contextPath(CONTEXT_PATH)
                                .param("farmId", String.valueOf(farm.getId()))
                                .with(user(String.valueOf(unlinked.getId())).roles("USER")))
                .andExpect(status().isForbidden());
        for (User dashboardUser : List.of(producer, employee, accountant)) {
            mockMvc.perform(
                            get("/api/harvest/seasons/dashboard")
                                    .contextPath(CONTEXT_PATH)
                                    .param("farmId", String.valueOf(farm.getId()))
                                    .with(
                                            user(String.valueOf(dashboardUser.getId()))
                                                    .roles("USER")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("00].status").value("IN_PROGRESS"));
        }
        mockMvc.perform(
                        get("/api/harvest/seasons/dashboard")
                                .contextPath(CONTEXT_PATH)
                                .param("farmId", String.valueOf(farm.getId()))
                                .with(user(String.valueOf(admin.getId())).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("00].productionActivityName").value("Coffee"));
    }

    private Farm saveFarm(String name, FarmStatus status) {
        Farm farm = new Farm();
        farm.setName(name);
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

    private ProductionActivity saveActivity(String name, ProductionActivityStatus status) {
        Farm farm = farmRepository.findAll(Sort.by("id")).getFirst();

        return saveActivity(farm, name, status);
    }

    private ProductionActivity saveActivity(
            Farm farm, String name, ProductionActivityStatus status) {
        ProductionActivity activity = new ProductionActivity();
        activity.setFarm(farm);
        activity.setName(name);
        activity.setStatus(status);

        return productionActivityRepository.save(activity);
    }

    private HarvestSeason saveSeason(
            Farm farm, ProductionActivity activity, String name, HarvestSeasonStatus status) {
        HarvestSeason season = new HarvestSeason();
        season.setFarm(farm);
        season.setProductionActivity(activity);
        season.setName(name);
        season.setStartDate(LocalDate.of(2026, 1, 1));
        season.setExpectedRevenue(BigDecimal.ZERO);
        season.setExpectedCost(BigDecimal.ZERO);
        season.setAreaHectares(BigDecimal.ZERO);
        season.setStatus(status);

        return harvestSeasonRepository.save(season);
    }

    private FinancialTransaction saveTransaction(
            Farm farm,
            User user,
            HarvestSeason harvestSeason,
            TransactionType type,
            PaymentStatus status,
            String amount) {
        FinancialTransaction transaction = new FinancialTransaction();
        transaction.setDescription("Transaction");
        transaction.setAmount(new BigDecimal(amount));
        transaction.setType(type);
        transaction.setStatus(status);
        transaction.setTransactionDate(LocalDate.now());
        transaction.setDueDate(LocalDate.now().plusDays(5));
        transaction.setFarm(farm);
        transaction.setHarvestSeason(harvestSeason);
        transaction.setCreatedByUser(user);
        transaction.setRecordStatus(FinancialRecordStatus.ACTIVE);

        return financialTransactionRepository.save(transaction);
    }
}
