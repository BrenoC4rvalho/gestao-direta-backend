package br.com.gestaodireta.auth.service;

import br.com.gestaodireta.auth.dto.AuthResponse;
import br.com.gestaodireta.auth.dto.AuthUserResponse;
import br.com.gestaodireta.auth.dto.ChangePasswordRequest;
import br.com.gestaodireta.auth.dto.LoginRequest;
import br.com.gestaodireta.auth.security.JwtService;
import br.com.gestaodireta.shared.exception.BusinessException;
import br.com.gestaodireta.shared.exception.UnauthorizedException;
import br.com.gestaodireta.shared.security.SecurityUtils;
import br.com.gestaodireta.user.entity.User;
import br.com.gestaodireta.user.enumeration.UserStatus;
import br.com.gestaodireta.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final String INVALID_CREDENTIALS_MESSAGE = "Invalid email or password";

    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    private final JwtService jwtService;

    public AuthService(
            UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional(readOnly = true)
    public LoginResult login(LoginRequest request) {
        User user =
                userRepository
                        .findByEmail(request.email())
                        .orElseThrow(() -> new UnauthorizedException(INVALID_CREDENTIALS_MESSAGE));

        validateActiveUser(user);
        validatePassword(request.password(), user.getPassword());

        return new LoginResult(toResponse(user), jwtService.generateToken(user));
    }

    @Transactional(readOnly = true)
    public AuthResponse getSession() {
        User user = findAuthenticatedUser();
        validateActiveUser(user);

        return toResponse(user);
    }

    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        User user = findAuthenticatedUser();
        validateActiveUser(user);
        validatePassword(request.currentPassword(), user.getPassword());

        if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
            throw new BusinessException("New password must be different from current password");
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }

    public long getTokenExpirationSeconds() {
        return jwtService.getExpirationSeconds();
    }

    private User findAuthenticatedUser() {
        Long userId = SecurityUtils.getAuthenticatedUserId();

        return userRepository
                .findById(userId)
                .orElseThrow(() -> new UnauthorizedException("Authentication is required"));
    }

    private void validateActiveUser(User user) {
        if (!UserStatus.ACTIVE.equals(user.getStatus())) {
            throw new UnauthorizedException(INVALID_CREDENTIALS_MESSAGE);
        }
    }

    private void validatePassword(String rawPassword, String encryptedPassword) {
        if (!passwordEncoder.matches(rawPassword, encryptedPassword)) {
            throw new UnauthorizedException(INVALID_CREDENTIALS_MESSAGE);
        }
    }

    private AuthResponse toResponse(User user) {
        return new AuthResponse(
                new AuthUserResponse(
                        user.getId(),
                        user.getName(),
                        user.getEmail(),
                        user.getDocument(),
                        user.getUserType(),
                        user.getStatus()));
    }

    public record LoginResult(AuthResponse response, String token) {}
}
