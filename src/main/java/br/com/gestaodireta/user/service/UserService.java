package br.com.gestaodireta.user.service;

import br.com.gestaodireta.shared.exception.BusinessException;
import br.com.gestaodireta.shared.exception.ResourceNotFoundException;
import br.com.gestaodireta.shared.exception.ValidationException;
import br.com.gestaodireta.shared.pagination.PaginationParams;
import br.com.gestaodireta.shared.response.PageResponse;
import br.com.gestaodireta.shared.security.SecurityUtils;
import br.com.gestaodireta.user.dto.ResetUserPasswordRequest;
import br.com.gestaodireta.user.dto.UserCreateRequest;
import br.com.gestaodireta.user.dto.UserResponse;
import br.com.gestaodireta.user.dto.UserStatusUpdateRequest;
import br.com.gestaodireta.user.dto.UserTypeUpdateRequest;
import br.com.gestaodireta.user.dto.UserUpdateRequest;
import br.com.gestaodireta.user.entity.User;
import br.com.gestaodireta.user.enumeration.UserStatus;
import br.com.gestaodireta.user.mapper.UserMapper;
import br.com.gestaodireta.user.repository.UserRepository;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.data.domain.Page;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private static final Pattern PASSWORD_PATTERN =
            Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,}$");

    private final UserRepository userRepository;

    private final UserMapper userMapper;

    private final PasswordEncoder passwordEncoder;

    public UserService(
            UserRepository userRepository, UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserResponse create(UserCreateRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException("Email is already in use");
        }

        User user = new User();
        user.setName(request.name());
        user.setEmail(request.email());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setDocument(request.document());
        user.setUserType(request.userType());
        user.setStatus(UserStatus.ACTIVE);

        return userMapper.toResponse(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> findAll(PaginationParams paginationParams) {
        Page<UserResponse> users =
                userRepository.findAll(paginationParams.toPageable()).map(userMapper::toResponse);

        return PageResponse.from(users);
    }

    @Transactional(readOnly = true)
    public UserResponse findById(Long id) {
        return userMapper.toResponse(findEntityById(id));
    }

    @Transactional(readOnly = true)
    public UserResponse findByEmail(String email) {
        String normalizedEmail = normalizeEmail(email);

        return userRepository
                .findByEmailIgnoreCase(normalizedEmail)
                .map(userMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    @Transactional(readOnly = true)
    public UserResponse findMe() {
        return userMapper.toResponse(findEntityById(SecurityUtils.getAuthenticatedUserId()));
    }

    @Transactional
    public UserResponse updateMe(UserUpdateRequest request) {
        User user = findEntityById(SecurityUtils.getAuthenticatedUserId());
        updateBasicData(user, request);

        return userMapper.toResponse(userRepository.save(user));
    }

    @Transactional
    public UserResponse updateUser(Long id, UserUpdateRequest request) {
        User user = findEntityById(id);
        updateBasicData(user, request);

        return userMapper.toResponse(userRepository.save(user));
    }

    @Transactional
    public UserResponse resetPassword(Long id, ResetUserPasswordRequest request) {
        User user = findEntityById(id);

        if (id.equals(SecurityUtils.getAuthenticatedUserId())) {
            throw new BusinessException(
                    "Use /auth/change-password to change the authenticated user's own password");
        }

        String newPassword = normalizePassword(request.newPassword());
        user.setPassword(passwordEncoder.encode(newPassword));

        return userMapper.toResponse(userRepository.save(user));
    }

    @Transactional
    public UserResponse updateStatus(Long id, UserStatusUpdateRequest request) {
        User user = findEntityById(id);
        user.setStatus(request.status());

        return userMapper.toResponse(userRepository.save(user));
    }

    @Transactional
    public UserResponse updateType(Long id, UserTypeUpdateRequest request) {
        User user = findEntityById(id);
        user.setUserType(request.userType());

        return userMapper.toResponse(userRepository.save(user));
    }

    private void updateBasicData(User user, UserUpdateRequest request) {
        user.setName(normalizeName(request.name()));
        user.setDocument(normalizeDocument(request.document()));
    }

    private String normalizeName(String name) {
        if (name == null) {
            throw new ValidationException("Name is required");
        }

        String normalizedName = name.trim();

        if (normalizedName.isBlank()) {
            throw new ValidationException("Name is required");
        }

        return normalizedName;
    }

    private String normalizeDocument(String document) {
        if (document == null) {
            return null;
        }

        String normalizedDocument = document.trim();

        if (normalizedDocument.isBlank()) {
            return null;
        }

        return normalizedDocument;
    }

    private String normalizePassword(String password) {
        if (password == null || password.isBlank()) {
            throw new ValidationException("Password is required");
        }

        if (!PASSWORD_PATTERN.matcher(password).matches()) {
            throw new ValidationException(
                    "Password must have at least 8 characters, uppercase, lowercase, number and special character");
        }

        return password;
    }

    private User findEntityById(Long id) {
        return userRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private String normalizeEmail(String email) {
        if (email == null) {
            throw new ValidationException("Email is required");
        }

        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);

        if (normalizedEmail.isBlank()) {
            throw new ValidationException("Email is required");
        }

        if (!EMAIL_PATTERN.matcher(normalizedEmail).matches()) {
            throw new ValidationException("Email is invalid");
        }

        return normalizedEmail;
    }
}
