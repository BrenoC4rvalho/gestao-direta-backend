package br.com.gestaodireta.financial.controller;

import br.com.gestaodireta.financial.dto.FinancialAgendaFilter;
import br.com.gestaodireta.financial.dto.FinancialAgendaItemResponse;
import br.com.gestaodireta.financial.dto.FinancialAgendaSummaryResponse;
import br.com.gestaodireta.financial.enumeration.FinancialAgendaStatusFilter;
import br.com.gestaodireta.financial.enumeration.FinancialAgendaTypeFilter;
import br.com.gestaodireta.financial.service.FinancialAgendaService;
import br.com.gestaodireta.shared.pagination.PaginationParams;
import br.com.gestaodireta.shared.response.PageResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/financial/agenda")
public class FinancialAgendaController {

    private final FinancialAgendaService financialAgendaService;

    public FinancialAgendaController(FinancialAgendaService financialAgendaService) {
        this.financialAgendaService = financialAgendaService;
    }

    @GetMapping("/summary")
    @PreAuthorize("@financialAccess.canViewFinancialDataOrMissingFarm(#farmId)")
    public FinancialAgendaSummaryResponse summarize(
            @RequestParam Long farmId,
            @RequestParam(required = false) FinancialAgendaStatusFilter status,
            @RequestParam(required = false) FinancialAgendaTypeFilter type,
            @RequestParam(required = false) Integer periodDays,
            @RequestParam(required = false) List<Long> harvestSeasonIds) {
        return financialAgendaService.summarize(
                new FinancialAgendaFilter(
                        farmId, status, type, periodDays, harvestSeasonIds, null));
    }

    @GetMapping
    @PreAuthorize("@financialAccess.canViewFinancialDataOrMissingFarm(#farmId)")
    public PageResponse<FinancialAgendaItemResponse> findAll(
            @RequestParam Long farmId,
            @RequestParam(required = false) FinancialAgendaStatusFilter status,
            @RequestParam(required = false) FinancialAgendaTypeFilter type,
            @RequestParam(required = false) Integer periodDays,
            @RequestParam(required = false) List<Long> harvestSeasonIds,
            @Valid @ModelAttribute PaginationParams paginationParams) {
        return financialAgendaService.findAll(
                new FinancialAgendaFilter(
                        farmId,
                        status,
                        type,
                        periodDays,
                        harvestSeasonIds,
                        toAgendaPageable(paginationParams)));
    }

    private PageRequest toAgendaPageable(PaginationParams paginationParams) {
        return PageRequest.of(
                Math.max(paginationParams.getPage(), 0),
                normalizedSize(paginationParams),
                Sort.by(Sort.Direction.ASC, "dueDate"));
    }

    private int normalizedSize(PaginationParams paginationParams) {
        return paginationParams.toPageable().getPageSize();
    }
}
