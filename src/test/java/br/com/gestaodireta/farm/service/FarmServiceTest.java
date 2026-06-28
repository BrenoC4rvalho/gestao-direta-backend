package br.com.gestaodireta.farm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.gestaodireta.farm.dto.FarmFilterRequest;
import br.com.gestaodireta.farm.dto.FarmRequest;
import br.com.gestaodireta.farm.dto.FarmResponse;
import br.com.gestaodireta.farm.dto.FarmStatusUpdateRequest;
import br.com.gestaodireta.farm.dto.FarmUpdateRequest;
import br.com.gestaodireta.farm.entity.Farm;
import br.com.gestaodireta.farm.entity.FarmUser;
import br.com.gestaodireta.farm.enumeration.FarmStatus;
import br.com.gestaodireta.farm.enumeration.FarmUserRole;
import br.com.gestaodireta.farm.enumeration.ProductionType;
import br.com.gestaodireta.farm.repository.FarmRepository;
import br.com.gestaodireta.farm.repository.FarmUserRepository;
import br.com.gestaodireta.shared.exception.ForbiddenException;
import br.com.gestaodireta.shared.pagination.PaginationParams;
import br.com.gestaodireta.shared.response.PageResponse;
import br.com.gestaodireta.support.PostgresIntegrationTest;
import br.com.gestaodireta.user.entity.User;
import br.com.gestaodireta.user.enumeration.UserStatus;
import br.com.gestaodireta.user.enumeration.UserType;
import br.com.gestaodireta.user.repository.UserRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest
class FarmServiceTest extends PostgresIntegrationTest {

    @Autowired private FarmService farmService;

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
    void shouldCreateFarmWithActiveStatus() {
        FarmResponse response = farmService.create(farmRequest("Fazenda Boa Esperanca"));

        assertThat(response.id()).isNotNull();
        assertThat(response.name()).isEqualTo("Fazenda Boa Esperanca");
        assertThat(response.status()).isEqualTo(FarmStatus.ACTIVE);
        assertThat(response.createdAt()).isNotNull();
    }

    @Test
    void shouldListAllFarmsForAdmin() {
        User admin = saveUser("Admin", "admin@example.com", UserType.ADMIN, UserStatus.ACTIVE);
        saveFarm("Fazenda B", FarmStatus.ACTIVE);
        saveFarm("Fazenda A", FarmStatus.INACTIVE);
        authenticateAs(admin, "ROLE_ADMIN");

        PageResponse<FarmResponse> response = farmService.findAll(sortedByName());

        assertThat(response.content())
                .extracting(FarmResponse::name)
                .containsExactly("Fazenda A", "Fazenda B");
    }

    @Test
    void shouldListOnlyActiveLinkedFarmsForUser() {
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
        authenticateAs(user, "ROLE_USER");

        PageResponse<FarmResponse> response = farmService.findAll(sortedByName());

        assertThat(response.content())
                .extracting(FarmResponse::name)
                .containsExactly("Accountant Farm", "Employee Farm", "Producer Farm");
    }

    @Test
    void shouldFilterFarmsForAdmin() {
        User admin = saveUser("Admin", "admin@example.com", UserType.ADMIN, UserStatus.ACTIVE);
        saveFarm("Soy Farm", FarmStatus.ACTIVE, "12.345.678/0001-99", ProductionType.AGRICULTURE);
        saveFarm("Cattle Farm", FarmStatus.INACTIVE, "98765432000100", ProductionType.LIVESTOCK);
        authenticateAs(admin, "ROLE_ADMIN");

        PageResponse<FarmResponse> response =
                farmService.findAll(
                        new FarmFilterRequest(
                                " soy ",
                                "12345678000199",
                                ProductionType.AGRICULTURE,
                                FarmStatus.ACTIVE),
                        sortedByName());

        assertThat(response.content()).extracting(FarmResponse::name).containsExactly("Soy Farm");
    }

