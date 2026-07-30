package br.com.gestaodireta.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.gestaodireta.shared.exception.BusinessException;
import br.com.gestaodireta.shared.exception.ResourceNotFoundException;
import br.com.gestaodireta.shared.exception.ValidationException;
import br.com.gestaodireta.shared.pagination.PaginationParams;
import br.com.gestaodireta.support.PostgresIntegrationTest;
import br.com.gestaodireta.user.dto.ResetUserPasswordRequest;
import br.com.gestaodireta.user.dto.UserCreateRequest;
import br.com.gestaodireta.user.dto.UserFilterRequest;
import br.com.gestaodireta.user.dto.UserResponse;
import br.com.gestaodireta.user.dto.UserStatusUpdateRequest;
import br.com.gestaodireta.user.dto.UserTypeUpdateRequest;
import br.com.gestaodireta.user.dto.UserUpdateRequest;
import br.com.gestaodireta.user.entity.User;
import br.com.gestaodireta.user.enumeration.UserStatus;
import br.com.gestaodireta.user.enumeration.UserType;
import br.com.gestaodireta.user.repository.UserRepository;
import br.com.gestaodireta.user.repository.UserContactRepository;
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
class UserServiceTest extends PostgresIntegrationTest {

    @Autowired private UserService userService;

    @Autowired private UserRepository userRepository;

    @Autowired private UserContactRepository userContactRepository;

