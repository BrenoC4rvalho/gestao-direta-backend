package br.com.gestaodireta.farm.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class FarmUserControllerTest extends PostgresIntegrationTest {

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
    void shouldLinkUsersAsAdminWithAllowedRoles() throws Exception {
        Farm farm = saveFarm("Farm", FarmStatus.ACTIVE);
        User producer = saveUser("Producer", "producer@example.com", UserType.USER);
        User employee = saveUser("Employee", "employee@example.com", UserType.USER);
        User accountant = saveUser("Accountant", "accountant@example.com", UserType.USER);

        expectAdminCanCreateFarmUser(farm, producer, FarmUserRole.PRODUCER);
        expectAdminCanCreateFarmUser(farm, employee, FarmUserRole.EMPLOYEE);
        expectAdminCanCreateFarmUser(farm, accountant, FarmUserRole.ACCOUNTANT);
    }

    @Test
    void shouldRejectDuplicatedOrMissingFarmUserLink() throws Exception {
        Farm farm = saveFarm("Farm", FarmStatus.ACTIVE);
        User employee = saveUser("Employee", "employee@example.com", UserType.USER);
        saveFarmUser(farm, employee, FarmUserRole.EMPLOYEE);

        mockMvc.perform(
                        post("/api/farms/{farmId}/users", farm.getId())
                                .contextPath(CONTEXT_PATH)
                                .with(user("1").roles("ADMIN"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(farmUserBody(employee.getId(), FarmUserRole.ACCOUNTANT)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("User is already linked to this farm"));

        mockMvc.perform(
                        post("/api/farms/{farmId}/users", 999L)
                                .contextPath(CONTEXT_PATH)
                                .with(user("1").roles("ADMIN"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(farmUserBody(employee.getId(), FarmUserRole.EMPLOYEE)))
                .andExpect(status().isNotFound());

        mockMvc.perform(
                        post("/api/farms/{farmId}/users", farm.getId())
                                .contextPath(CONTEXT_PATH)
                                .with(user("1").roles("ADMIN"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(farmUserBody(999L, FarmUserRole.EMPLOYEE)))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldRejectLinkWhenFarmIsInactiveOrTargetUserIsAdmin() throws Exception {
        Farm inactiveFarm = saveFarm("Inactive Farm", FarmStatus.INACTIVE);
        Farm activeFarm = saveFarm("Active Farm", FarmStatus.ACTIVE);
        User employee = saveUser("Employee", "employee@example.com", UserType.USER);
        User admin = saveUser("Admin", "admin@example.com", UserType.ADMIN);

        mockMvc.perform(
                        post("/api/farms/{farmId}/users", inactiveFarm.getId())
                                .contextPath(CONTEXT_PATH)
                                .with(user("1").roles("ADMIN"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(farmUserBody(employee.getId(), FarmUserRole.EMPLOYEE)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Inactive farm cannot receive user links"));

        mockMvc.perform(
                        post("/api/farms/{farmId}/users", activeFarm.getId())
                                .contextPath(CONTEXT_PATH)
                                .with(user("1").roles("ADMIN"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(farmUserBody(admin.getId(), FarmUserRole.EMPLOYEE)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Only USER can be linked to farm"));
    }

    @Test
    void shouldAllowProducerToLinkOnlyEmployeeOrAccountantInManagedFarm() throws Exception {
        Farm managedFarm = saveFarm("Managed Farm", FarmStatus.ACTIVE);
        Farm otherFarm = saveFarm("Other Farm", FarmStatus.ACTIVE);
        User producer = saveUser("Producer", "producer@example.com", UserType.USER);
        User employee = saveUser("Employee", "employee@example.com", UserType.USER);
        User accountant = saveUser("Accountant", "accountant@example.com", UserType.USER);
        User newProducer = saveUser("New Producer", "new-producer@example.com", UserType.USER);
        User admin = saveUser("Admin", "admin@example.com", UserType.ADMIN);
        saveFarmUser(managedFarm, producer, FarmUserRole.PRODUCER);

        mockMvc.perform(
                        post("/api/farms/{farmId}/users", managedFarm.getId())
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(producer.getId())).roles("USER"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(farmUserBody(employee.getId(), FarmUserRole.EMPLOYEE)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("EMPLOYEE"));

        mockMvc.perform(
                        post("/api/farms/{farmId}/users", managedFarm.getId())
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(producer.getId())).roles("USER"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(farmUserBody(accountant.getId(), FarmUserRole.ACCOUNTANT)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("ACCOUNTANT"));

        expectProducerCannotCreateFarmUser(
                producer, managedFarm, newProducer, FarmUserRole.PRODUCER);
        expectProducerCannotCreateFarmUser(producer, managedFarm, admin, FarmUserRole.EMPLOYEE);
        expectProducerCannotCreateFarmUser(producer, otherFarm, newProducer, FarmUserRole.EMPLOYEE);
    }

    @Test
    void shouldListFarmUsersOnlyForAdminOrProducer() throws Exception {
        Farm farm = saveFarm("Farm", FarmStatus.ACTIVE);
        User producer = saveUser("Producer", "producer@example.com", UserType.USER);
        User employee = saveUser("Employee", "employee@example.com", UserType.USER);
        User accountant = saveUser("Accountant", "accountant@example.com", UserType.USER);
        saveFarmUser(farm, producer, FarmUserRole.PRODUCER);
        saveFarmUser(farm, employee, FarmUserRole.EMPLOYEE);
        saveFarmUser(farm, accountant, FarmUserRole.ACCOUNTANT);

        mockMvc.perform(
                        get("/api/farms/{farmId}/users", farm.getId())
                                .contextPath(CONTEXT_PATH)
                                .with(user("1").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(3)));

        mockMvc.perform(
                        get("/api/farms/{farmId}/users", farm.getId())
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(producer.getId())).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(3)));

        mockMvc.perform(
                        get("/api/farms/{farmId}/users", farm.getId())
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(employee.getId())).roles("USER")))
                .andExpect(status().isForbidden());

        mockMvc.perform(
                        get("/api/farms/{farmId}/users", farm.getId())
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(accountant.getId())).roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldListFarmUsersWithFiltersAndRejectInvalidRole() throws Exception {
        Farm farm = saveFarm("Farm", FarmStatus.ACTIVE);
        User employee = saveUser("Employee", "employee@example.com", UserType.USER);
        User accountant = saveUser("Accountant", "accountant@example.com", UserType.USER);
        saveFarmUser(farm, employee, FarmUserRole.EMPLOYEE);
        saveFarmUser(farm, accountant, FarmUserRole.ACCOUNTANT);

        mockMvc.perform(
                        get("/api/farms/{farmId}/users", farm.getId())
                                .contextPath(CONTEXT_PATH)
                                .param("search", "employee")
                                .param("role", "EMPLOYEE")
                                .param("sort", "userEmail")
                                .with(user("1").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].userEmail").value("employee@example.com"));

        mockMvc.perform(
                        get("/api/farms/{farmId}/users", farm.getId())
                                .contextPath(CONTEXT_PATH)
                                .param("role", "INVALID")
                                .with(user("1").roles("ADMIN")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldChangeRolesAccordingToAdminAndProducerRules() throws Exception {
        Farm farm = saveFarm("Farm", FarmStatus.ACTIVE);
        User producer = saveUser("Producer", "producer@example.com", UserType.USER);
        User employee = saveUser("Employee", "employee@example.com", UserType.USER);
        User secondEmployee =
                saveUser("Second Employee", "second-employee@example.com", UserType.USER);
        User accountant = saveUser("Accountant", "accountant@example.com", UserType.USER);
        User inactive = saveUser("Inactive", "inactive@example.com", UserType.USER);
        saveFarmUser(farm, producer, FarmUserRole.PRODUCER);
        saveFarmUser(farm, employee, FarmUserRole.EMPLOYEE);
        saveFarmUser(farm, secondEmployee, FarmUserRole.EMPLOYEE);
        saveFarmUser(farm, accountant, FarmUserRole.ACCOUNTANT);
        saveFarmUser(farm, inactive, FarmUserRole.INACTIVE);

        mockMvc.perform(
                        patch(
                                        "/api/farms/{farmId}/users/{userId}/role",
                                        farm.getId(),
                                        employee.getId())
                                .contextPath(CONTEXT_PATH)
                                .with(user("1").roles("ADMIN"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(roleBody(FarmUserRole.PRODUCER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("PRODUCER"));

        mockMvc.perform(
                        patch(
                                        "/api/farms/{farmId}/users/{userId}/role",
                                        farm.getId(),
                                        secondEmployee.getId())
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(producer.getId())).roles("USER"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(roleBody(FarmUserRole.ACCOUNTANT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ACCOUNTANT"));

        mockMvc.perform(
                        patch(
                                        "/api/farms/{farmId}/users/{userId}/role",
                                        farm.getId(),
                                        accountant.getId())
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(producer.getId())).roles("USER"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(roleBody(FarmUserRole.EMPLOYEE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("EMPLOYEE"));

        mockMvc.perform(
                        patch(
                                        "/api/farms/{farmId}/users/{userId}/role",
                                        farm.getId(),
                                        accountant.getId())
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(producer.getId())).roles("USER"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(roleBody(FarmUserRole.ACCOUNTANT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ACCOUNTANT"));

        mockMvc.perform(
                        patch(
                                        "/api/farms/{farmId}/users/{userId}/role",
                                        farm.getId(),
                                        accountant.getId())
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(producer.getId())).roles("USER"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(roleBody(FarmUserRole.INACTIVE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("INACTIVE"));

        expectCannotChangeRole(farm, producer, employee, FarmUserRole.ACCOUNTANT);
        expectCannotChangeRole(farm, producer, secondEmployee, FarmUserRole.PRODUCER);
        expectCannotChangeRole(farm, producer, inactive, FarmUserRole.EMPLOYEE);
    }

    @Test
    void shouldRejectRoleChangesFromEmployeeAccountantOrInactive() throws Exception {
        Farm farm = saveFarm("Farm", FarmStatus.ACTIVE);
        User producer = saveUser("Producer", "producer@example.com", UserType.USER);
        User employee = saveUser("Employee", "employee@example.com", UserType.USER);
        User accountant = saveUser("Accountant", "accountant@example.com", UserType.USER);
        User inactive = saveUser("Inactive", "inactive@example.com", UserType.USER);
        User target = saveUser("Target", "target@example.com", UserType.USER);
        saveFarmUser(farm, producer, FarmUserRole.PRODUCER);
        saveFarmUser(farm, employee, FarmUserRole.EMPLOYEE);
        saveFarmUser(farm, accountant, FarmUserRole.ACCOUNTANT);
        saveFarmUser(farm, inactive, FarmUserRole.INACTIVE);
        saveFarmUser(farm, target, FarmUserRole.EMPLOYEE);

        expectCannotChangeRole(farm, employee, target, FarmUserRole.ACCOUNTANT);
        expectCannotChangeRole(farm, accountant, target, FarmUserRole.ACCOUNTANT);
        expectCannotChangeRole(farm, inactive, target, FarmUserRole.ACCOUNTANT);
    }

    @Test
    void shouldRemoveFarmUserWithoutDeletingAndPreventProducerRemovingProducer() throws Exception {
        Farm farm = saveFarm("Farm", FarmStatus.ACTIVE);
        User producer = saveUser("Producer", "producer@example.com", UserType.USER);
        User otherProducer =
                saveUser("Other Producer", "other-producer@example.com", UserType.USER);
        User employee = saveUser("Employee", "employee@example.com", UserType.USER);
        saveFarmUser(farm, producer, FarmUserRole.PRODUCER);
        saveFarmUser(farm, otherProducer, FarmUserRole.PRODUCER);
        saveFarmUser(farm, employee, FarmUserRole.EMPLOYEE);

        mockMvc.perform(
                        delete("/api/farms/{farmId}/users/{userId}", farm.getId(), employee.getId())
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(producer.getId())).roles("USER"))
                                .with(csrf()))
                .andExpect(status().isNoContent());

        FarmUser savedLink =
                farmUserRepository
                        .findByFarmIdAndUserId(farm.getId(), employee.getId())
                        .orElseThrow();
        assertThat(savedLink.getRole()).isEqualTo(FarmUserRole.INACTIVE);

        mockMvc.perform(
                        delete(
                                        "/api/farms/{farmId}/users/{userId}",
                                        farm.getId(),
                                        otherProducer.getId())
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(producer.getId())).roles("USER"))
                                .with(csrf()))
                .andExpect(status().isForbidden());
    }

    private void expectAdminCanCreateFarmUser(Farm farm, User user, FarmUserRole role)
            throws Exception {
        mockMvc.perform(
                        post("/api/farms/{farmId}/users", farm.getId())
                                .contextPath(CONTEXT_PATH)
                                .with(user("1").roles("ADMIN"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(farmUserBody(user.getId(), role)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(user.getId()))
                .andExpect(jsonPath("$.role").value(role.name()));
    }

    private void expectProducerCannotCreateFarmUser(
            User producer, Farm farm, User targetUser, FarmUserRole role) throws Exception {
        mockMvc.perform(
                        post("/api/farms/{farmId}/users", farm.getId())
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(producer.getId())).roles("USER"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(farmUserBody(targetUser.getId(), role)))
                .andExpect(status().isForbidden());
    }

    private void expectCannotChangeRole(
            Farm farm, User authenticatedUser, User targetUser, FarmUserRole role)
            throws Exception {
        mockMvc.perform(
                        patch(
                                        "/api/farms/{farmId}/users/{userId}/role",
                                        farm.getId(),
                                        targetUser.getId())
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(authenticatedUser.getId())).roles("USER"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(roleBody(role)))
                .andExpect(status().isForbidden());
    }

    private String farmUserBody(Long userId, FarmUserRole role) {
        return """
                {
                  "userId": %d,
                  "role": "%s"
                }
                """
                .formatted(userId, role.name());
    }

    private String roleBody(FarmUserRole role) {
        return """
                {
                  "role": "%s"
                }
                """
                .formatted(role.name());
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
}
