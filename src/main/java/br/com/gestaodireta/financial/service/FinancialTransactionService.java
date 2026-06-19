package br.com.gestaodireta.financial.service;

import br.com.gestaodireta.farm.entity.Farm;
import br.com.gestaodireta.farm.enumeration.FarmStatus;
import br.com.gestaodireta.farm.service.FarmService;
import br.com.gestaodireta.financial.dto.FinancialTransactionRequest;
import br.com.gestaodireta.financial.dto.FinancialTransactionResponse;
import br.com.gestaodireta.financial.dto.FinancialTransactionUpdateRequest;
import br.com.gestaodireta.financial.dto.PayTransactionRequest;
import br.com.gestaodireta.financial.entity.FinancialCategory;
import br.com.gestaodireta.financial.entity.FinancialTransaction;
import br.com.gestaodireta.financial.enumeration.FinancialCategoryStatus;
import br.com.gestaodireta.financial.enumeration.FinancialRecordStatus;
import br.com.gestaodireta.financial.enumeration.PaymentStatus;
import br.com.gestaodireta.financial.enumeration.TransactionType;
import br.com.gestaodireta.financial.mapper.FinancialTransactionMapper;
import br.com.gestaodireta.financial.repository.FinancialTransactionRepository;
import br.com.gestaodireta.shared.exception.BusinessException;
import br.com.gestaodireta.shared.exception.ResourceNotFoundException;
import br.com.gestaodireta.shared.pagination.PaginationParams;
import br.com.gestaodireta.shared.response.PageResponse;
import br.com.gestaodireta.shared.security.SecurityUtils;
import br.com.gestaodireta.user.entity.User;
import br.com.gestaodireta.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FinancialTransactionService {

    private final FinancialTransactionRepository financialTransactionRepository;

    private final FarmService farmService;

    private final FinancialCategoryService financialCategoryService;

    private final UserRepository userRepository;

    private final FinancialTransactionMapper financialTransactionMapper;

    public FinancialTransactionService(
            FinancialTransactionRepository financialTransactionRepository,
            FarmService farmService,
            FinancialCategoryService financialCategoryService,
            UserRepository userRepository,
            FinancialTransactionMapper financialTransactionMapper) {
        this.financialTransactionRepository = financialTransactionRepository;
        this.farmService = farmService;
        this.financialCategoryService = financialCategoryService;
        this.userRepository = userRepository;
        this.financialTransactionMapper = financialTransactionMapper;
    }

    @Transactional
    public FinancialTransactionResponse create(FinancialTransactionRequest request) {
        Farm farm = farmService.findEntityById(request.farmId());
        ensureFarmIsActive(farm);
        FinancialCategory category = resolveCategory(request.categoryId(), farm, request.type());
        User currentUser = getCurrentUser();

        FinancialTransaction transaction = new FinancialTransaction();
        transaction.setFarm(farm);
        transaction.setCreatedByUser(currentUser);
        transaction.setRecordStatus(FinancialRecordStatus.ACTIVE);
        transaction.setStatus(request.status() == null ? PaymentStatus.PENDING : request.status());
        applyRequest(transaction, request, category);

        return financialTransactionMapper.toResponse(
                financialTransactionRepository.save(transaction));
    }

    @Transactional(readOnly = true)
    public PageResponse<FinancialTransactionResponse> findAll(
            Long farmId, PaginationParams paginationParams) {
        Page<FinancialTransactionResponse> transactions =
                financialTransactionRepository
                        .findByFarmIdAndRecordStatus(
                                farmId, FinancialRecordStatus.ACTIVE, paginationParams.toPageable())
                        .map(financialTransactionMapper::toResponse);

        return PageResponse.from(transactions);
    }

    @Transactional(readOnly = true)
    public FinancialTransactionResponse findById(Long id) {
        return financialTransactionMapper.toResponse(findEntityById(id));
    }

    @Transactional
    public FinancialTransactionResponse update(Long id, FinancialTransactionUpdateRequest request) {
        FinancialTransaction transaction = findEntityById(id);
        FinancialCategory category =
                resolveCategory(request.categoryId(), transaction.getFarm(), request.type());
        transaction.setUpdatedByUser(getCurrentUser());
        transaction.setStatus(request.status());
        applyRequest(transaction, request, category);

        return financialTransactionMapper.toResponse(
                financialTransactionRepository.save(transaction));
    }

    @Transactional
    public void delete(Long id) {
        FinancialTransaction transaction = findEntityById(id);
        transaction.setRecordStatus(FinancialRecordStatus.DELETED);
        transaction.setUpdatedByUser(getCurrentUser());
        financialTransactionRepository.save(transaction);
    }

    @Transactional
    public FinancialTransactionResponse pay(Long id, PayTransactionRequest request) {
        FinancialTransaction transaction = findEntityById(id);
        transaction.setStatus(PaymentStatus.PAID);
        transaction.setPaidAt(request.paidAt() == null ? LocalDate.now() : request.paidAt());
        transaction.setPaymentMethod(request.paymentMethod());
        transaction.setUpdatedByUser(getCurrentUser());

        return financialTransactionMapper.toResponse(
                financialTransactionRepository.save(transaction));
    }

    @Transactional
    public FinancialTransactionResponse cancel(Long id) {
        FinancialTransaction transaction = findEntityById(id);
        transaction.setStatus(PaymentStatus.CANCELED);
        transaction.setUpdatedByUser(getCurrentUser());

        return financialTransactionMapper.toResponse(
                financialTransactionRepository.save(transaction));
    }

    public FinancialTransaction findEntityById(Long id) {
        return financialTransactionRepository
                .findById(id)
                .orElseThrow(
                        () -> new ResourceNotFoundException("Financial transaction not found"));
    }

    private void applyRequest(
            FinancialTransaction transaction,
            FinancialTransactionRequest request,
            FinancialCategory category) {
        ensurePositiveAmount(request.amount());
        transaction.setDescription(request.description());
        transaction.setAmount(request.amount());
        transaction.setType(request.type());
        transaction.setPaymentMethod(request.paymentMethod());
        transaction.setTransactionDate(request.transactionDate());
        transaction.setDueDate(request.dueDate());
        transaction.setPaidAt(request.paidAt());
        transaction.setNotes(request.notes());
        transaction.setCategory(category);
    }

    private void applyRequest(
            FinancialTransaction transaction,
            FinancialTransactionUpdateRequest request,
            FinancialCategory category) {
        ensurePositiveAmount(request.amount());
        transaction.setDescription(request.description());
        transaction.setAmount(request.amount());
        transaction.setType(request.type());
        transaction.setPaymentMethod(request.paymentMethod());
        transaction.setTransactionDate(request.transactionDate());
        transaction.setDueDate(request.dueDate());
        transaction.setPaidAt(request.paidAt());
        transaction.setNotes(request.notes());
        transaction.setCategory(category);
    }

    private FinancialCategory resolveCategory(
            Long categoryId, Farm farm, TransactionType transactionType) {
        if (categoryId == null) {
            return null;
        }

        FinancialCategory category = financialCategoryService.findEntityById(categoryId);

        if (!FinancialCategoryStatus.ACTIVE.equals(category.getStatus())) {
            throw new BusinessException("Financial category is inactive");
        }

        if (category.getFarm() != null && !category.getFarm().getId().equals(farm.getId())) {
            throw new BusinessException("Financial category does not belong to farm");
        }

        if (!category.getType().equals(transactionType)) {
            throw new BusinessException("Financial category type must match transaction type");
        }

        return category;
    }

    private void ensureFarmIsActive(Farm farm) {
        if (!FarmStatus.ACTIVE.equals(farm.getStatus())) {
            throw new BusinessException("Inactive farm cannot receive financial transactions");
        }
    }

    private void ensurePositiveAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Transaction amount must be greater than zero");
        }
    }

    private User getCurrentUser() {
        return userRepository
                .findById(SecurityUtils.getAuthenticatedUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
