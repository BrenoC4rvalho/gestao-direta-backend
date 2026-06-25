package br.com.gestaodireta.farm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.gestaodireta.farm.dto.FarmAccessPermissions;
import br.com.gestaodireta.farm.dto.FarmAccessResponse;
import br.com.gestaodireta.farm.entity.Farm;
import br.com.gestaodireta.farm.entity.FarmUser;
import br.com.gestaodireta.farm.enumeration.FarmStatus;
import br.com.gestaodireta.farm.enumeration.FarmUserRole;
import br.com.gestaodireta.farm.repository.FarmRepository;
import br.com.gestaodireta.farm.repository.FarmUserRepository;
import br.com.gestaodireta.shared.exception.ForbiddenException;
import br.com.gestaodireta.shared.exception.ResourceNotFoundException;
import br.com.gestaodireta.support.PostgresIntegrationTest;
import br.com.gestaodireta.user.entity.User;
import br.com.gestaodireta.user.enumeration.UserStatus;
import br.com.gestaodireta.user.enumeration.UserType;
import br.com.gestaodireta.user.repository.UserRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest
class FarmAccessServiceTest extends PostgresIntegrationTest {

    @Autowired private FarmAccessService farmAccessService;

    @Autowired private FarmRepository farmRepository;

    @Autowired private FarmUserRepository farmUserRepository;

    @Autowired private UserRepository userRepository;

    @Autowired private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        farmUserRepository.deleteAll();
        farmRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void shouldGrantAllPermissionsToAdminForActiveAndInactiveFarm() {
        User admin = saveUser("Admin", "admin@example.com", UserType.ADMIN, UserStatus.ACTIVE);
        Farm activeFarm = saveFarm("Active Farm", FarmStatus.ACTIVE);
        Farm inactiveFarm = saveFarm("Inactive Farm", FarmStatus.INACTIVE);
        authenticateAs(admin, "ROLE_ADMIN");

        FarmAccessResponse activeResponse = farmAccessService.getAccess(activeFarm.getId());
        FarmAccessResponse inactiveResponse = farmAccessService.getAccess(inactiveFarm.getId());

        assertAdminAccess(activeResponse, activeFarm, admin);
        assertAdminAccess(inactiveResponse, inactiveFarm, admin);
    }

    @Test
    void shouldReturnProducerPermissions() {
        FarmAccessResponse response = getAccessForRole(FarmUserRole.PRODUCER);

        assertThat(response.role()).isEqualTo(FarmUserRole.PRODUCER);
        assertPermissions(
                response.permissions(), true, true, false, true, true, true, true, false, false);
    }

    @Test
    void shouldReturnEmployeePermissions() {
        FarmAccessResponse response = getAccessForRole(FarmUserRole.EMPLOYEE);

        assertThat(response.role()).isEqualTo(FarmUserRole.EMPLOYEE);
        assertPermissions(
                response.permissions(), true, false, false, false, true, true, false, false, false);
    }

    @Test
    void shouldReturnAccountantPermissions() {
        FarmAccessResponse response = getAccessForRole(FarmUserRole.ACCOUNTANT);

        assertThat(response.role()).isEqualTo(FarmUserRole.ACCOUNTANT);
        assertPermissions(
                response.permissions(),
                true,
                false,
                false,
                false,
                true,
                false,
                false,
                false,
                false);
    }

    @Test
    void shouldRejectInactiveRoleOrMissingLink() {
        Farm farm = saveFarm("Farm", FarmStatus.ACTIVE);
        User inactiveLinkUser =
                saveUser("Inactive", "inactive@example.com", UserType.USER, UserStatus.ACTIVE);
        User unlinkedUser =
                saveUser("Unlinked", "unlinked@example.com", UserType.USER, UserStatus.ACTIVE);
        saveFarmUser(farm, inactiveLinkUser, FarmUserRole.INACTIVE);

        authenticateAs(inactiveLinkUser, "ROLE_USER");
        assertAccessDenied(farm.getId());

        authenticateAs(unlinkedUser, "ROLE_USER");
        assertAccessDenied(farm.getId());
    }

