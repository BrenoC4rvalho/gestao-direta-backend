package br.com.gestaodireta.financial.controller;

import br.com.gestaodireta.financial.dto.FinancialTransactionRequest;
import br.com.gestaodireta.financial.dto.FinancialTransactionResponse;
import br.com.gestaodireta.financial.dto.FinancialTransactionUpdateRequest;
import br.com.gestaodireta.financial.dto.PayTransactionRequest;
import br.com.gestaodireta.financial.service.FinancialTransactionService;
import br.com.gestaodireta.shared.pagination.PaginationParams;
import br.com.gestaodireta.shared.response.PageResponse;
import jakarta.validation.Valid;
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
@RequestMapping("/financial/transactions")
public class FinancialTransactionController {

    private final FinancialTransactionService financialTransactionService;

    public FinancialTransactionController(FinancialTransactionService financialTransactionService) {
        this.financialTransactionService = financialTransactionService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@financialAccess.canCreateTransaction(#request)")
    public FinancialTransactionResponse create(
            @Valid @RequestBody FinancialTransactionRequest request) {
        return financialTransactionService.create(request);
    }

    @GetMapping
    @PreAuthorize("@financialAccess.canViewFinancialData(#farmId)")
    public PageResponse<FinancialTransactionResponse> findAll(
            @RequestParam Long farmId, @Valid @ModelAttribute PaginationParams paginationParams) {
        return financialTransactionService.findAll(farmId, paginationParams);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@financialAccess.canViewTransaction(#id)")
    public FinancialTransactionResponse findById(@PathVariable Long id) {
        return financialTransactionService.findById(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@financialAccess.canUpdateTransaction(#id)")
    public FinancialTransactionResponse update(
            @PathVariable Long id, @Valid @RequestBody FinancialTransactionUpdateRequest request) {
        return financialTransactionService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@financialAccess.canDeleteTransaction(#id)")
    public void delete(@PathVariable Long id) {
        financialTransactionService.delete(id);
    }

    @PatchMapping("/{id}/pay")
    @PreAuthorize("@financialAccess.canPayTransaction(#id)")
    public FinancialTransactionResponse pay(
            @PathVariable Long id, @RequestBody PayTransactionRequest request) {
        return financialTransactionService.pay(id, request);
    }

    @PatchMapping("/{id}/cancel")
    @PreAuthorize("@financialAccess.canCancelTransaction(#id)")
    public FinancialTransactionResponse cancel(@PathVariable Long id) {
        return financialTransactionService.cancel(id);
    }
}
