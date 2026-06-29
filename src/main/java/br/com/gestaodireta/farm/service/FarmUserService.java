package br.com.gestaodireta.farm.service;

import br.com.gestaodireta.farm.dto.FarmUserFilterRequest;
import br.com.gestaodireta.farm.dto.FarmUserRequest;
import br.com.gestaodireta.farm.dto.FarmUserResponse;
import br.com.gestaodireta.farm.dto.FarmUserRoleUpdateRequest;
import br.com.gestaodireta.farm.entity.Farm;
import br.com.gestaodireta.farm.entity.FarmUser;
import br.com.gestaodireta.farm.enumeration.FarmStatus;
import br.com.gestaodireta.farm.enumeration.FarmUserRole;
import br.com.gestaodireta.farm.mapper.FarmUserMapper;
import br.com.gestaodireta.farm.repository.FarmRepository;
import br.com.gestaodireta.farm.repository.FarmUserRepository;
import br.com.gestaodireta.shared.exception.BusinessException;
import br.com.gestaodireta.shared.exception.ResourceNotFoundException;
import br.com.gestaodireta.shared.pagination.PaginationParams;
import br.com.gestaodireta.shared.response.PageResponse;
import br.com.gestaodireta.user.entity.User;
import br.com.gestaodireta.user.enumeration.UserType;
import br.com.gestaodireta.user.repository.UserRepository;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FarmUserService {

    private final FarmRepository farmRepository;

    private final FarmUserRepository farmUserRepository;

    private final UserRepository userRepository;

    private final FarmUserMapper farmUserMapper;

    public FarmUserService(
            FarmRepository farmRepository,
            FarmUserRepository farmUserRepository,
            UserRepository userRepository,
            FarmUserMapper farmUserMapper) {
        this.farmRepository = farmRepository;
        this.farmUserRepository = farmUserRepository;
        this.userRepository = userRepository;
        this.farmUserMapper = farmUserMapper;
    }

    @Transactional
    public FarmUserResponse create(Long farmId, FarmUserRequest request) {
        Farm farm = findFarmById(farmId);
        User user = findUserById(request.userId());

        ensureFarmIsActive(farm);
        ensureUserCanBeLinked(user);
        ensureFarmUserDoesNotExist(farmId, request.userId());

        FarmUser farmUser = new FarmUser();
        farmUser.setFarm(farm);
        farmUser.setUser(user);
        farmUser.setRole(request.role());

        return farmUserMapper.toResponse(farmUserRepository.save(farmUser));
    }

    @Transactional(readOnly = true)
    public PageResponse<FarmUserResponse> findByFarmId(
            Long farmId, FarmUserFilterRequest filterRequest, PaginationParams paginationParams) {
        findFarmById(farmId);
        FarmUserFilterRequest normalizedFilter = normalizeFilter(filterRequest);
        Page<FarmUserResponse> farmUsers =
                farmUserRepository
                        .findByFarmFiltered(
                                farmId,
                                normalizedFilter.search(),
                                hasRoles(normalizedFilter),
                                rolesOrPlaceholder(normalizedFilter),
                                toFarmUserPageable(paginationParams.toPageable()))
                        .map(farmUserMapper::toResponse);

        return PageResponse.from(farmUsers);
    }

    @Transactional
    public FarmUserResponse updateRole(
            Long farmId, Long userId, FarmUserRoleUpdateRequest request) {
        FarmUser farmUser = findFarmUser(farmId, userId);
        ensureCanChangeProducerRole(farmUser, request.role());
        farmUser.setRole(request.role());

        return farmUserMapper.toResponse(farmUserRepository.save(farmUser));
    }

    @Transactional
    public void inactivate(Long farmId, Long userId) {
        FarmUser farmUser = findFarmUser(farmId, userId);
        ensureCanChangeProducerRole(farmUser, FarmUserRole.INACTIVE);
        farmUser.setRole(FarmUserRole.INACTIVE);
        farmUserRepository.save(farmUser);
    }

    private Farm findFarmById(Long farmId) {
        return farmRepository
                .findById(farmId)
                .orElseThrow(() -> new ResourceNotFoundException("Farm not found"));
    }

    private User findUserById(Long userId) {
        return userRepository
                .findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private FarmUser findFarmUser(Long farmId, Long userId) {
        return farmUserRepository
                .findByFarmIdAndUserId(farmId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Farm user not found"));
    }

    private void ensureFarmIsActive(Farm farm) {
        if (!FarmStatus.ACTIVE.equals(farm.getStatus())) {
            throw new BusinessException("Inactive farm cannot receive user links");
        }
    }

    private void ensureFarmUserDoesNotExist(Long farmId, Long userId) {
        if (farmUserRepository.existsByFarmIdAndUserId(farmId, userId)) {
            throw new BusinessException("User is already linked to this farm");
        }
    }

    private void ensureUserCanBeLinked(User user) {
        if (!UserType.USER.equals(user.getUserType())) {
            throw new BusinessException("Only USER can be linked to farm");
        }
    }

    private void ensureCanChangeProducerRole(FarmUser farmUser, FarmUserRole newRole) {
        if (!FarmUserRole.PRODUCER.equals(farmUser.getRole())
                || FarmUserRole.PRODUCER.equals(newRole)) {
            return;
        }

        long activeProducerCount =
                farmUserRepository.countByFarmIdAndRole(
                        farmUser.getFarm().getId(), FarmUserRole.PRODUCER);

        if (activeProducerCount <= 1) {
            throw new BusinessException("Farm must have at least one active producer");
        }
    }

    private FarmUserFilterRequest normalizeFilter(FarmUserFilterRequest filterRequest) {
        if (filterRequest == null) {
            return new FarmUserFilterRequest(null, null);
        }

        return new FarmUserFilterRequest(
                normalizeNullableLowercaseText(filterRequest.search()),
                filterRequest.role(),
                effectiveRoles(filterRequest.role(), filterRequest.roles()));
    }

    private boolean hasRoles(FarmUserFilterRequest filterRequest) {
        return filterRequest.roles() != null;
    }

    private List<FarmUserRole> rolesOrPlaceholder(FarmUserFilterRequest filterRequest) {
        if (hasRoles(filterRequest)) {
            return filterRequest.roles();
        }

        return List.of(FarmUserRole.INACTIVE);
    }

    private List<FarmUserRole> effectiveRoles(FarmUserRole role, List<FarmUserRole> roles) {
        List<FarmUserRole> normalizedRoles = normalizeList(roles);

        if (normalizedRoles != null) {
            return normalizedRoles;
        }

        if (role == null) {
            return null;
        }

        return List.of(role);
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

    private Pageable toFarmUserPageable(Pageable pageable) {
        Sort translatedSort =
                Sort.by(pageable.getSort().stream().map(this::toFarmUserSortOrder).toList());

        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), translatedSort);
    }

    private Sort.Order toFarmUserSortOrder(Sort.Order order) {
        return order.withProperty(toFarmUserSortProperty(order.getProperty()));
    }

    private String toFarmUserSortProperty(String property) {
        return switch (property) {
            case "userName" -> "user.name";
            case "userEmail" -> "user.email";
            default -> property;
        };
    }
}
