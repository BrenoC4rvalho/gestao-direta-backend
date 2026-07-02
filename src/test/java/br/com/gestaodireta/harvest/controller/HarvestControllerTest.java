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
class HarvestControllerTest extends PostgresIntegrationTest {

    private static final String CONTEXT_PATH = "/api";

    @Autowired private MockMvc mockMvc;

    @Autowired private HarvestSeasonRepository harvestSeasonRepository;

    @Autowired private ProductionActivityRepository productionActivityRepository;

    @Autowired private FarmUserRepository farmUserRepository;

    @Autowired private FarmRepository farmRepository;

    @Autowired private UserRepository userRepository;

    @Autowired private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        harvestSeasonRepository.deleteAll();
        productionActivityRepository.deleteAll();
        farmUserRepository.deleteAll();
        farmRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void shouldManageProductionActivitiesAsAdmin() throws Exception {
        User admin = saveUser("Admin", "admin@example.com", UserType.ADMIN);

        mockMvc.perform(
                        post("/api/harvest/production-activities")
                                .contextPath(CONTEXT_PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(activityBody("Soja"))
                                .with(user(String.valueOf(admin.getId())).roles("ADMIN")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Soja"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        ProductionActivity activity = productionActivityRepository.findAll().getFirst();

        mockMvc.perform(
                        put("/api/harvest/production-activities/{id}", activity.getId())
                                .contextPath(CONTEXT_PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(activityBody("Soja verão"))
                                .with(user(String.valueOf(admin.getId())).roles("ADMIN")))
                .andExpect(status().isOk())
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
    void shouldDenyProductionActivityManagementForUser() throws Exception {
        Farm farm = saveFarm("Farm", FarmStatus.ACTIVE);
        User producer = saveUser("Producer", "producer@example.com", UserType.USER);
        saveFarmUser(farm, producer, FarmUserRole.PRODUCER);

        mockMvc.perform(
                        post("/api/harvest/production-activities")
                                .contextPath(CONTEXT_PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(activityBody("Milho"))
                                .with(user(String.valueOf(producer.getId())).roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldListOnlyActiveProductionActivitiesForLinkedUsers() throws Exception {
        Farm farm = saveFarm("Farm", FarmStatus.ACTIVE);
        User producer = saveUser("Producer", "producer@example.com", UserType.USER);
        User unlinked = saveUser("Unlinked", "unlinked@example.com", UserType.USER);
        saveFarmUser(farm, producer, FarmUserRole.PRODUCER);
        saveActivity("Soja", ProductionActivityStatus.ACTIVE);
        saveActivity("Milho", ProductionActivityStatus.ACTIVE);
        saveActivity("Café", ProductionActivityStatus.INACTIVE);

        mockMvc.perform(
                        get("/api/harvest/production-activities/active")
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(producer.getId())).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name").value(containsInAnyOrder("Soja", "Milho")));

        mockMvc.perform(
                        get("/api/harvest/production-activities/active")
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(unlinked.getId())).roles("USER")))
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
                saveActivity("Café", ProductionActivityStatus.INACTIVE);

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
        ProductionActivity activity = saveActivity("Soja", ProductionActivityStatus.ACTIVE);
        saveSeason(farm, activity, "Safra Soja", HarvestSeasonStatus.PLANNED);
        saveSeason(otherFarm, activity, "Safra Soja", HarvestSeasonStatus.PLANNED);

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

    private String activityBody(String name) {
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
        ProductionActivity activity = new ProductionActivity();
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
}
