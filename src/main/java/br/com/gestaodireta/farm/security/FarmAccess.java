package br.com.gestaodireta.farm.security;

import br.com.gestaodireta.farm.dto.FarmUserRequest;
import br.com.gestaodireta.farm.dto.FarmUserRoleUpdateRequest;
import br.com.gestaodireta.farm.enumeration.FarmStatus;
import br.com.gestaodireta.farm.enumeration.FarmUserRole;
import br.com.gestaodireta.farm.repository.FarmRepository;
import br.com.gestaodireta.farm.repository.FarmUserRepository;
import br.com.gestaodireta.shared.security.SecurityUtils;
import br.com.gestaodireta.user.entity.User;
import br.com.gestaodireta.user.enumeration.UserStatus;
import br.com.gestaodireta.user.enumeration.UserType;
import br.com.gestaodireta.user.repository.UserRepository;
import org.springframework.stereotype.Component;

@Component("farmAccess")
public class FarmAccess {

    private final FarmRepository farmRepository;

    private final FarmUserRepository farmUserRepository;

    private final UserRepository userRepository;

    public FarmAccess(
            FarmRepository farmRepository,
            FarmUserRepository farmUserRepository,
            UserRepository userRepository) {
        this.farmRepository = farmRepository;
        this.farmUserRepository = farmUserRepository;
        this.userRepository = userRepository;
    }

    public boolean canCreateFarm() {
        return SecurityUtils.isAdmin();
    }

    public boolean canViewFarm(Long farmId) {
        if (SecurityUtils.isAdmin()) {
            return true;
        }

        return isCurrentUserActive()
                && isFarmActive(farmId)
                && hasAnyActiveRole(farmId, SecurityUtils.getAuthenticatedUserId());
    }

    public boolean canManageFarm(Long farmId) {
        if (SecurityUtils.isAdmin()) {
            return true;
        }

        return isCurrentUserActive()
                && isFarmActive(farmId)
                && hasRole(farmId, SecurityUtils.getAuthenticatedUserId(), FarmUserRole.PRODUCER);
    }

    public boolean canChangeFarmStatus() {
        return SecurityUtils.isAdmin();
    }

    public boolean canManageFarmUsers(Long farmId) {
        return canManageFarm(farmId);
    }

    public boolean canCreateFarmUser(Long farmId, FarmUserRequest request) {
        if (SecurityUtils.isAdmin()) {
            return true;
        }

        if (!canManageFarm(farmId)) {
            return false;
        }

        return isAllowedProducerTarget(request.userId(), request.role());
    }

    public boolean canChangeFarmUserRole(
            Long farmId, Long targetUserId, FarmUserRoleUpdateRequest request) {
        if (SecurityUtils.isAdmin()) {
            return true;
        }

        if (!canManageFarm(farmId)) {
            return false;
        }

        if (FarmUserRole.PRODUCER.equals(request.role())) {
            return false;
        }

        return isTargetEmployeeOrAccountant(farmId, targetUserId);
    }

    public boolean canRemoveFarmUser(Long farmId, Long targetUserId) {
        if (SecurityUtils.isAdmin()) {
            return true;
        }

        if (!canManageFarm(farmId)) {
            return false;
        }

        return isTargetEmployeeOrAccountant(farmId, targetUserId);
    }

    private boolean isCurrentUserActive() {
        return userRepository
                .findById(SecurityUtils.getAuthenticatedUserId())
                .map(User::getStatus)
                .filter(UserStatus.ACTIVE::equals)
                .isPresent();
    }

    private boolean isFarmActive(Long farmId) {
        return farmRepository
                .findById(farmId)
                .map(farm -> FarmStatus.ACTIVE.equals(farm.getStatus()))
                .orElse(false);
    }

    private boolean hasAnyActiveRole(Long farmId, Long userId) {
        return farmUserRepository
                .findRoleByFarmIdAndUserId(farmId, userId)
                .filter(role -> !FarmUserRole.INACTIVE.equals(role))
                .isPresent();
    }

    private boolean hasRole(Long farmId, Long userId, FarmUserRole expectedRole) {
        return farmUserRepository
                .findRoleByFarmIdAndUserId(farmId, userId)
                .filter(expectedRole::equals)
                .isPresent();
    }

    private boolean isAllowedProducerTarget(Long targetUserId, FarmUserRole requestedRole) {
        if (!FarmUserRole.EMPLOYEE.equals(requestedRole)
                && !FarmUserRole.ACCOUNTANT.equals(requestedRole)) {
            return false;
        }

        return userRepository
                .findById(targetUserId)
                .filter(user -> UserType.USER.equals(user.getUserType()))
                .filter(user -> UserStatus.ACTIVE.equals(user.getStatus()))
                .isPresent();
    }

    private boolean isTargetEmployeeOrAccountant(Long farmId, Long targetUserId) {
        return farmUserRepository
                .findRoleByFarmIdAndUserId(farmId, targetUserId)
                .filter(
                        role ->
                                FarmUserRole.EMPLOYEE.equals(role)
                                        || FarmUserRole.ACCOUNTANT.equals(role))
                .isPresent();
    }
}
