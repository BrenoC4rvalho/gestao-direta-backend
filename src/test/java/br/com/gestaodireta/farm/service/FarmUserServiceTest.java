package br.com.gestaodireta.farm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.gestaodireta.farm.dto.FarmUserRequest;
import br.com.gestaodireta.farm.dto.FarmUserResponse;
import br.com.gestaodireta.farm.dto.FarmUserRoleUpdateRequest;
import br.com.gestaodireta.farm.entity.Farm;
import br.com.gestaodireta.farm.entity.FarmUser;
import br.com.gestaodireta.farm.enumeration.FarmStatus;
import br.com.gestaodireta.farm.enumeration.FarmUserRole;
import br.com.gestaodireta.farm.repository.FarmRepository;
import br.com.gestaodireta.farm.repository.FarmUserRepository;
import br.com.gestaodireta.shared.exception.BusinessException;
import br.com.gestaodireta.shared.exception.ResourceNotFoundException;
import br.com.gestaodireta.support.PostgresIntegrationTest;
import br.com.gestaodireta.user.entity.User;
import br.com.gestaodireta.user.enumeration.UserStatus;
import br.com.gestaodireta.user.enumeration.UserType;
import br.com.gestaodireta.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest
class FarmUserServiceTest extends PostgresIntegrationTest {

    @Autowired private FarmUserService farmUserService;

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
    void shouldLinkUserToFarmWithRequestedRole() {
        Farm farm = saveFarm("Farm", FarmStatus.ACTIVE);
        User user = saveUser("User", "user@example.com", UserType.USER);

        FarmUserResponse response =
                farmUserService.create(
                        farm.getId(), new FarmUserRequest(user.getId(), FarmUserRole.PRODUCER));

        assertThat(response.farmId()).isEqualTo(farm.getId());
        assertThat(response.userId()).isEqualTo(user.getId());
        assertThat(response.role()).isEqualTo(FarmUserRole.PRODUCER);
    }

    @Test
    void shouldRejectDuplicatedFarmUserLink() {
        Farm farm = saveFarm("Farm", FarmStatus.ACTIVE);
        User user = saveUser("User", "user@example.com", UserType.USER);
        saveFarmUser(farm, user, FarmUserRole.EMPLOYEE);

        assertThatThrownBy(
                        () ->
                                farmUserService.create(
                                        farm.getId(),
                                        new FarmUserRequest(user.getId(), FarmUserRole.ACCOUNTANT)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("User is already linked to this farm");
    }

    @Test
    void shouldRejectLinkWithMissingUserOrFarm() {
        Farm farm = saveFarm("Farm", FarmStatus.ACTIVE);
        User user = saveUser("User", "user@example.com", UserType.USER);

        assertThatThrownBy(
                        () ->
                                farmUserService.create(
                                        999L,
                                        new FarmUserRequest(user.getId(), FarmUserRole.EMPLOYEE)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Farm not found");

        assertThatThrownBy(
                        () ->
                                farmUserService.create(
                                        farm.getId(),
                                        new FarmUserRequest(999L, FarmUserRole.EMPLOYEE)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("User not found");
    }

    @Test
    void shouldRejectLinkWhenFarmIsInactive() {
        Farm farm = saveFarm("Farm", FarmStatus.INACTIVE);
        User user = saveUser("User", "user@example.com", UserType.USER);

        assertThatThrownBy(
                        () ->
                                farmUserService.create(
                                        farm.getId(),
                                        new FarmUserRequest(user.getId(), FarmUserRole.EMPLOYEE)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Inactive farm cannot receive user links");
    }

    @Test
    void shouldListFarmUsers() {
        Farm farm = saveFarm("Farm", FarmStatus.ACTIVE);
        User employee = saveUser("Employee", "employee@example.com", UserType.USER);
        User accountant = saveUser("Accountant", "accountant@example.com", UserType.USER);
        saveFarmUser(farm, employee, FarmUserRole.EMPLOYEE);
        saveFarmUser(farm, accountant, FarmUserRole.ACCOUNTANT);

        assertThat(farmUserService.findByFarmId(farm.getId()))
                .extracting(FarmUserResponse::userEmail)
                .containsExactly("employee@example.com", "accountant@example.com");
    }

    @Test
    void shouldUpdateFarmUserRole() {
        Farm farm = saveFarm("Farm", FarmStatus.ACTIVE);
        User user = saveUser("User", "user@example.com", UserType.USER);
        saveFarmUser(farm, user, FarmUserRole.EMPLOYEE);

        FarmUserResponse response =
                farmUserService.updateRole(
                        farm.getId(),
                        user.getId(),
                        new FarmUserRoleUpdateRequest(FarmUserRole.ACCOUNTANT));

        assertThat(response.role()).isEqualTo(FarmUserRole.ACCOUNTANT);
    }

    @Test
    void shouldInactivateFarmUserWithoutDeletingIt() {
        Farm farm = saveFarm("Farm", FarmStatus.ACTIVE);
        User user = saveUser("User", "user@example.com", UserType.USER);
        saveFarmUser(farm, user, FarmUserRole.EMPLOYEE);

        farmUserService.inactivate(farm.getId(), user.getId());

        FarmUser farmUser =
                farmUserRepository.findByFarmIdAndUserId(farm.getId(), user.getId()).orElseThrow();
        assertThat(farmUser.getRole()).isEqualTo(FarmUserRole.INACTIVE);
    }

    @Test
    void shouldKeepAtLeastOneActiveProducer() {
        Farm farm = saveFarm("Farm", FarmStatus.ACTIVE);
        User producer = saveUser("Producer", "producer@example.com", UserType.USER);
        saveFarmUser(farm, producer, FarmUserRole.PRODUCER);

        assertThatThrownBy(
                        () ->
                                farmUserService.updateRole(
                                        farm.getId(),
                                        producer.getId(),
                                        new FarmUserRoleUpdateRequest(FarmUserRole.INACTIVE)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Farm must have at least one active producer");

        assertThatThrownBy(() -> farmUserService.inactivate(farm.getId(), producer.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Farm must have at least one active producer");
    }

    @Test
    void shouldAllowChangingProducerWhenAnotherProducerRemainsActive() {
        Farm farm = saveFarm("Farm", FarmStatus.ACTIVE);
        User firstProducer = saveUser("First Producer", "first@example.com", UserType.USER);
        User secondProducer = saveUser("Second Producer", "second@example.com", UserType.USER);
        saveFarmUser(farm, firstProducer, FarmUserRole.PRODUCER);
        saveFarmUser(farm, secondProducer, FarmUserRole.PRODUCER);

        FarmUserResponse response =
                farmUserService.updateRole(
                        farm.getId(),
                        firstProducer.getId(),
                        new FarmUserRoleUpdateRequest(FarmUserRole.INACTIVE));

        assertThat(response.role()).isEqualTo(FarmUserRole.INACTIVE);
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
