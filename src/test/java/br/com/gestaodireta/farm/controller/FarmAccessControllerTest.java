package br.com.gestaodireta.farm.controller;

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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class FarmAccessControllerTest extends PostgresIntegrationTest {

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
    void shouldReturnAdminAccessContextForInactiveFarm() throws Exception {
        User admin = saveUser("Admin", "admin@example.com", UserType.ADMIN, UserStatus.ACTIVE);
        Farm farm = saveFarm("Fazenda Boa Safra", FarmStatus.INACTIVE);

        mockMvc.perform(
                        get("/api/farms/{farmId}/access", farm.getId())
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(admin.getId())).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.farmId").value(farm.getId()))
                .andExpect(jsonPath("$.farmName").value("Fazenda Boa Safra"))
                .andExpect(jsonPath("$.userId").value(admin.getId()))
                .andExpect(jsonPath("$.userType").value("ADMIN"))
                .andExpect(jsonPath("$.role").doesNotExist())
                .andExpect(jsonPath("$.permissions.canViewFarm").value(true))
                .andExpect(jsonPath("$.permissions.canEditFarm").value(true))
                .andExpect(jsonPath("$.permissions.canChangeFarmStatus").value(true))
                .andExpect(jsonPath("$.permissions.canManageFarmUsers").value(true))
                .andExpect(jsonPath("$.permissions.canViewFinancial").value(true))
                .andExpect(jsonPath("$.permissions.canManageTransactions").value(true))
                .andExpect(jsonPath("$.permissions.canManageCategories").value(true))
                .andExpect(jsonPath("$.permissions.canManageGlobalCategories").value(true))
                .andExpect(jsonPath("$.permissions.canCreateFarm").value(true));
    }

    @Test
    void shouldReturnProducerAccessContext() throws Exception {
        User producer =
                saveUser("Producer", "producer@example.com", UserType.USER, UserStatus.ACTIVE);
        Farm farm = saveFarm("Fazenda Boa Safra", FarmStatus.ACTIVE);
        saveFarmUser(farm, producer, FarmUserRole.PRODUCER);

        mockMvc.perform(
                        get("/api/farms/{farmId}/access", farm.getId())
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(producer.getId())).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.farmId").value(farm.getId()))
                .andExpect(jsonPath("$.farmName").value("Fazenda Boa Safra"))
                .andExpect(jsonPath("$.userId").value(producer.getId()))
                .andExpect(jsonPath("$.userType").value("USER"))
                .andExpect(jsonPath("$.role").value("PRODUCER"))
                .andExpect(jsonPath("$.permissions.canViewFarm").value(true))
                .andExpect(jsonPath("$.permissions.canEditFarm").value(true))
                .andExpect(jsonPath("$.permissions.canChangeFarmStatus").value(false))
                .andExpect(jsonPath("$.permissions.canManageFarmUsers").value(true))
                .andExpect(jsonPath("$.permissions.canViewFinancial").value(true))
                .andExpect(jsonPath("$.permissions.canManageTransactions").value(true))
                .andExpect(jsonPath("$.permissions.canManageCategories").value(true))
                .andExpect(jsonPath("$.permissions.canManageGlobalCategories").value(false))
                .andExpect(jsonPath("$.permissions.canCreateFarm").value(false));
    }

    @Test
    void shouldReturnForbiddenForUserWithoutAccess() throws Exception {
        User user = saveUser("User", "user@example.com", UserType.USER, UserStatus.ACTIVE);
        Farm farm = saveFarm("Farm", FarmStatus.ACTIVE);

        mockMvc.perform(
                        get("/api/farms/{farmId}/access", farm.getId())
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(user.getId())).roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldReturnNotFoundForMissingFarm() throws Exception {
        User admin = saveUser("Admin", "admin@example.com", UserType.ADMIN, UserStatus.ACTIVE);

        mockMvc.perform(
                        get("/api/farms/{farmId}/access", 999L)
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(admin.getId())).roles("ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldRequireAuthentication() throws Exception {
        Farm farm = saveFarm("Farm", FarmStatus.ACTIVE);

        mockMvc.perform(get("/api/farms/{farmId}/access", farm.getId()).contextPath(CONTEXT_PATH))
                .andExpect(status().isUnauthorized());
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
