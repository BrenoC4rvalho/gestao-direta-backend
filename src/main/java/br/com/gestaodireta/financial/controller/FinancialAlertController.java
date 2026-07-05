package br.com.gestaodireta.financial.controller;

import br.com.gestaodireta.financial.dto.FinancialAlertsResponse;
import br.com.gestaodireta.financial.service.FinancialAlertService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/financial")
public class FinancialAlertController {

    private final FinancialAlertService financialAlertService;

    public FinancialAlertController(FinancialAlertService financialAlertService) {
        this.financialAlertService = financialAlertService;
    }

    @GetMapping("/alerts")
    @PreAuthorize("@financialAccess.canViewFinancialData(#farmId)")
    public FinancialAlertsResponse getAlerts(@RequestParam Long farmId) {
        return financialAlertService.getAlerts(farmId);
    }
}
