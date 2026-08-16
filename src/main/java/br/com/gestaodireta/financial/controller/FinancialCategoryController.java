package br.com.gestaodireta.financial.controller;

import br.com.gestaodireta.financial.dto.FinancialCategoryCreateRequest;
import br.com.gestaodireta.financial.dto.FinancialCategoryResponse;
import br.com.gestaodireta.financial.dto.FinancialCategoryUpdateRequest;
import br.com.gestaodireta.financial.enumeration.FinancialCategoryStatus;
import br.com.gestaodireta.financial.enumeration.FinancialCategoryStatus;
import br.com.gestaodireta.financial.service.FinancialCategoryService;
import br.com.gestaodireta.shared.pagination.PaginationParams;
import br.com.gestaodireta.shared.response.PageResponse;
import jakarta.validation.Valid;
import java.util.List;
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
@RequestMapping("/financial/categories")
public class FinancialCategoryController {

    private final FinancialCategoryService financialCategoryService;

    public FinancialCategoryController(FinancialCategoryService financialCategoryService) {
        this.financialCategoryService = financialCategoryService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@financialAccess.canCreateCategory(#request)")
    public FinancialCategoryResponse create(
            @Valid @RequestBody FinancialCategoryCreateRequest request) {
        return financialCategoryService.create(request);
    }

    @GetMapping
    @PreAuthorize("@financialAccess.canViewFinancialData(#farmId)")
    public PageResponse<FinancialCategoryResponse> findAll(
            @RequestParam Long farmId,
            @RequestParam(defaultValue = "false") boolean includeInactive,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) FinancialCategoryStatus status,
            @Valid @ModelAttribute PaginationParams paginationParams) {
        return financialCategoryService.findAll(
                farmId, includeInactive, search, status, paginationParams);
    }

    @GetMapping("/used-in-transactions")
    @PreAuthorize("@financialAccess.canViewFinancialDataOrMissingFarm(#farmId)")
    public List<FinancialCategoryResponse> findUsedInTransactions(@RequestParam Long farmId) {
        return financialCategoryService.findUsedInTransactions(farmId);
    }

    @GetMapping("/global")
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public void removedGlobalCategoriesEndpoint() {}

    @GetMapping("/{id:\\d+}")
    @PreAuthorize("@financialAccess.canViewCategory(#id)")
    public FinancialCategoryResponse findById(@PathVariable Long id) {
        return financialCategoryService.findById(id);
    }

    @PutMapping("/{id:\\d+}")
    @PreAuthorize("@financialAccess.canUpdateCategory(#id)")
    public FinancialCategoryResponse update(
            @PathVariable Long id, @Valid @RequestBody FinancialCategoryUpdateRequest request) {
        return financialCategoryService.update(id, request);
    }

    @PatchMapping("/{id:\\d+}/activate")
    @PreAuthorize("@financialAccess.canManageCategoryByCategoryId(#id)")
    public FinancialCategoryResponse activate(@PathVariable Long id) {
        return financialCategoryService.activate(id);
    }

    @DeleteMapping("/{id:\\d+}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@financialAccess.canManageCategoryByCategoryId(#id)")
    public void inactivate(@PathVariable Long id) {
        financialCategoryService.inactivate(id);
    }
}
