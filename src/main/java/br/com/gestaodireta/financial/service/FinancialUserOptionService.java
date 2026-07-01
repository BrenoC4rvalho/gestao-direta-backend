package br.com.gestaodireta.financial.service;

import br.com.gestaodireta.farm.enumeration.FarmUserRole;
import br.com.gestaodireta.farm.service.FarmService;
import br.com.gestaodireta.financial.enumeration.FinancialRecordStatus;
import br.com.gestaodireta.financial.repository.FinancialUserOptionRepository;
import br.com.gestaodireta.user.dto.UserOptionResponse;
import br.com.gestaodireta.user.enumeration.UserStatus;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FinancialUserOptionService {

    private final FinancialUserOptionRepository financialUserOptionRepository;

    private final FarmService farmService;

    public FinancialUserOptionService(
            FinancialUserOptionRepository financialUserOptionRepository, FarmService farmService) {
        this.financialUserOptionRepository = financialUserOptionRepository;
        this.farmService = farmService;
    }

    @Transactional(readOnly = true)
    public List<UserOptionResponse> findTransactionFilterOptions(Long farmId) {
        farmService.findEntityById(farmId);

        return financialUserOptionRepository.findUserOptionsForTransactionFilter(
                farmId, FarmUserRole.INACTIVE, UserStatus.ACTIVE, FinancialRecordStatus.ACTIVE);
    }
}
