package br.com.gestaodireta.financial.controller;

import br.com.gestaodireta.financial.dto.FinancialTransactionExportFile;
import br.com.gestaodireta.financial.dto.FinancialTransactionFilterRequest;
import br.com.gestaodireta.financial.dto.FinancialTransactionRequest;
import br.com.gestaodireta.financial.dto.FinancialTransactionResponse;
import br.com.gestaodireta.financial.dto.FinancialTransactionUpdateRequest;
import br.com.gestaodireta.financial.dto.PayTransactionRequest;
import br.com.gestaodireta.financial.enumeration.FinancialRecordStatus;
import br.com.gestaodireta.financial.enumeration.FinancialReportBasis;
import br.com.gestaodireta.financial.enumeration.PaymentMethod;
import br.com.gestaodireta.financial.enumeration.PaymentStatus;
import br.com.gestaodireta.financial.enumeration.TransactionType;
import br.com.gestaodireta.financial.service.FinancialTransactionExportService;
import br.com.gestaodireta.financial.service.FinancialTransactionService;
import br.com.gestaodireta.shared.pagination.PaginationParams;
import br.com.gestaodireta.shared.response.PageResponse;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    private final FinancialTransactionExportService financialTransactionExportService;

    public FinancialTransactionController(
            FinancialTransactionService financialTransactionService,
            FinancialTransactionExportService financialTransactionExportService) {
        this.financialTransactionService = financialTransactionService;
        this.financialTransactionExportService = financialTransactionExportService;
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
            @RequestParam Long farmId,
            @RequestParam(required = false) LocalDate transactionDateStart,
            @RequestParam(required = false) LocalDate transactionDateEnd,
            @RequestParam(required = false) LocalDate paidAtStart,
            @RequestParam(required = false) LocalDate paidAtEnd,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) List<Long> categoryIds,
            @RequestParam(required = false) Long harvestSeasonId,
            @RequestParam(required = false) PaymentStatus paymentStatus,
            @RequestParam(required = false) List<PaymentStatus> paymentStatuses,
            @RequestParam(required = false) PaymentMethod paymentMethod,
            @RequestParam(required = false) List<PaymentMethod> paymentMethods,
            @RequestParam(required = false) FinancialRecordStatus recordStatus,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) Long createdByUserId,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            @RequestParam(defaultValue = "ACCRUAL") FinancialReportBasis basis,
            @Valid @ModelAttribute PaginationParams paginationParams) {
        FinancialTransactionFilterRequest filterRequest =
                new FinancialTransactionFilterRequest(
                        farmId,
                        transactionDateStart,
                        transactionDateEnd,
                        paidAtStart,
                        paidAtEnd,
                        type,
                        categoryId,
                        categoryIds,
                        harvestSeasonId,
                        paymentStatus,
                        paymentStatuses,
                        paymentMethod,
                        paymentMethods,
                        recordStatus,
                        description,
                        createdByUserId,
                        minAmount,
                        maxAmount,
                        basis);

        return financialTransactionService.findAll(filterRequest, paginationParams);
    }

    @GetMapping("/export/xlsx")
    @PreAuthorize("@financialAccess.canViewFinancialData(#farmId)")
    public ResponseEntity<byte[]> exportXlsx(
            @RequestParam Long farmId,
            @RequestParam(required = false) LocalDate transactionDateStart,
            @RequestParam(required = false) LocalDate transactionDateEnd,
            @RequestParam(required = false) LocalDate paidAtStart,
            @RequestParam(required = false) LocalDate paidAtEnd,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) List<Long> categoryIds,
            @RequestParam(required = false) Long harvestSeasonId,
            @RequestParam(required = false) PaymentStatus paymentStatus,
            @RequestParam(required = false) List<PaymentStatus> paymentStatuses,
            @RequestParam(required = false) PaymentMethod paymentMethod,
            @RequestParam(required = false) List<PaymentMethod> paymentMethods,
            @RequestParam(required = false) FinancialRecordStatus recordStatus,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) Long createdByUserId,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount) {
        return export(
                financialTransactionExportService.exportXlsx(
                        toFilter(
                                farmId,
                                transactionDateStart,
                                transactionDateEnd,
                                paidAtStart,
                                paidAtEnd,
                                type,
                                categoryId,
                                categoryIds,
                                harvestSeasonId,
                                paymentStatus,
                                paymentStatuses,
                                paymentMethod,
                                paymentMethods,
                                recordStatus,
                                description,
                                createdByUserId,
                                minAmount,
                                maxAmount)));
    }

    @GetMapping("/export/pdf")
    @PreAuthorize("@financialAccess.canViewFinancialData(#farmId)")
    public ResponseEntity<byte[]> exportPdf(
            @RequestParam Long farmId,
            @RequestParam(required = false) LocalDate transactionDateStart,
            @RequestParam(required = false) LocalDate transactionDateEnd,
            @RequestParam(required = false) LocalDate paidAtStart,
            @RequestParam(required = false) LocalDate paidAtEnd,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) List<Long> categoryIds,
            @RequestParam(required = false) Long harvestSeasonId,
            @RequestParam(required = false) PaymentStatus paymentStatus,
            @RequestParam(required = false) List<PaymentStatus> paymentStatuses,
            @RequestParam(required = false) PaymentMethod paymentMethod,
            @RequestParam(required = false) List<PaymentMethod> paymentMethods,
            @RequestParam(required = false) FinancialRecordStatus recordStatus,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) Long createdByUserId,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount) {
        return export(
                financialTransactionExportService.exportPdf(
                        toFilter(
                                farmId,
                                transactionDateStart,
                                transactionDateEnd,
                                paidAtStart,
                                paidAtEnd,
                                type,
                                categoryId,
                                categoryIds,
                                harvestSeasonId,
                                paymentStatus,
                                paymentStatuses,
                                paymentMethod,
                                paymentMethods,
                                recordStatus,
                                description,
                                createdByUserId,
                                minAmount,
                                maxAmount)));
    }

    private FinancialTransactionFilterRequest toFilter(
            Long farmId,
            LocalDate transactionDateStart,
            LocalDate transactionDateEnd,
            LocalDate paidAtStart,
            LocalDate paidAtEnd,
            TransactionType type,
            Long categoryId,
            List<Long> categoryIds,
            Long harvestSeasonId,
            PaymentStatus paymentStatus,
            List<PaymentStatus> paymentStatuses,
            PaymentMethod paymentMethod,
            List<PaymentMethod> paymentMethods,
            FinancialRecordStatus recordStatus,
            String description,
            Long createdByUserId,
            BigDecimal minAmount,
            BigDecimal maxAmount) {
        return new FinancialTransactionFilterRequest(
                farmId,
                transactionDateStart,
                transactionDateEnd,
                paidAtStart,
                paidAtEnd,
                type,
                categoryId,
                categoryIds,
                harvestSeasonId,
                paymentStatus,
                paymentStatuses,
                paymentMethod,
                paymentMethods,
                recordStatus,
                description,
                createdByUserId,
                minAmount,
                maxAmount);
    }

    private ResponseEntity<byte[]> export(FinancialTransactionExportFile file) {
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + file.filename() + "\"")
                .header(HttpHeaders.CONTENT_TYPE, file.contentType())
                .body(file.content());
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
