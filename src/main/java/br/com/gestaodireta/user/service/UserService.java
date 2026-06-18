package br.com.gestaodireta.user.service;

import br.com.gestaodireta.shared.exception.BusinessException;
import br.com.gestaodireta.shared.exception.ResourceNotFoundException;
import br.com.gestaodireta.shared.pagination.PaginationParams;
import br.com.gestaodireta.shared.response.PageResponse;
import br.com.gestaodireta.shared.security.SecurityUtils;
import br.com.gestaodireta.user.dto.UserCreateRequest;
import br.com.gestaodireta.user.dto.UserResponse;
import br.com.gestaodireta.user.dto.UserStatusUpdateRequest;
import br.com.gestaodireta.user.dto.UserTypeUpdateRequest;
import br.com.gestaodireta.user.dto.UserUpdateRequest;
import br.com.gestaodireta.user.entity.User;
import br.com.gestaodireta.user.enumeration.UserStatus;
import br.com.gestaodireta.user.mapper.UserMapper;
import br.com.gestaodireta.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

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
    public UserResponse findMe() {
        return userMapper.toResponse(findEntityById(SecurityUtils.getAuthenticatedUserId()));
    }

    @Transactional
    public UserResponse updateMe(UserUpdateRequest request) {
        User user = findEntityById(SecurityUtils.getAuthenticatedUserId());
        user.setName(request.name());
        user.setDocument(request.document());

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

    private User findEntityById(Long id) {
        return userRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
