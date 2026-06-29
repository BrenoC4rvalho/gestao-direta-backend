package br.com.gestaodireta.financial.service;

import br.com.gestaodireta.farm.entity.Farm;
import br.com.gestaodireta.farm.enumeration.FarmStatus;
import br.com.gestaodireta.farm.service.FarmService;
import br.com.gestaodireta.financial.dto.FinancialTransactionFilterRequest;
import br.com.gestaodireta.financial.dto.FinancialTransactionRequest;
import br.com.gestaodireta.financial.dto.FinancialTransactionResponse;
import br.com.gestaodireta.financial.dto.FinancialTransactionUpdateRequest;
import br.com.gestaodireta.financial.dto.PayTransactionRequest;
import br.com.gestaodireta.financial.entity.FinancialCategory;
import br.com.gestaodireta.financial.entity.FinancialTransaction;
import br.com.gestaodireta.financial.enumeration.FinancialCategoryStatus;
import br.com.gestaodireta.financial.enumeration.FinancialRecordStatus;
import br.com.gestaodireta.financial.enumeration.PaymentMethod;
import br.com.gestaodireta.financial.enumeration.PaymentStatus;
import br.com.gestaodireta.financial.enumeration.TransactionType;
import br.com.gestaodireta.financial.mapper.FinancialTransactionMapper;
import br.com.gestaodireta.financial.repository.FinancialTransactionRepository;
import br.com.gestaodireta.shared.exception.BusinessException;
import br.com.gestaodireta.shared.exception.ResourceNotFoundException;
import br.com.gestaodireta.shared.exception.ValidationException;
import br.com.gestaodireta.shared.pagination.PaginationParams;
import br.com.gestaodireta.shared.response.PageResponse;
import br.com.gestaodireta.shared.security.SecurityUtils;
import br.com.gestaodireta.user.entity.User;
import br.com.gestaodireta.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FinancialTransactionService {

    private static final LocalDate MIN_FILTER_DATE = LocalDate.of(1, 1, 1);

    private static final LocalDate MAX_FILTER_DATE = LocalDate.of(9999, 12, 31);

    private static final BigDecimal MAX_FILTER_AMOUNT = new BigDecimal("9999999999999.99");

    private final FinancialTransactionRepository financialTransactionRepository;

    private final FarmService farmService;

    private final FinancialCategoryService financialCategoryService;

    private final UserRepository userRepository;

    private final FinancialTransactionMapper financialTransactionMapper;

    private final Clock clock;

    public FinancialTransactionService(
            FinancialTransactionRepository financialTransactionRepository,
            FarmService farmService,
            FinancialCategoryService financialCategoryService,
            UserRepository userRepository,
            FinancialTransactionMapper financialTransactionMapper,
            Clock clock) {
        this.financialTransactionRepository = financialTransactionRepository;
        this.farmService = farmService;
        this.financialCategoryService = financialCategoryService;
        this.userRepository = userRepository;
        this.financialTransactionMapper = financialTransactionMapper;
        this.clock = clock;
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
        return findAll(defaultFilter(farmId), paginationParams);
    }

    @Transactional(readOnly = true)
    public PageResponse<FinancialTransactionResponse> findAll(
            FinancialTransactionFilterRequest filterRequest, PaginationParams paginationParams) {
        FinancialTransactionFilterRequest normalizedFilter = normalizeFilter(filterRequest);
        validateFilter(normalizedFilter);

        Page<FinancialTransactionResponse> transactions =
                financialTransactionRepository
                        .findAllFiltered(
                                normalizedFilter.farmId(),
                                startDateOrDefault(normalizedFilter.transactionDateStart()),
                                endDateOrDefault(normalizedFilter.transactionDateEnd()),
                                startDateOrDefault(normalizedFilter.paidAtStart()),
                                endDateOrDefault(normalizedFilter.paidAtEnd()),
                                shouldFilterPaidAt(normalizedFilter),
                                normalizedFilter.type(),
                                hasCategoryIds(normalizedFilter),
                                categoryIdsOrPlaceholder(normalizedFilter),
                                hasPaymentStatuses(normalizedFilter),
                                paymentStatusesOrPlaceholder(normalizedFilter),
                                hasPaymentMethods(normalizedFilter),
                                paymentMethodsOrPlaceholder(normalizedFilter),
                                normalizedFilter.recordStatus(),
                                normalizedFilter.description(),
                                normalizedFilter.createdByUserId(),
                                minAmountOrDefault(normalizedFilter.minAmount()),
                                maxAmountOrDefault(normalizedFilter.maxAmount()),
                                paginationParams.toPageable())
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

    @Transactional
    public int markOverdueTransactions() {
        LocalDate today = LocalDate.now(clock);
        LocalDateTime updatedAt = LocalDateTime.now(clock);

        return financialTransactionRepository.markOverdueTransactions(
                PaymentStatus.OVERDUE,
                updatedAt,
                TransactionType.EXPENSE,
                PaymentStatus.PENDING,
                FinancialRecordStatus.ACTIVE,
                today);
    }

    public FinancialTransaction findEntityById(Long id) {
        return financialTransactionRepository
                .findById(id)
                .orElseThrow(
                        () -> new ResourceNotFoundException("Financial transaction not found"));
    }

    private FinancialTransactionFilterRequest defaultFilter(Long farmId) {
        return new FinancialTransactionFilterRequest(
                farmId, null, null, null, null, null, null, null, null, null, null, null, null,
                null);
    }

    private FinancialTransactionFilterRequest normalizeFilter(
            FinancialTransactionFilterRequest filterRequest) {
        if (filterRequest == null) {
            return defaultFilter(null);
        }

        FinancialRecordStatus recordStatus =
                filterRequest.recordStatus() == null
                        ? FinancialRecordStatus.ACTIVE
                        : filterRequest.recordStatus();

        return new FinancialTransactionFilterRequest(
                filterRequest.farmId(),
                filterRequest.transactionDateStart(),
                filterRequest.transactionDateEnd(),
                filterRequest.paidAtStart(),
                filterRequest.paidAtEnd(),
                filterRequest.type(),
                filterRequest.categoryId(),
                effectiveValues(filterRequest.categoryId(), filterRequest.categoryIds()),
                filterRequest.paymentStatus(),
                effectiveValues(filterRequest.paymentStatus(), filterRequest.paymentStatuses()),
                filterRequest.paymentMethod(),
                effectiveValues(filterRequest.paymentMethod(), filterRequest.paymentMethods()),
                recordStatus,
                normalizeNullableLowercaseText(filterRequest.description()),
                filterRequest.createdByUserId(),
                filterRequest.minAmount(),
                filterRequest.maxAmount());
    }

    private void validateFilter(FinancialTransactionFilterRequest filterRequest) {
        if (filterRequest.transactionDateStart() != null
                && filterRequest.transactionDateEnd() != null
                && filterRequest
                        .transactionDateStart()
                        .isAfter(filterRequest.transactionDateEnd())) {
            throw new ValidationException(
                    "Transaction date start cannot be after transaction date end");
        }

        if (filterRequest.paidAtStart() != null
                && filterRequest.paidAtEnd() != null
                && filterRequest.paidAtStart().isAfter(filterRequest.paidAtEnd())) {
            throw new ValidationException("Paid at start cannot be after paid at end");
        }

        ensureNonNegativeAmount(filterRequest.minAmount(), "Minimum amount cannot be negative");
        ensureNonNegativeAmount(filterRequest.maxAmount(), "Maximum amount cannot be negative");

        if (filterRequest.minAmount() != null
                && filterRequest.maxAmount() != null
                && filterRequest.minAmount().compareTo(filterRequest.maxAmount()) > 0) {
            throw new ValidationException("Minimum amount cannot be greater than maximum amount");
        }
    }

    private void ensureNonNegativeAmount(BigDecimal amount, String message) {
        if (amount != null && amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new ValidationException(message);
        }
    }

    private boolean hasCategoryIds(FinancialTransactionFilterRequest filterRequest) {
        return filterRequest.categoryIds() != null;
    }

    private List<Long> categoryIdsOrPlaceholder(FinancialTransactionFilterRequest filterRequest) {
        if (hasCategoryIds(filterRequest)) {
            return filterRequest.categoryIds();
        }

        return List.of(-1L);
    }

    private boolean hasPaymentStatuses(FinancialTransactionFilterRequest filterRequest) {
        return filterRequest.paymentStatuses() != null;
    }

    private List<PaymentStatus> paymentStatusesOrPlaceholder(
            FinancialTransactionFilterRequest filterRequest) {
        if (hasPaymentStatuses(filterRequest)) {
            return filterRequest.paymentStatuses();
        }

        return List.of(PaymentStatus.PENDING);
    }

    private boolean hasPaymentMethods(FinancialTransactionFilterRequest filterRequest) {
        return filterRequest.paymentMethods() != null;
    }

    private List<PaymentMethod> paymentMethodsOrPlaceholder(
            FinancialTransactionFilterRequest filterRequest) {
        if (hasPaymentMethods(filterRequest)) {
            return filterRequest.paymentMethods();
        }

        return List.of(PaymentMethod.CASH);
    }

    private <T> List<T> effectiveValues(T value, List<T> values) {
        List<T> normalizedValues = normalizeList(values);

        if (normalizedValues != null) {
            return normalizedValues;
        }

        if (value == null) {
            return null;
        }

        return List.of(value);
    }

    private <T> List<T> normalizeList(List<T> values) {
        if (values == null) {
            return null;
        }

        List<T> normalizedValues = values.stream().filter(Objects::nonNull).distinct().toList();

        if (normalizedValues.isEmpty()) {
            return null;
        }

        return normalizedValues;
    }

    private String normalizeNullableLowercaseText(String value) {
        if (value == null) {
            return null;
        }

        String normalizedValue = value.trim().toLowerCase(Locale.ROOT);

        if (normalizedValue.isBlank()) {
            return null;
        }

        return normalizedValue;
    }

    private LocalDate startDateOrDefault(LocalDate date) {
        return date == null ? MIN_FILTER_DATE : date;
    }

    private LocalDate endDateOrDefault(LocalDate date) {
        return date == null ? MAX_FILTER_DATE : date;
    }

    private boolean shouldFilterPaidAt(FinancialTransactionFilterRequest filterRequest) {
        return filterRequest.paidAtStart() != null || filterRequest.paidAtEnd() != null;
    }

    private BigDecimal minAmountOrDefault(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    private BigDecimal maxAmountOrDefault(BigDecimal amount) {
        return amount == null ? MAX_FILTER_AMOUNT : amount;
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
