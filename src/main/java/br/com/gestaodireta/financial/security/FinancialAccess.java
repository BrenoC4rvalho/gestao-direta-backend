package br.com.gestaodireta.financial.security;

import br.com.gestaodireta.farm.enumeration.FarmStatus;
import br.com.gestaodireta.farm.enumeration.FarmUserRole;
import br.com.gestaodireta.farm.repository.FarmRepository;
import br.com.gestaodireta.farm.repository.FarmUserRepository;
import br.com.gestaodireta.financial.dto.FinancialCategoryRequest;
import br.com.gestaodireta.financial.dto.FinancialTransactionRequest;
import br.com.gestaodireta.financial.repository.FinancialCategoryRepository;
import br.com.gestaodireta.financial.repository.FinancialTransactionRepository;
import br.com.gestaodireta.shared.security.SecurityUtils;
import br.com.gestaodireta.user.entity.User;
import br.com.gestaodireta.user.enumeration.UserStatus;
import br.com.gestaodireta.user.repository.UserRepository;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component("financialAccess")
public class FinancialAccess {

    private final FarmRepository farmRepository;

    private final FarmUserRepository farmUserRepository;

    private final UserRepository userRepository;

    private final FinancialTransactionRepository financialTransactionRepository;

    private final FinancialCategoryRepository financialCategoryRepository;

    public FinancialAccess(
            FarmRepository farmRepository,
            FarmUserRepository farmUserRepository,
            UserRepository userRepository,
            FinancialTransactionRepository financialTransactionRepository,
            FinancialCategoryRepository financialCategoryRepository) {
        this.farmRepository = farmRepository;
        this.farmUserRepository = farmUserRepository;
        this.userRepository = userRepository;
        this.financialTransactionRepository = financialTransactionRepository;
        this.financialCategoryRepository = financialCategoryRepository;
    }

    public boolean canCreateTransaction(FinancialTransactionRequest request) {
        return canManageFinancialData(request.farmId());
    }

    public boolean canViewFinancialData(Long farmId) {
        if (SecurityUtils.isAdmin()) {
            return true;
        }

        Optional<FarmUserRole> role = currentUserRole(farmId);

        return isCurrentUserActive()
                && isFarmActive(farmId)
                && role.filter(this::canViewByRole).isPresent();
    }

    public boolean canManageFinancialData(Long farmId) {
        if (SecurityUtils.isAdmin()) {
            return true;
        }

        Optional<FarmUserRole> role = currentUserRole(farmId);

        return isCurrentUserActive()
                && isFarmActive(farmId)
                && role.filter(this::canManageByRole).isPresent();
    }

    public boolean canViewTransaction(Long transactionId) {
        return financialTransactionRepository
                .findFarmIdById(transactionId)
                .filter(this::canViewFinancialData)
                .isPresent();
    }

    public boolean canUpdateTransaction(Long transactionId) {
        return financialTransactionRepository
                .findFarmIdById(transactionId)
                .filter(this::canManageFinancialData)
                .isPresent();
    }

    public boolean canDeleteTransaction(Long transactionId) {
        return canUpdateTransaction(transactionId);
    }

    public boolean canPayTransaction(Long transactionId) {
        return canUpdateTransaction(transactionId);
    }

    public boolean canCancelTransaction(Long transactionId) {
        return canUpdateTransaction(transactionId);
    }

    public boolean canCreateCategory(FinancialCategoryRequest request) {
        if (request.isDefault()) {
            return SecurityUtils.isAdmin();
        }

        if (request.farmId() == null) {
            return false;
        }

        if (SecurityUtils.isAdmin()) {
            return true;
        }

        Optional<FarmUserRole> role = currentUserRole(request.farmId());

        return isCurrentUserActive()
                && isFarmActive(request.farmId())
                && role.filter(FarmUserRole.PRODUCER::equals).isPresent();
    }

    public boolean canManageCategoryByCategoryId(Long categoryId) {
        if (SecurityUtils.isAdmin()) {
            return true;
        }

        Optional<Long> farmId = financialCategoryRepository.findFarmIdById(categoryId);

        if (farmId.isEmpty()) {
            return false;
        }

        return isCurrentUserActive()
                && isFarmActive(farmId.get())
                && currentUserRole(farmId.get()).filter(FarmUserRole.PRODUCER::equals).isPresent();
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

    private boolean canViewByRole(FarmUserRole role) {
        return FarmUserRole.PRODUCER.equals(role)
                || FarmUserRole.EMPLOYEE.equals(role)
                || FarmUserRole.ACCOUNTANT.equals(role);
    }

    private boolean canManageByRole(FarmUserRole role) {
        return FarmUserRole.PRODUCER.equals(role) || FarmUserRole.EMPLOYEE.equals(role);
    }
}
