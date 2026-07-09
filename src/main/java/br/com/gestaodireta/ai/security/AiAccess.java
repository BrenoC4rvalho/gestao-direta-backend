package br.com.gestaodireta.ai.security;

import br.com.gestaodireta.ai.service.dto.ParseTransactionTextRequest;
import br.com.gestaodireta.farm.enumeration.FarmStatus;
import br.com.gestaodireta.farm.enumeration.FarmUserRole;
import br.com.gestaodireta.farm.repository.FarmRepository;
import br.com.gestaodireta.farm.repository.FarmUserRepository;
import br.com.gestaodireta.shared.security.SecurityUtils;
import br.com.gestaodireta.user.entity.User;
import br.com.gestaodireta.user.enumeration.UserStatus;
import br.com.gestaodireta.user.repository.UserRepository;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component("aiAccess")
public class AiAccess {

    private final FarmRepository farmRepository;

    private final FarmUserRepository farmUserRepository;

    private final UserRepository userRepository;

    public AiAccess(
            FarmRepository farmRepository,
            FarmUserRepository farmUserRepository,
            UserRepository userRepository) {
        this.farmRepository = farmRepository;
        this.farmUserRepository = farmUserRepository;
        this.userRepository = userRepository;
    }

    public boolean canParseTransactionText(ParseTransactionTextRequest request) {
        if (request == null || request.farmId() == null) {
            return false;
        }

        if (SecurityUtils.isAdmin()) {
            return isCurrentUserActive() && isFarmActive(request.farmId());
        }

        Optional<FarmUserRole> role = currentUserRole(request.farmId());

        return isCurrentUserActive()
                && isFarmActive(request.farmId())
                && role.filter(this::canUseAiByRole).isPresent();
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
        return farmUserRepository.findRoleByFarmIdAndUserId(
                farmId, SecurityUtils.getAuthenticatedUserId());
    }

    private boolean canUseAiByRole(FarmUserRole role) {
        return FarmUserRole.PRODUCER.equals(role) || FarmUserRole.EMPLOYEE.equals(role);
    }
}