    @Test
    void shouldIgnoreInactiveStatusFilterForUserFarmList() {
        User user = saveUser("User", "user@example.com", UserType.USER, UserStatus.ACTIVE);
        Farm activeFarm = saveFarm("Active Farm", FarmStatus.ACTIVE);
        Farm inactiveFarm = saveFarm("Inactive Farm", FarmStatus.INACTIVE);
        saveFarmUser(activeFarm, user, FarmUserRole.PRODUCER);
        saveFarmUser(inactiveFarm, user, FarmUserRole.PRODUCER);
        authenticateAs(user, "ROLE_USER");

        PageResponse<FarmResponse> response =
                farmService.findAll(
                        new FarmFilterRequest(null, null, null, FarmStatus.INACTIVE),
                        sortedByName());

        assertThat(response.content())
                .extracting(FarmResponse::name)
                .containsExactly("Active Farm");
    }

    @Test
    void shouldFilterLinkedFarmsForUser() {
        User user = saveUser("User", "user@example.com", UserType.USER, UserStatus.ACTIVE);
        Farm soyFarm =
                saveFarm(
                        "Soy Farm",
                        FarmStatus.ACTIVE,
                        "12.345.678/0001-99",
                        ProductionType.AGRICULTURE);
        Farm cattleFarm =
                saveFarm(
                        "Cattle Farm",
                        FarmStatus.ACTIVE,
                        "98765432000100",
                        ProductionType.LIVESTOCK);
        saveFarmUser(soyFarm, user, FarmUserRole.PRODUCER);
        saveFarmUser(cattleFarm, user, FarmUserRole.PRODUCER);
        authenticateAs(user, "ROLE_USER");

        PageResponse<FarmResponse> response =
                farmService.findAll(
                        new FarmFilterRequest(
                                "SOY", "12345678000199", ProductionType.AGRICULTURE, null),
                        sortedByName());

        assertThat(response.content()).extracting(FarmResponse::name).containsExactly("Soy Farm");
    }

    @Test
    void shouldRejectFarmListWhenUserStatusIsInactiveOrBlocked() {
        User inactive =
                saveUser("Inactive", "inactive@example.com", UserType.USER, UserStatus.INACTIVE);
        authenticateAs(inactive, "ROLE_USER");

        assertThatThrownBy(() -> farmService.findAll(new PaginationParams()))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Access denied");

        User blocked =
                saveUser("Blocked", "blocked@example.com", UserType.USER, UserStatus.BLOCKED);
        authenticateAs(blocked, "ROLE_USER");

        assertThatThrownBy(() -> farmService.findAll(new PaginationParams()))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Access denied");
    }

    @Test
    void shouldUpdateFarmData() {
        Farm farm = saveFarm("Old Farm", FarmStatus.ACTIVE);

        FarmResponse response =
                farmService.update(
                        farm.getId(),
                        new FarmUpdateRequest(
                                "Updated Farm",
                                "123",
                                "Goiania",
                                "GO",
                                new BigDecimal("150.50"),
                                ProductionType.MIXED));

        assertThat(response.name()).isEqualTo("Updated Farm");
        assertThat(response.document()).isEqualTo("123");
        assertThat(response.productionType()).isEqualTo(ProductionType.MIXED);
    }

    @Test
    void shouldUpdateFarmStatus() {
        Farm farm = saveFarm("Farm", FarmStatus.ACTIVE);

        FarmResponse response =
                farmService.updateStatus(
                        farm.getId(), new FarmStatusUpdateRequest(FarmStatus.INACTIVE));

        assertThat(response.status()).isEqualTo(FarmStatus.INACTIVE);
    }

    @Test
    void shouldInactivateFarmWithoutDeletingIt() {
        Farm farm = saveFarm("Farm", FarmStatus.ACTIVE);

        farmService.inactivate(farm.getId());

        Farm savedFarm = farmRepository.findById(farm.getId()).orElseThrow();
        assertThat(savedFarm.getStatus()).isEqualTo(FarmStatus.INACTIVE);
    }

    private PaginationParams sortedByName() {
        PaginationParams paginationParams = new PaginationParams();
        paginationParams.setSort("name");
        paginationParams.setDirection(Sort.Direction.ASC);

        return paginationParams;
    }

    private FarmRequest farmRequest(String name) {
        return new FarmRequest(name, "123", "Goiania", "GO", BigDecimal.TEN, ProductionType.OTHER);
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
        return saveFarm(name, status, null, null);
    }

    private Farm saveFarm(
            String name, FarmStatus status, String document, ProductionType productionType) {
        Farm farm = new Farm();
        farm.setName(name);
        farm.setDocument(document);
        farm.setProductionType(productionType);
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
