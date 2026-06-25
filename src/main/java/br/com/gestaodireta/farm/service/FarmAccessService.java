package br.com.gestaodireta.farm.service;

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
import br.com.gestaodireta.shared.security.SecurityUtils;
import br.com.gestaodireta.user.entity.User;
import br.com.gestaodireta.user.enumeration.UserStatus;
import br.com.gestaodireta.user.enumeration.UserType;
import br.com.gestaodireta.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FarmAccessService {

    private final FarmRepository farmRepository;

    private final FarmUserRepository farmUserRepository;

    private final UserRepository userRepository;

    public FarmAccessService(
            FarmRepository farmRepository,
            FarmUserRepository farmUserRepository,
            UserRepository userRepository) {
        this.farmRepository = farmRepository;
        this.farmUserRepository = farmUserRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public FarmAccessResponse getAccess(Long farmId) {
        Farm farm =
                farmRepository
                        .findById(farmId)
                        .orElseThrow(() -> new ResourceNotFoundException("Farm not found"));
        User user = findActiveAuthenticatedUser();

        if (UserType.ADMIN.equals(user.getUserType())) {
            return toResponse(farm, user, null, adminPermissions());
        }

        ensureFarmIsActive(farm);

        FarmUser farmUser =
                farmUserRepository
                        .findByFarmIdAndUserId(farmId, user.getId())
                        .filter(link -> !FarmUserRole.INACTIVE.equals(link.getRole()))
                        .orElseThrow(() -> new ForbiddenException("Access denied"));

        return toResponse(farm, user, farmUser.getRole(), permissionsFor(farmUser.getRole()));
    }

    private User findActiveAuthenticatedUser() {
        return userRepository
                .findById(SecurityUtils.getAuthenticatedUserId())
                .filter(user -> UserStatus.ACTIVE.equals(user.getStatus()))
                .orElseThrow(() -> new ForbiddenException("Access denied"));
    }

    private void ensureFarmIsActive(Farm farm) {
        if (!FarmStatus.ACTIVE.equals(farm.getStatus())) {
            throw new ForbiddenException("Access denied");
        }
    }

    private FarmAccessResponse toResponse(
            Farm farm, User user, FarmUserRole role, FarmAccessPermissions permissions) {
        return new FarmAccessResponse(
                farm.getId(), farm.getName(), user.getId(), user.getUserType(), role, permissions);
    }

    private FarmAccessPermissions permissionsFor(FarmUserRole role) {
        return switch (role) {
            case PRODUCER -> producerPermissions();
            case EMPLOYEE -> employeePermissions();
            case ACCOUNTANT -> accountantPermissions();
            case INACTIVE -> throw new ForbiddenException("Access denied");
        };
    }

    private FarmAccessPermissions adminPermissions() {
        return new FarmAccessPermissions(true, true, true, true, true, true, true, true, true);
    }

    private FarmAccessPermissions producerPermissions() {
        return new FarmAccessPermissions(true, true, false, true, true, true, true, false, false);
    }

    private FarmAccessPermissions employeePermissions() {
        return new FarmAccessPermissions(
                true, false, false, false, true, true, false, false, false);
    }

    private FarmAccessPermissions accountantPermissions() {
        return new FarmAccessPermissions(
                true, false, false, false, true, false, false, false, false);
    }
}
