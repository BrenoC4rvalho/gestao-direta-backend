package br.com.gestaodireta.financial.controller;

import br.com.gestaodireta.financial.dto.FinancialReportFilter;
import br.com.gestaodireta.financial.dto.FinancialReportResponse;
import br.com.gestaodireta.financial.dto.FinancialReportTransactionResponse;
import br.com.gestaodireta.financial.enumeration.FinancialReportBasis;
import br.com.gestaodireta.financial.enumeration.FinancialReportGranularity;
import br.com.gestaodireta.financial.service.FinancialReportService;
import br.com.gestaodireta.shared.response.PageResponse;
import java.time.LocalDate;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/financial/reports")
public class FinancialReportController {

    private final FinancialReportService financialReportService;

    public FinancialReportController(FinancialReportService financialReportService) {
        this.financialReportService = financialReportService;
    }

    @GetMapping
    @PreAuthorize("@financialAccess.canViewFinancialDataOrMissingFarm(#farmId)")
    public FinancialReportResponse getReport(
            @RequestParam Long farmId,
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate,
            @RequestParam FinancialReportBasis basis,
            @RequestParam(required = false) List<Long> harvestSeasonIds,
            @RequestParam(required = false) List<Long> categoryIds,
            @RequestParam(defaultValue = "MONTHLY") FinancialReportGranularity granularity) {
        return financialReportService.getReport(
                new FinancialReportFilter(
                        farmId,
                        startDate,
                        endDate,
                        basis,
                        harvestSeasonIds,
                        categoryIds,
                        granularity));
    }

    @GetMapping("/transactions")
    @PreAuthorize("@financialAccess.canViewFinancialDataOrMissingFarm(#farmId)")
    public PageResponse<FinancialReportTransactionResponse> findTransactions(
            @RequestParam Long farmId,
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate,
            @RequestParam FinancialReportBasis basis,
            @RequestParam(required = false) List<Long> harvestSeasonIds,
            @RequestParam(required = false) List<Long> categoryIds,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "referenceDate") String sort,
            @RequestParam(defaultValue = "ASC") String direction) {
        return financialReportService.findTransactions(
                new FinancialReportFilter(
                        farmId, startDate, endDate, basis, harvestSeasonIds, categoryIds),
                page,
                size,
                sort,
                direction);
    }
}