    @Autowired private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        userContactRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void shouldCreateUserWithActiveStatusAndEncryptedPassword() {
        UserCreateRequest request =
                new UserCreateRequest(
                        "Maria Silva",
                        "maria@example.com",
                        "Strong1!",
                        "12345678900",
                        "+5524999999999",
                        UserType.USER);

        UserResponse response = userService.create(request);
        User savedUser = userRepository.findById(response.id()).orElseThrow();

        assertThat(response.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(savedUser.getPassword()).isNotEqualTo("Strong1!");
        assertThat(passwordEncoder.matches("Strong1!", savedUser.getPassword())).isTrue();
    }

    @Test
    void shouldCreateAdminUser() {
        UserResponse response = createUser("admin@example.com", UserType.ADMIN);

        assertThat(response.userType()).isEqualTo(UserType.ADMIN);
        assertThat(response.status()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void shouldRejectDuplicatedEmail() {
        userService.create(
                new UserCreateRequest(
                        "Maria Silva",
                        "maria@example.com",
                        "Strong1!",
                        null,
                        "+5524999999999",
                        UserType.USER));

        UserCreateRequest duplicatedRequest =
                new UserCreateRequest(
                        "Maria Souza",
                        "maria@example.com",
                        "Strong2!",
                        null,
                        "+5524988888888",
                        UserType.USER);

        assertThatThrownBy(() -> userService.create(duplicatedRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Email is already in use");
    }

    @Test
    void shouldListUsersWithoutFilter() {
        createUser("admin@example.com", UserType.ADMIN);
        createUser("user@example.com", UserType.USER);

        assertThat(userService.findAll(new PaginationParams()).content())
                .extracting(UserResponse::email)
                .containsExactly("admin@example.com", "user@example.com");
    }

    @Test
    void shouldFilterUsersBySearchIgnoringCaseAndBlankSearch() {
        createNamedUser("Maria Silva", "maria@example.com", UserType.USER);
        createNamedUser("Joao Souza", "joao@example.com", UserType.USER);

        assertThat(
                        userService
                                .findAll(
                                        new UserFilterRequest("  MARIA  ", null, null),
                                        new PaginationParams())
                                .content())
                .extracting(UserResponse::email)
                .containsExactly("maria@example.com");

        assertThat(
                        userService
                                .findAll(
                                        new UserFilterRequest("   ", null, null),
                                        new PaginationParams())
                                .content())
                .hasSize(2);
    }

    @Test
    void shouldFilterUsersByTypeStatusAndCombination() {
        UserResponse admin = createNamedUser("Admin", "admin@example.com", UserType.ADMIN);
        UserResponse activeUser = createNamedUser("Active", "active@example.com", UserType.USER);
        UserResponse blockedUser = createNamedUser("Blocked", "blocked@example.com", UserType.USER);
        userService.updateStatus(blockedUser.id(), new UserStatusUpdateRequest(UserStatus.BLOCKED));

        assertThat(
                        userService
                                .findAll(
                                        new UserFilterRequest(null, UserType.ADMIN, null),
                                        new PaginationParams())
                                .content())
                .extracting(UserResponse::id)
                .containsExactly(admin.id());
        assertThat(
                        userService
                                .findAll(
                                        new UserFilterRequest(null, null, UserStatus.ACTIVE),
                                        new PaginationParams())
                                .content())
                .extracting(UserResponse::id)
                .containsExactly(admin.id(), activeUser.id());
        assertThat(
                        userService
                                .findAll(
                                        new UserFilterRequest(
                                                null, UserType.USER, UserStatus.BLOCKED),
                                        new PaginationParams())
                                .content())
                .extracting(UserResponse::id)
                .containsExactly(blockedUser.id());
    }

    @Test
    void shouldFindUserById() {
        UserResponse createdUser = createUser("admin@example.com", UserType.ADMIN);

        UserResponse foundUser = userService.findById(createdUser.id());

        assertThat(foundUser.id()).isEqualTo(createdUser.id());
        assertThat(foundUser.email()).isEqualTo("admin@example.com");
    }

    @Test
    void shouldThrowResourceNotFoundWhenUserDoesNotExist() {
        assertThatThrownBy(() -> userService.findById(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("User not found");
    }

    @Test
    void shouldFindUserByExactEmailIgnoringCaseAndSpaces() {
        UserResponse createdUser = createUser("target@example.com", UserType.USER);

        UserResponse foundUser = userService.findByEmail("  TARGET@example.com  ");

        assertThat(foundUser.id()).isEqualTo(createdUser.id());
        assertThat(foundUser.email()).isEqualTo("target@example.com");
    }

    @Test
    void shouldThrowResourceNotFoundWhenEmailDoesNotExist() {
        assertThatThrownBy(() -> userService.findByEmail("missing@example.com"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("User not found");
    }

    @Test
    void shouldRejectBlankEmailSearch() {
        assertThatThrownBy(() -> userService.findByEmail("   "))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Email is required");
    }

    @Test
    void shouldRejectInvalidEmailSearch() {
        assertThatThrownBy(() -> userService.findByEmail("invalid"))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Email is invalid");
    }

    @Test
    void shouldUpdateOnlyNameAndDocumentForAuthenticatedUser() {
        UserResponse createdUser = createUser("user@example.com", UserType.USER);
        authenticateAs(createdUser.id(), "ROLE_USER");

        UserResponse response =
                userService.updateMe(new UserUpdateRequest("Updated Name", "98765432100"));
        User savedUser = userRepository.findById(createdUser.id()).orElseThrow();

        assertThat(response.name()).isEqualTo("Updated Name");
        assertThat(response.document()).isEqualTo("98765432100");
        assertThat(savedUser.getEmail()).isEqualTo("user@example.com");
        assertThat(savedUser.getUserType()).isEqualTo(UserType.USER);
        assertThat(savedUser.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void shouldUpdateUserStatus() {
        UserResponse createdUser = createUser("user@example.com", UserType.USER);

        UserResponse response =
                userService.updateStatus(
                        createdUser.id(), new UserStatusUpdateRequest(UserStatus.BLOCKED));

        assertThat(response.status()).isEqualTo(UserStatus.BLOCKED);
    }

    @Test
    void shouldUpdateUserType() {
        UserResponse createdUser = createUser("user@example.com", UserType.USER);

        UserResponse response =
                userService.updateType(createdUser.id(), new UserTypeUpdateRequest(UserType.ADMIN));

        assertThat(response.userType()).isEqualTo(UserType.ADMIN);
    }

    @Test
    void shouldUpdateUserAsAdmin() {
        UserResponse createdUser = createUser("user@example.com", UserType.USER);

        UserResponse response =
                userService.updateUser(
                        createdUser.id(), new UserUpdateRequest("Updated Name", "98765432100"));

        assertThat(response.name()).isEqualTo("Updated Name");
        assertThat(response.document()).isEqualTo("98765432100");
    }

    @Test
    void shouldTrimNameAndDocumentWhenUpdatingUser() {
        UserResponse createdUser = createUser("user@example.com", UserType.USER);

        UserResponse response =
                userService.updateUser(
                        createdUser.id(),
                        new UserUpdateRequest("  Updated Name  ", "  98765432100  "));

        assertThat(response.name()).isEqualTo("Updated Name");
        assertThat(response.document()).isEqualTo("98765432100");
    }

    @Test
    void shouldNormalizeBlankDocumentToNullWhenUpdatingUser() {
        UserResponse createdUser = createUser("user@example.com", UserType.USER);

        UserResponse response =
                userService.updateUser(
                        createdUser.id(), new UserUpdateRequest("Updated Name", "   "));

        assertThat(response.document()).isNull();
    }

    @Test
    void shouldThrowResourceNotFoundWhenUpdatingUserDoesNotExist() {
        assertThatThrownBy(() -> userService.updateUser(999L, new UserUpdateRequest("User", null)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("User not found");
    }

    @Test
    void shouldRejectBlankNameWhenUpdatingUser() {
        UserResponse createdUser = createUser("user@example.com", UserType.USER);

        assertThatThrownBy(
                        () ->
                                userService.updateUser(
                                        createdUser.id(), new UserUpdateRequest("   ", null)))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Name is required");
    }

    @Test
    void shouldNotUpdateProtectedFieldsWhenUpdatingUser() {
        UserResponse createdUser = createUser("user@example.com", UserType.USER);
        User originalUser = userRepository.findById(createdUser.id()).orElseThrow();
        String originalPassword = originalUser.getPassword();

        userService.updateUser(createdUser.id(), new UserUpdateRequest("Updated Name", "123"));
        User savedUser = userRepository.findById(createdUser.id()).orElseThrow();

        assertThat(savedUser.getEmail()).isEqualTo("user@example.com");
        assertThat(savedUser.getUserType()).isEqualTo(UserType.USER);
        assertThat(savedUser.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(savedUser.getPassword()).isEqualTo(originalPassword);
    }

    @Test
    void shouldResetPasswordForAnotherUserAsAdmin() {
        UserResponse admin = createUser("admin@example.com", UserType.ADMIN);
        UserResponse target = createUser("target@example.com", UserType.USER);
        authenticateAs(admin.id(), "ROLE_ADMIN");

        UserResponse response =
                userService.resetPassword(
                        target.id(), new ResetUserPasswordRequest("NewPassword@123"));
        User savedUser = userRepository.findById(target.id()).orElseThrow();

        assertThat(response.id()).isEqualTo(target.id());
        assertThat(passwordEncoder.matches("NewPassword@123", savedUser.getPassword())).isTrue();
        assertThat(savedUser.getPassword()).isNotEqualTo("NewPassword@123");
    }

    @Test
    void shouldNotExposePasswordWhenResettingPassword() {
        UserResponse admin = createUser("admin@example.com", UserType.ADMIN);
        UserResponse target = createUser("target@example.com", UserType.USER);
        authenticateAs(admin.id(), "ROLE_ADMIN");

        UserResponse response =
                userService.resetPassword(
                        target.id(), new ResetUserPasswordRequest("NewPassword@123"));

        assertThat(response).hasNoNullFieldsOrPropertiesExcept("document");
    }

    @Test
    void shouldThrowResourceNotFoundWhenResetPasswordUserDoesNotExist() {
        UserResponse admin = createUser("admin@example.com", UserType.ADMIN);
        authenticateAs(admin.id(), "ROLE_ADMIN");

        assertThatThrownBy(
                        () ->
                                userService.resetPassword(
                                        999L, new ResetUserPasswordRequest("NewPassword@123")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("User not found");
    }

    @Test
    void shouldRejectInvalidPasswordWhenResettingPassword() {
        UserResponse admin = createUser("admin@example.com", UserType.ADMIN);
        UserResponse target = createUser("target@example.com", UserType.USER);
        authenticateAs(admin.id(), "ROLE_ADMIN");

        assertThatThrownBy(
                        () ->
                                userService.resetPassword(
                                        target.id(), new ResetUserPasswordRequest("weak")))
                .isInstanceOf(ValidationException.class)
                .hasMessage(
                        "Password must have at least 8 characters, uppercase, lowercase, number and special character");
    }

    @Test
    void shouldRejectBlankPasswordWhenResettingPassword() {
        UserResponse admin = createUser("admin@example.com", UserType.ADMIN);
        UserResponse target = createUser("target@example.com", UserType.USER);
        authenticateAs(admin.id(), "ROLE_ADMIN");

        assertThatThrownBy(
                        () ->
                                userService.resetPassword(
                                        target.id(), new ResetUserPasswordRequest("   ")))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Password is required");
    }

    @Test
    void shouldRejectResettingOwnPassword() {
        UserResponse admin = createUser("admin@example.com", UserType.ADMIN);
        authenticateAs(admin.id(), "ROLE_ADMIN");

        assertThatThrownBy(
                        () ->
                                userService.resetPassword(
                                        admin.id(),
                                        new ResetUserPasswordRequest("NewPassword@123")))
                .isInstanceOf(BusinessException.class)
                .hasMessage(
                        "Use /auth/change-password to change the authenticated user's own password");
    }

    @Test
    void shouldNotUpdateProtectedFieldsWhenResettingPassword() {
        UserResponse admin = createUser("admin@example.com", UserType.ADMIN);
        UserResponse target = createUser("target@example.com", UserType.USER);
        authenticateAs(admin.id(), "ROLE_ADMIN");

        userService.resetPassword(target.id(), new ResetUserPasswordRequest("NewPassword@123"));
        User savedUser = userRepository.findById(target.id()).orElseThrow();

        assertThat(savedUser.getEmail()).isEqualTo("target@example.com");
        assertThat(savedUser.getUserType()).isEqualTo(UserType.USER);
        assertThat(savedUser.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    private UserResponse createUser(String email, UserType userType) {
        return createNamedUser("Test User", email, userType);
    }

    private UserResponse createNamedUser(String name, String email, UserType userType) {
        return userService.create(
                new UserCreateRequest(
                        name,
                        email,
                        "Strong1!",
                        null,
                        phoneNumberFor(email),
                        userType));
    }

    private String phoneNumberFor(String email) {
        int number = Math.floorMod(email.hashCode(), 100_000_000);

        return "+5524" + String.format("%08d", number);
    }

    private void authenticateAs(Long userId, String role) {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        String.valueOf(userId), null, List.of(new SimpleGrantedAuthority(role)));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
