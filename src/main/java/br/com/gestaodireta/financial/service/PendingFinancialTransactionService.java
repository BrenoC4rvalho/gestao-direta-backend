package br.com.gestaodireta.financial.service;

import br.com.gestaodireta.financial.dto.*;
import br.com.gestaodireta.financial.entity.FinancialCategory;
import br.com.gestaodireta.financial.entity.PendingFinancialTransaction;
import br.com.gestaodireta.financial.enumeration.*;
import br.com.gestaodireta.financial.mapper.PendingFinancialTransactionMapper;
import br.com.gestaodireta.financial.repository.PendingFinancialTransactionRepository;
import br.com.gestaodireta.messaging.service.OutgoingMessagingService;
import br.com.gestaodireta.shared.exception.BusinessException;
import br.com.gestaodireta.shared.exception.ResourceNotFoundException;
import br.com.gestaodireta.shared.pagination.PaginationParams;
import br.com.gestaodireta.shared.response.PageResponse;
import br.com.gestaodireta.shared.security.SecurityUtils;
import br.com.gestaodireta.user.entity.User;
import br.com.gestaodireta.user.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class PendingFinancialTransactionService {
    private static final String PROCESSED_MESSAGE = "Esta pendência já foi processada.";
    private final PendingFinancialTransactionRepository repository;
    private final FinancialCategoryService categoryService;
    private final FinancialTransactionService transactionService;
    private final PendingFinancialTransactionMapper mapper;
    private final UserRepository userRepository;
    private final Clock clock;
    private final OutgoingMessagingService outgoing;

    public PendingFinancialTransactionService(
            PendingFinancialTransactionRepository repository,
            FinancialCategoryService categoryService,
            FinancialTransactionService transactionService,
            PendingFinancialTransactionMapper mapper,
            UserRepository userRepository,
            Clock clock,
            OutgoingMessagingService outgoing) {
        this.repository = repository;
        this.categoryService = categoryService;
        this.transactionService = transactionService;
        this.mapper = mapper;
        this.userRepository = userRepository;
        this.clock = clock;
        this.outgoing = outgoing;
    }

    @Transactional(readOnly = true)
    public PageResponse<PendingFinancialTransactionResponse> findAll(
            PendingFinancialTransactionFilterRequest filter, PaginationParams pagination) {
        Page<PendingFinancialTransactionResponse> page =
                repository
                        .findAllFiltered(
                                filter.farmId(),
                                filter.status(),
                                filter.type(),
                                filter.startDate(),
                                filter.endDate(),
                                filter.requestedByUserId(),
                                pagination.toPageable())
                        .map(mapper::toResponse);
        return PageResponse.from(page);
    }

    @Transactional(readOnly = true)
    public PendingFinancialTransactionResponse findById(Long id) {
        return mapper.toResponse(findEntityById(id));
    }

    @Transactional
    public PendingFinancialTransactionResponse update(
            Long id, PendingFinancialTransactionUpdateRequest request) {
        PendingFinancialTransaction pending = findForUpdate(id);
        ensurePending(pending);
        FinancialCategory category =
                resolveCategory(request.categoryId(), pending.getFarm().getId(), request.type());
        pending.setType(request.type());
        pending.setAmount(request.amount());
        pending.setTransactionDate(request.transactionDate());
        pending.setDescription(request.description().trim());
        pending.setSuggestedCategory(category);
        if (category != null) {
            pending.setRawCategoryName(null);
        }
        return mapper.toResponse(repository.save(pending));
    }

    @Transactional
    public PendingFinancialTransactionResponse approve(Long id) {
        PendingFinancialTransaction pending = findForUpdate(id);
        ensurePending(pending);
        FinancialCategory category =
                resolveCategory(
                        pending.getSuggestedCategory() == null
                                ? null
                                : pending.getSuggestedCategory().getId(),
                        pending.getFarm().getId(),
                        pending.getType());
        FinancialTransactionRequest request =
                new FinancialTransactionRequest(
                        pending.getDescription(),
                        pending.getAmount(),
                        pending.getType(),
                        PaymentStatus.PENDING,
                        null,
                        pending.getTransactionDate(),
                        null,
                        null,
                        null,
                        pending.getFarm().getId(),
                        category == null ? null : category.getId(),
                        null);
        Long transactionId = transactionService.create(request).id();
        pending.setApprovedFinancialTransaction(transactionService.findEntityById(transactionId));
        pending.setStatus(PendingFinancialTransactionStatus.APPROVED);
        pending.setReviewedByUser(currentUser());
        pending.setReviewedAt(LocalDateTime.now(clock));
        PendingFinancialTransactionResponse response = mapper.toResponse(repository.save(pending));
        notifyAfterCommit(
                pending,
                "Movimentação aprovada no Gestão Direta:\n\n"
                        + pending.getDescription()
                        + "\n"
                        + pending.getFarm().getName());
        return response;
    }

    @Transactional
    public PendingFinancialTransactionResponse reject(
            Long id, RejectPendingFinancialTransactionRequest request) {
        PendingFinancialTransaction pending = findForUpdate(id);
        ensurePending(pending);
        pending.setStatus(PendingFinancialTransactionStatus.REJECTED);
        pending.setReviewedByUser(currentUser());
        pending.setReviewedAt(LocalDateTime.now(clock));
        pending.setRejectionReason(
                request == null || request.reason() == null ? null : request.reason().trim());
        PendingFinancialTransactionResponse response = mapper.toResponse(repository.save(pending));
        notifyAfterCommit(pending, "A movimentação enviada foi rejeitada no Gestão Direta.");
        return response;
    }

    private void notifyAfterCommit(PendingFinancialTransaction pending, String content) {
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        outgoing.send(pending.getMessagingConversation(), content);
                    }
                });
    }

    public PendingFinancialTransaction findEntityById(Long id) {
        return repository
                .findById(id)
                .orElseThrow(
                        () ->
                                new ResourceNotFoundException(
                                        "Pending financial transaction not found"));
    }

    private PendingFinancialTransaction findForUpdate(Long id) {
        return repository
                .findByIdForUpdate(id)
                .orElseThrow(
                        () ->
                                new ResourceNotFoundException(
                                        "Pending financial transaction not found"));
    }

    private FinancialCategory resolveCategory(Long categoryId, Long farmId, TransactionType type) {
        if (categoryId == null) return null;
        FinancialCategory category = categoryService.findEntityById(categoryId);
        if (!category.getFarm().getId().equals(farmId)
                || category.getStatus() != FinancialCategoryStatus.ACTIVE
                || category.getType() != type) {
            throw new BusinessException("Invalid category for pending financial transaction");
        }
        return category;
    }

    private void ensurePending(PendingFinancialTransaction pending) {
        if (pending.getStatus() != PendingFinancialTransactionStatus.PENDING_REVIEW) {
            throw new BusinessException(PROCESSED_MESSAGE);
        }
    }

    private User currentUser() {
        return userRepository
                .findById(SecurityUtils.getAuthenticatedUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
