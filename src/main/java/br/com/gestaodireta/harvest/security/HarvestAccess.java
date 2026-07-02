package br.com.gestaodireta.harvest.security;

import br.com.gestaodireta.farm.enumeration.FarmStatus;
import br.com.gestaodireta.farm.enumeration.FarmUserRole;
import br.com.gestaodireta.farm.repository.FarmRepository;
import br.com.gestaodireta.harvest.dto.HarvestSeasonRequest;
import br.com.gestaodireta.harvest.enumeration.HarvestSeasonStatus;
import br.com.gestaodireta.harvest.repository.HarvestFarmUserAccessRepository;
import br.com.gestaodireta.harvest.repository.HarvestSeasonRepository;
import br.com.gestaodireta.shared.security.SecurityUtils;
import br.com.gestaodireta.user.entity.User;
import br.com.gestaodireta.user.enumeration.UserStatus;
import br.com.gestaodireta.user.repository.UserRepository;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component("harvestAccess")
public class HarvestAccess {

    private final FarmRepository farmRepository;

    private final HarvestFarmUserAccessRepository harvestFarmUserAccessRepository;

    private final HarvestSeasonRepository harvestSeasonRepository;

    private final UserRepository userRepository;

    public HarvestAccess(
            FarmRepository farmRepository,
            HarvestFarmUserAccessRepository harvestFarmUserAccessRepository,
            HarvestSeasonRepository harvestSeasonRepository,
            UserRepository userRepository) {
        this.farmRepository = farmRepository;
        this.harvestFarmUserAccessRepository = harvestFarmUserAccessRepository;
        this.harvestSeasonRepository = harvestSeasonRepository;
        this.userRepository = userRepository;
    }

    public boolean canManageProductionActivities() {
        return SecurityUtils.isAdmin();
    }

    public boolean canViewActiveProductionActivities() {
        if (SecurityUtils.isAdmin()) {
            return true;
        }

        return isCurrentUserActive()
                && harvestFarmUserAccessRepository.existsActiveFarmAccessByUserId(
                        SecurityUtils.getAuthenticatedUserId());
    }

    public boolean canCreateSeason(HarvestSeasonRequest request) {
        return canManageActiveFarm(request.farmId());
    }

    public boolean canViewSeasons(Long farmId) {
        return canViewFarmHarvestData(farmId);
    }

    public boolean canViewSeason(Long seasonId) {
        return harvestSeasonRepository
                .findAccessById(seasonId)
                .filter(access -> canViewFarmHarvestData(access.getFarmId()))
                .isPresent();
    }

    public boolean canManageSeason(Long seasonId) {
        return harvestSeasonRepository
                .findAccessById(seasonId)
                .filter(
                        access ->
                                SecurityUtils.isAdmin()
                                        || !HarvestSeasonStatus.INACTIVE.equals(access.getStatus()))
                .filter(access -> canManageActiveFarm(access.getFarmId()))
                .isPresent();
    }

    public boolean canChangeSeasonStatus(Long seasonId) {
        return harvestSeasonRepository
                .findAccessById(seasonId)
                .filter(access -> canManageActiveFarm(access.getFarmId()))
                .isPresent();
    }

    private boolean canViewFarmHarvestData(Long farmId) {
        if (SecurityUtils.isAdmin()) {
            return true;
        }

        Optional<FarmUserRole> role = currentUserRole(farmId);

        return isCurrentUserActive()
                && isFarmActive(farmId)
                && role.filter(this::canViewByRole).isPresent();
    }

    private boolean canManageActiveFarm(Long farmId) {
        if (SecurityUtils.isAdmin()) {
            return isFarmActive(farmId);
        }

        Optional<FarmUserRole> role = currentUserRole(farmId);

        return isCurrentUserActive()
                && isFarmActive(farmId)
                && role.filter(FarmUserRole.PRODUCER::equals).isPresent();
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

    private Optional<FarmUserRole> currentUserRole(Long farmId) {
        return harvestFarmUserAccessRepository.findRoleByFarmIdAndUserId(
                farmId, SecurityUtils.getAuthenticatedUserId());
    }

    private boolean canViewByRole(FarmUserRole role) {
        return FarmUserRole.PRODUCER.equals(role)
                || FarmUserRole.EMPLOYEE.equals(role)
                || FarmUserRole.ACCOUNTANT.equals(role);
    }
}