    @Test
    void shouldRejectInactiveFarmForCommonUser() {
        Farm farm = saveFarm("Inactive Farm", FarmStatus.INACTIVE);
        User producer =
                saveUser("Producer", "producer@example.com", UserType.USER, UserStatus.ACTIVE);
        saveFarmUser(farm, producer, FarmUserRole.PRODUCER);
        authenticateAs(producer, "ROLE_USER");

        assertAccessDenied(farm.getId());
    }

    @Test
    void shouldRejectInactiveOrBlockedAuthenticatedUser() {
        Farm farm = saveFarm("Farm", FarmStatus.ACTIVE);
        User inactive =
                saveUser("Inactive", "inactive@example.com", UserType.USER, UserStatus.INACTIVE);
        User blocked =
                saveUser("Blocked", "blocked@example.com", UserType.USER, UserStatus.BLOCKED);
        saveFarmUser(farm, inactive, FarmUserRole.PRODUCER);
        saveFarmUser(farm, blocked, FarmUserRole.PRODUCER);

        authenticateAs(inactive, "ROLE_USER");
        assertAccessDenied(farm.getId());

        authenticateAs(blocked, "ROLE_USER");
        assertAccessDenied(farm.getId());
    }

    @Test
    void shouldReturnNotFoundForMissingFarm() {
        User admin = saveUser("Admin", "admin@example.com", UserType.ADMIN, UserStatus.ACTIVE);
        authenticateAs(admin, "ROLE_ADMIN");

        assertThatThrownBy(() -> farmAccessService.getAccess(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Farm not found");
    }

    private FarmAccessResponse getAccessForRole(FarmUserRole role) {
        Farm farm = saveFarm(role.name() + " Farm", FarmStatus.ACTIVE);
        User user =
                saveUser(
                        role.name(),
                        role.name().toLowerCase() + "@example.com",
                        UserType.USER,
                        UserStatus.ACTIVE);
        saveFarmUser(farm, user, role);
        authenticateAs(user, "ROLE_USER");

        FarmAccessResponse response = farmAccessService.getAccess(farm.getId());

        assertThat(response.farmId()).isEqualTo(farm.getId());
        assertThat(response.farmName()).isEqualTo(farm.getName());
        assertThat(response.userId()).isEqualTo(user.getId());
        assertThat(response.userType()).isEqualTo(UserType.USER);

        return response;
    }

    private void assertAdminAccess(FarmAccessResponse response, Farm farm, User admin) {
        assertThat(response.farmId()).isEqualTo(farm.getId());
        assertThat(response.farmName()).isEqualTo(farm.getName());
        assertThat(response.userId()).isEqualTo(admin.getId());
        assertThat(response.userType()).isEqualTo(UserType.ADMIN);
        assertThat(response.role()).isNull();
        assertPermissions(
                response.permissions(), true, true, true, true, true, true, true, true, true);
    }

    private void assertAccessDenied(Long farmId) {
        assertThatThrownBy(() -> farmAccessService.getAccess(farmId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Access denied");
    }

    private void assertPermissions(
            FarmAccessPermissions permissions,
            boolean canViewFarm,
            boolean canEditFarm,
            boolean canChangeFarmStatus,
            boolean canManageFarmUsers,
            boolean canViewFinancial,
            boolean canManageTransactions,
            boolean canManageCategories,
            boolean canManageGlobalCategories,
            boolean canCreateFarm) {
        assertThat(permissions.canViewFarm()).isEqualTo(canViewFarm);
        assertThat(permissions.canEditFarm()).isEqualTo(canEditFarm);
        assertThat(permissions.canChangeFarmStatus()).isEqualTo(canChangeFarmStatus);
        assertThat(permissions.canManageFarmUsers()).isEqualTo(canManageFarmUsers);
        assertThat(permissions.canViewFinancial()).isEqualTo(canViewFinancial);
        assertThat(permissions.canManageTransactions()).isEqualTo(canManageTransactions);
        assertThat(permissions.canManageCategories()).isEqualTo(canManageCategories);
        assertThat(permissions.canManageGlobalCategories()).isEqualTo(canManageGlobalCategories);
        assertThat(permissions.canCreateFarm()).isEqualTo(canCreateFarm);
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

    private void authenticateAs(User user, String role) {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        String.valueOf(user.getId()),
                        null,
                        List.of(new SimpleGrantedAuthority(role)));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
