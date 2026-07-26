package br.com.gestaodireta.financial.controller;

import br.com.gestaodireta.financial.dto.*;
import br.com.gestaodireta.financial.enumeration.PendingFinancialTransactionStatus;
import br.com.gestaodireta.financial.enumeration.TransactionType;
import br.com.gestaodireta.financial.service.PendingFinancialTransactionService;
import br.com.gestaodireta.shared.pagination.PaginationParams;
import br.com.gestaodireta.shared.response.PageResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/pending-financial-transactions")
public class PendingFinancialTransactionController {
    private final PendingFinancialTransactionService service;

    public PendingFinancialTransactionController(PendingFinancialTransactionService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("@financialAccess.canViewFinancialData(#farmId)")
    public PageResponse<PendingFinancialTransactionResponse> findAll(
            @RequestParam Long farmId,
            @RequestParam(required = false) PendingFinancialTransactionStatus status,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(required = false) Long requestedByUserId,
            @Valid @ModelAttribute PaginationParams pagination) {
        return service.findAll(
                new PendingFinancialTransactionFilterRequest(
                        farmId, status, type, startDate, endDate, requestedByUserId),
                pagination);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@financialAccess.canViewPendingTransaction(#id)")
    public PendingFinancialTransactionResponse findById(@PathVariable Long id) {
        return service.findById(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@financialAccess.canManagePendingTransaction(#id)")
    public PendingFinancialTransactionResponse update(
            @PathVariable Long id,
            @Valid @RequestBody PendingFinancialTransactionUpdateRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("@financialAccess.canManagePendingTransaction(#id)")
    public PendingFinancialTransactionResponse approve(@PathVariable Long id) {
        return service.approve(id);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("@financialAccess.canManagePendingTransaction(#id)")
    public PendingFinancialTransactionResponse reject(
            @PathVariable Long id,
            @Valid @RequestBody(required = false)
                    RejectPendingFinancialTransactionRequest request) {
        return service.reject(id, request);
    }
}
