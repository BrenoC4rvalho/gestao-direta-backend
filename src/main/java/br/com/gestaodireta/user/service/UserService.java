package br.com.gestaodireta.user.service;

import br.com.gestaodireta.shared.exception.BusinessException;
import br.com.gestaodireta.shared.exception.ResourceNotFoundException;
import br.com.gestaodireta.shared.exception.ValidationException;
import br.com.gestaodireta.shared.pagination.PaginationParams;
import br.com.gestaodireta.shared.response.PageResponse;
import br.com.gestaodireta.shared.security.SecurityUtils;
import br.com.gestaodireta.user.dto.ResetUserPasswordRequest;
import br.com.gestaodireta.user.dto.UserCreateRequest;
import br.com.gestaodireta.user.dto.UserFilterRequest;
import br.com.gestaodireta.user.dto.UserResponse;
import br.com.gestaodireta.user.dto.UserStatusUpdateRequest;
import br.com.gestaodireta.user.dto.UserTypeUpdateRequest;
import br.com.gestaodireta.user.dto.UserUpdateRequest;
import br.com.gestaodireta.user.entity.User;
import br.com.gestaodireta.user.entity.UserContact;
import br.com.gestaodireta.user.enumeration.PhoneVerificationStatus;
import br.com.gestaodireta.user.enumeration.PreferredMessagingChannel;
import br.com.gestaodireta.user.enumeration.UserContactStatus;
import br.com.gestaodireta.user.enumeration.UserStatus;
import br.com.gestaodireta.user.mapper.UserMapper;
import br.com.gestaodireta.user.repository.UserContactRepository;
import br.com.gestaodireta.user.repository.UserRepository;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
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

    private final UserContactRepository userContactRepository;

    private final UserContactService userContactService;

    public UserService(
            UserRepository userRepository,
            UserMapper userMapper,
            PasswordEncoder passwordEncoder,
            UserContactRepository userContactRepository,
            UserContactService userContactService) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.userContactRepository = userContactRepository;
        this.userContactService = userContactService;
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

        User savedUser = userRepository.save(user);
        UserContact contact = new UserContact();
        contact.setUser(savedUser);
        contact.setPhoneNumber(userContactService.normalize(request.phoneNumber()));
        contact.setPhoneVerificationStatus(PhoneVerificationStatus.PENDING);
        contact.setPreferredChannel(PreferredMessagingChannel.NONE);
        contact.setStatus(UserContactStatus.PENDING);
        userContactRepository.save(contact);

        return userMapper.toResponse(savedUser);
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> findAll(PaginationParams paginationParams) {
        return findAll(new UserFilterRequest(null, null, null), paginationParams);
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> findAll(
            UserFilterRequest filterRequest, PaginationParams paginationParams) {
        UserFilterRequest normalizedFilter = normalizeFilter(filterRequest);
        Page<UserResponse> users =
                userRepository
                        .findAllFiltered(
                                normalizedFilter.search(),
                                normalizedFilter.userType(),
                                hasStatuses(normalizedFilter),
                                statusesOrPlaceholder(normalizedFilter),
                                paginationParams.toPageable())
                        .map(userMapper::toResponse);

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
        user.incrementCredentialsVersion();

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

    private UserFilterRequest normalizeFilter(UserFilterRequest filterRequest) {
        if (filterRequest == null) {
            return new UserFilterRequest(null, null, null);
        }

        return new UserFilterRequest(
                normalizeNullableLowercaseText(filterRequest.search()),
                filterRequest.userType(),
                filterRequest.status(),
                effectiveStatuses(filterRequest.status(), filterRequest.statuses()));
    }

    private boolean hasStatuses(UserFilterRequest filterRequest) {
        return filterRequest.statuses() != null;
    }

    private List<UserStatus> statusesOrPlaceholder(UserFilterRequest filterRequest) {
        if (hasStatuses(filterRequest)) {
            return filterRequest.statuses();
        }

        return List.of(UserStatus.ACTIVE);
    }

    private List<UserStatus> effectiveStatuses(UserStatus status, List<UserStatus> statuses) {
        List<UserStatus> normalizedStatuses = normalizeList(statuses);

        if (normalizedStatuses != null) {
            return normalizedStatuses;
        }

        if (status == null) {
            return null;
        }

        return List.of(status);
    }

    private <T> List<T> normalizeList(List<T> values) {
        if (values == null) {
            return null;
        }

        List<T> normalizedValues = values.stream().filter(Objects::nonNull).distinct().toList();

        if (normalizedValues.isEmpty()) {
            return null;
        }

        return normalizedValues;
    }

    private String normalizeNullableLowercaseText(String value) {
        if (value == null) {
            return null;
        }

        String normalizedValue = value.trim().toLowerCase(Locale.ROOT);

        if (normalizedValue.isBlank()) {
            return null;
        }

        return normalizedValue;
    }
}
