package br.com.gestaodireta.farm.controller;

import static org.hamcrest.Matchers.hasSize;
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
class FarmControllerTest extends PostgresIntegrationTest {

    private static final String CONTEXT_PATH = "/api";

    @Autowired private MockMvc mockMvc;

    @Autowired private FarmRepository farmRepository;

    @Autowired private FarmUserRepository farmUserRepository;

    @Autowired private UserRepository userRepository;

    @Autowired private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        farmUserRepository.deleteAll();
        farmRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void shouldCreateFarmAsAdminWithActiveStatus() throws Exception {
        String body = farmBody("Fazenda Boa Esperanca");

        mockMvc.perform(
                        post("/api/farms")
                                .contextPath(CONTEXT_PATH)
                                .with(user("1").roles("ADMIN"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Fazenda Boa Esperanca"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void shouldRejectFarmCreationForUserOrAnonymousOrInvalidName() throws Exception {
        mockMvc.perform(
                        post("/api/farms")
                                .contextPath(CONTEXT_PATH)
                                .with(user("1").roles("USER"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(farmBody("Fazenda")))
                .andExpect(status().isForbidden());

        mockMvc.perform(
                        post("/api/farms")
                                .contextPath(CONTEXT_PATH)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(farmBody("Fazenda")))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(
                        post("/api/farms")
                                .contextPath(CONTEXT_PATH)
                                .with(user("1").roles("ADMIN"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(farmBody("")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldListAllFarmsAsAdmin() throws Exception {
        saveFarm("Fazenda A", FarmStatus.ACTIVE);
        saveFarm("Fazenda B", FarmStatus.INACTIVE);

        mockMvc.perform(get("/api/farms").contextPath(CONTEXT_PATH).with(user("1").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)));
    }

    @Test
    void shouldListOnlyActiveLinkedFarmsAsUser() throws Exception {
        User user = saveUser("User", "user@example.com", UserType.USER, UserStatus.ACTIVE);
        Farm producerFarm = saveFarm("Producer Farm", FarmStatus.ACTIVE);
        Farm employeeFarm = saveFarm("Employee Farm", FarmStatus.ACTIVE);
        Farm accountantFarm = saveFarm("Accountant Farm", FarmStatus.ACTIVE);
        Farm inactiveRoleFarm = saveFarm("Inactive Role Farm", FarmStatus.ACTIVE);
        Farm inactiveFarm = saveFarm("Inactive Farm", FarmStatus.INACTIVE);
        saveFarm("Unlinked Farm", FarmStatus.ACTIVE);
        saveFarmUser(producerFarm, user, FarmUserRole.PRODUCER);
        saveFarmUser(employeeFarm, user, FarmUserRole.EMPLOYEE);
        saveFarmUser(accountantFarm, user, FarmUserRole.ACCOUNTANT);
        saveFarmUser(inactiveRoleFarm, user, FarmUserRole.INACTIVE);
        saveFarmUser(inactiveFarm, user, FarmUserRole.PRODUCER);

        mockMvc.perform(
                        get("/api/farms")
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(user.getId())).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(3)))
                .andExpect(jsonPath("$.content[0].name").value("Producer Farm"))
                .andExpect(jsonPath("$.content[1].name").value("Employee Farm"))
                .andExpect(jsonPath("$.content[2].name").value("Accountant Farm"));
    }

    @Test
    void shouldFindFarmByIdForAdminAndActiveLinkedRoles() throws Exception {
        Farm farm = saveFarm("Farm", FarmStatus.ACTIVE);
        User producer =
                saveUser("Producer", "producer@example.com", UserType.USER, UserStatus.ACTIVE);
        User employee =
                saveUser("Employee", "employee@example.com", UserType.USER, UserStatus.ACTIVE);
        User accountant =
                saveUser("Accountant", "accountant@example.com", UserType.USER, UserStatus.ACTIVE);
        saveFarmUser(farm, producer, FarmUserRole.PRODUCER);
        saveFarmUser(farm, employee, FarmUserRole.EMPLOYEE);
        saveFarmUser(farm, accountant, FarmUserRole.ACCOUNTANT);

        mockMvc.perform(
                        get("/api/farms/{id}", farm.getId())
                                .contextPath(CONTEXT_PATH)
                                .with(user("1").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(farm.getId()));

        expectCanFindFarm(farm, producer);
        expectCanFindFarm(farm, employee);
        expectCanFindFarm(farm, accountant);
    }

    @Test
    void shouldRejectFindFarmForInactiveRoleOrUnlinkedUser() throws Exception {
        Farm farm = saveFarm("Farm", FarmStatus.ACTIVE);
        User inactive =
                saveUser("Inactive", "inactive@example.com", UserType.USER, UserStatus.ACTIVE);
        User unlinked =
                saveUser("Unlinked", "unlinked@example.com", UserType.USER, UserStatus.ACTIVE);
        User blocked =
                saveUser("Blocked", "blocked@example.com", UserType.USER, UserStatus.BLOCKED);
        saveFarmUser(farm, inactive, FarmUserRole.INACTIVE);
        saveFarmUser(farm, blocked, FarmUserRole.PRODUCER);

        mockMvc.perform(
                        get("/api/farms/{id}", farm.getId())
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(inactive.getId())).roles("USER")))
                .andExpect(status().isForbidden());

        mockMvc.perform(
                        get("/api/farms/{id}", farm.getId())
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(unlinked.getId())).roles("USER")))
                .andExpect(status().isForbidden());

        mockMvc.perform(
                        get("/api/farms/{id}", farm.getId())
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(blocked.getId())).roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldUpdateFarmAsAdminOrProducerOnly() throws Exception {
        Farm farm = saveFarm("Farm", FarmStatus.ACTIVE);
        User producer =
                saveUser("Producer", "producer@example.com", UserType.USER, UserStatus.ACTIVE);
        User employee =
                saveUser("Employee", "employee@example.com", UserType.USER, UserStatus.ACTIVE);
        User accountant =
                saveUser("Accountant", "accountant@example.com", UserType.USER, UserStatus.ACTIVE);
        User inactive =
                saveUser("Inactive", "inactive@example.com", UserType.USER, UserStatus.ACTIVE);
        saveFarmUser(farm, producer, FarmUserRole.PRODUCER);
        saveFarmUser(farm, employee, FarmUserRole.EMPLOYEE);
        saveFarmUser(farm, accountant, FarmUserRole.ACCOUNTANT);
        saveFarmUser(farm, inactive, FarmUserRole.INACTIVE);

        mockMvc.perform(
                        put("/api/farms/{id}", farm.getId())
                                .contextPath(CONTEXT_PATH)
                                .with(user("1").roles("ADMIN"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(farmBody("Admin Updated")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Admin Updated"));

        mockMvc.perform(
                        put("/api/farms/{id}", farm.getId())
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(producer.getId())).roles("USER"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(farmBody("Producer Updated")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Producer Updated"));

        expectCannotUpdateFarm(farm, employee);
        expectCannotUpdateFarm(farm, accountant);
        expectCannotUpdateFarm(farm, inactive);
    }

    @Test
    void shouldChangeStatusAndInactivateFarmAsAdminOnly() throws Exception {
        Farm farm = saveFarm("Farm", FarmStatus.ACTIVE);
        User user = saveUser("User", "user@example.com", UserType.USER, UserStatus.ACTIVE);
        String body =
                """
                {
                  "status": "INACTIVE"
                }
                """;

        mockMvc.perform(
                        patch("/api/farms/{id}/status", farm.getId())
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(user.getId())).roles("USER"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isForbidden());

        mockMvc.perform(
                        patch("/api/farms/{id}/status", farm.getId())
                                .contextPath(CONTEXT_PATH)
                                .with(user("1").roles("ADMIN"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));

        farm.setStatus(FarmStatus.ACTIVE);
        farmRepository.save(farm);

        mockMvc.perform(
                        delete("/api/farms/{id}", farm.getId())
                                .contextPath(CONTEXT_PATH)
                                .with(user("1").roles("ADMIN"))
                                .with(csrf()))
                .andExpect(status().isNoContent());

        Farm savedFarm = farmRepository.findById(farm.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(savedFarm.getStatus())
                .isEqualTo(FarmStatus.INACTIVE);
    }

    private void expectCanFindFarm(Farm farm, User user) throws Exception {
        mockMvc.perform(
                        get("/api/farms/{id}", farm.getId())
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(user.getId())).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(farm.getId()));
    }

    private void expectCannotUpdateFarm(Farm farm, User user) throws Exception {
        mockMvc.perform(
                        put("/api/farms/{id}", farm.getId())
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(user.getId())).roles("USER"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(farmBody("Rejected")))
                .andExpect(status().isForbidden());
    }

    private String farmBody(String name) {
        return """
                {
                  "name": "%s",
                  "document": "123",
                  "city": "Goiania",
                  "state": "GO",
                  "totalArea": 100.50,
                  "productionType": "MIXED"
                }
                """
                .formatted(name);
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
}
