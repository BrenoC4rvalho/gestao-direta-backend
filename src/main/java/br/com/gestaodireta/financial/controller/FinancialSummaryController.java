package br.com.gestaodireta.financial.controller;

import br.com.gestaodireta.financial.dto.CashFlowResponse;
import br.com.gestaodireta.financial.dto.FinancialSummaryResponse;
import br.com.gestaodireta.financial.dto.UpcomingBillResponse;
import br.com.gestaodireta.financial.service.FinancialSummaryService;
import br.com.gestaodireta.shared.pagination.PaginationParams;
import br.com.gestaodireta.shared.response.PageResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/financial")
public class FinancialSummaryController {

    private final FinancialSummaryService financialSummaryService;

    public FinancialSummaryController(FinancialSummaryService financialSummaryService) {
        this.financialSummaryService = financialSummaryService;
    }

    @GetMapping("/summary")
    @PreAuthorize("@financialAccess.canViewFinancialData(#farmId)")
    public FinancialSummaryResponse summarize(@RequestParam Long farmId) {
        return financialSummaryService.summarize(farmId);
    }

    @GetMapping("/cash-flow")
    @PreAuthorize("@financialAccess.canViewFinancialDataOrMissingFarm(#farmId)")
    public CashFlowResponse getCashFlow(@RequestParam Long farmId, @RequestParam Integer year) {
        return financialSummaryService.getCashFlow(farmId, year);
    }

    @GetMapping("/upcoming-bills")
    @PreAuthorize("@financialAccess.canViewFinancialData(#farmId)")
    public PageResponse<UpcomingBillResponse> findUpcomingBills(
            @RequestParam Long farmId, @Valid @ModelAttribute PaginationParams paginationParams) {
        return financialSummaryService.findUpcomingBills(farmId, paginationParams);
    }
}
