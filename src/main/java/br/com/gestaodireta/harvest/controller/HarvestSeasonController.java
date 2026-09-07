package br.com.gestaodireta.harvest.controller;

import br.com.gestaodireta.harvest.dto.DashboardHarvestSeasonResponse;
import br.com.gestaodireta.harvest.dto.HarvestSeasonBudgetItemRequest;
import br.com.gestaodireta.harvest.dto.HarvestSeasonBudgetItemResponse;
import br.com.gestaodireta.harvest.dto.HarvestSeasonBudgetResponse;
import br.com.gestaodireta.harvest.dto.HarvestSeasonDetailSummaryResponse;
import br.com.gestaodireta.harvest.dto.HarvestSeasonFinancialSummaryResponse;
import br.com.gestaodireta.harvest.dto.HarvestSeasonRequest;
import br.com.gestaodireta.harvest.dto.HarvestSeasonResponse;
import br.com.gestaodireta.harvest.dto.HarvestSeasonStatusUpdateRequest;
import br.com.gestaodireta.harvest.dto.HarvestSeasonSummaryListResponse;
import br.com.gestaodireta.harvest.dto.HarvestSeasonUpdateRequest;
import br.com.gestaodireta.harvest.enumeration.HarvestSeasonStatus;
import br.com.gestaodireta.harvest.service.HarvestSeasonBudgetItemService;
import br.com.gestaodireta.harvest.service.HarvestSeasonService;
import br.com.gestaodireta.shared.pagination.PaginationParams;
import br.com.gestaodireta.shared.response.PageResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/harvest/seasons")
public class HarvestSeasonController {

    private final HarvestSeasonService harvestSeasonService;

    private final HarvestSeasonBudgetItemService harvestSeasonBudgetItemService;

    public HarvestSeasonController(
            HarvestSeasonService harvestSeasonService,
            HarvestSeasonBudgetItemService harvestSeasonBudgetItemService) {
        this.harvestSeasonService = harvestSeasonService;
        this.harvestSeasonBudgetItemService = harvestSeasonBudgetItemService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@harvestAccess.canCreateSeason(#request)")
    public HarvestSeasonResponse create(@Valid @RequestBody HarvestSeasonRequest request) {
        return harvestSeasonService.create(request);
    }

    @GetMapping
    @PreAuthorize("@harvestAccess.canViewSeasons(#farmId)")
    public PageResponse<HarvestSeasonResponse> findAll(
            @RequestParam Long farmId,
            @RequestParam(required = false) List<HarvestSeasonStatus> statuses,
            @RequestParam(required = false) Long productionActivityId,
            @RequestParam(required = false) List<Long> productionActivityIds,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate periodStart,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate periodEnd,
            @RequestParam(defaultValue = "false") boolean includeInactive,
            @Valid @ModelAttribute PaginationParams paginationParams) {
        return harvestSeasonService.findAll(
                farmId,
                statuses,
                productionActivityId,
                productionActivityIds,
                periodStart,
                periodEnd,
                includeInactive,
                paginationParams);
    }

    @GetMapping("/summary-list")
    @PreAuthorize("@harvestAccess.canViewSeasons(#farmId)")
    public PageResponse<HarvestSeasonSummaryListResponse> findSummaryList(
            @RequestParam Long farmId,
            @RequestParam(required = false) List<HarvestSeasonStatus> statuses,
            @RequestParam(required = false) Long productionActivityId,
            @RequestParam(required = false) List<Long> productionActivityIds,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate periodStart,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate periodEnd,
            @RequestParam(required = false) String search,
            @Valid @ModelAttribute PaginationParams paginationParams) {
        return harvestSeasonService.findSummaryList(
                farmId,
                statuses,
                productionActivityId,
                productionActivityIds,
                periodStart,
                periodEnd,
                search,
                paginationParams);
    }

    @GetMapping("/summary")
    @PreAuthorize("@harvestAccess.canViewSeasons(#farmId)")
    public HarvestSeasonFinancialSummaryResponse getFinancialSummary(
            @RequestParam Long farmId,
            @RequestParam(required = false) List<HarvestSeasonStatus> statuses,
            @RequestParam(required = false) Long productionActivityId,
            @RequestParam(required = false) List<Long> productionActivityIds,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate periodStart,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate periodEnd,
            @RequestParam(required = false) String search) {
        return harvestSeasonService.getFinancialSummary(
                farmId,
                statuses,
                productionActivityId,
                productionActivityIds,
                periodStart,
                periodEnd,
                search);
    }

    @GetMapping("/dashboard")
    @PreAuthorize("@harvestAccess.canViewSeasons(#farmId)")
    public List<DashboardHarvestSeasonResponse> findDashboardSeasons(@RequestParam Long farmId) {
        return harvestSeasonService.findDashboardSeasons(farmId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@harvestAccess.canViewSeason(#id)")
    public HarvestSeasonResponse findById(@PathVariable Long id) {
        return harvestSeasonService.findById(id);
    }

    @GetMapping("/{id}/summary")
    @PreAuthorize("@harvestAccess.canViewSeason(#id)")
    public HarvestSeasonDetailSummaryResponse getSummary(@PathVariable Long id) {
        return harvestSeasonService.getSummary(id);
    }

    @GetMapping("/{id}/budget-items")
    @PreAuthorize("@harvestAccess.canViewSeason(#id)")
    public HarvestSeasonBudgetResponse findBudgetItems(@PathVariable Long id) {
        return harvestSeasonBudgetItemService.findAll(id);
    }

    @PostMapping("/{id}/budget-items")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@harvestAccess.canManageSeason(#id)")
    public HarvestSeasonBudgetItemResponse createBudgetItem(
            @PathVariable Long id, @Valid @RequestBody HarvestSeasonBudgetItemRequest request) {
        return harvestSeasonBudgetItemService.create(id, request);
    }

    @PutMapping("/{id}/budget-items/{itemId}")
    @PreAuthorize("@harvestAccess.canManageSeason(#id)")
    public HarvestSeasonBudgetItemResponse updateBudgetItem(
            @PathVariable Long id,
            @PathVariable Long itemId,
            @Valid @RequestBody HarvestSeasonBudgetItemRequest request) {
        return harvestSeasonBudgetItemService.update(id, itemId, request);
    }

    @DeleteMapping("/{id}/budget-items/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@harvestAccess.canManageSeason(#id)")
    public void deleteBudgetItem(@PathVariable Long id, @PathVariable Long itemId) {
        harvestSeasonBudgetItemService.delete(id, itemId);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@harvestAccess.canManageSeason(#id)")
    public HarvestSeasonResponse update(
            @PathVariable Long id, @Valid @RequestBody HarvestSeasonUpdateRequest request) {
        return harvestSeasonService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("@harvestAccess.canChangeSeasonStatus(#id)")
    public HarvestSeasonResponse updateStatus(
            @PathVariable Long id, @Valid @RequestBody HarvestSeasonStatusUpdateRequest request) {
        return harvestSeasonService.updateStatus(id, request);
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("@harvestAccess.canChangeSeasonStatus(#id)")
    public HarvestSeasonResponse activate(@PathVariable Long id) {
        return harvestSeasonService.activate(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@harvestAccess.canChangeSeasonStatus(#id)")
    public void inactivate(@PathVariable Long id) {
        harvestSeasonService.inactivate(id);
    }
}
