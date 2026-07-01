package br.com.gestaodireta.financial.controller;

import br.com.gestaodireta.financial.service.FinancialUserOptionService;
import br.com.gestaodireta.user.dto.UserOptionResponse;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/farms/{farmId}/users/options")
public class FinancialUserOptionController {

    private final FinancialUserOptionService financialUserOptionService;

    public FinancialUserOptionController(FinancialUserOptionService financialUserOptionService) {
        this.financialUserOptionService = financialUserOptionService;
    }

    @GetMapping
    @PreAuthorize("@financialAccess.canViewFinancialDataOrMissingFarm(#farmId)")
    public List<UserOptionResponse> findTransactionFilterOptions(@PathVariable Long farmId) {
        return financialUserOptionService.findTransactionFilterOptions(farmId);
    }
}
