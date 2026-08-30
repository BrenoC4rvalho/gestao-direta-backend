package br.com.gestaodireta.financial.service;

import br.com.gestaodireta.financial.dto.*;
import br.com.gestaodireta.financial.entity.FinancialCategory;
import br.com.gestaodireta.financial.entity.PendingFinancialTransaction;
import br.com.gestaodireta.financial.enumeration.*;
import br.com.gestaodireta.financial.mapper.PendingFinancialTransactionMapper;
import br.com.gestaodireta.financial.repository.PendingFinancialTransactionRepository;
import br.com.gestaodireta.harvest.entity.HarvestSeason;
import br.com.gestaodireta.harvest.enumeration.HarvestSeasonStatus;
import br.com.gestaodireta.harvest.repository.HarvestSeasonRepository;
import br.com.gestaodireta.messaging.repository.MessagingConversationRepository;
import br.com.gestaodireta.messaging.service.OutgoingMessagingService;
import br.com.gestaodireta.shared.exception.BusinessException;
import br.com.gestaodireta.shared.exception.ConflictException;
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
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PendingFinancialTransactionService {
    private static final String PROCESSED_MESSAGE = "Esta pendência já foi processada.";
    private final PendingFinancialTransactionRepository repository;
    private final FinancialCategoryService categoryService;
    private final FinancialTransactionService transactionService;
    private final PendingFinancialTransactionMapper mapper;
    private final UserRepository userRepository;
    private final HarvestSeasonRepository harvestSeasonRepository;
    private final MessagingConversationRepository messagingConversationRepository;
    private final Clock clock;
    private final OutgoingMessagingService outgoing;
    private final TransactionTemplate transactionTemplate;

    public PendingFinancialTransactionService(
            PendingFinancialTransactionRepository repository,
            FinancialCategoryService categoryService,
            FinancialTransactionService transactionService,
            PendingFinancialTransactionMapper mapper,
            UserRepository userRepository,
            HarvestSeasonRepository harvestSeasonRepository,
            MessagingConversationRepository messagingConversationRepository,
            Clock clock,
            OutgoingMessagingService outgoing,
            TransactionTemplate transactionTemplate) {
        this.repository = repository;
        this.categoryService = categoryService;
        this.transactionService = transactionService;
        this.mapper = mapper;
        this.userRepository = userRepository;
        this.harvestSeasonRepository = harvestSeasonRepository;
        this.messagingConversationRepository = messagingConversationRepository;
        this.clock = clock;
        this.outgoing = outgoing;
        this.transactionTemplate = transactionTemplate;
        this.transactionTemplate.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW);
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
        pending.setHarvestSeason(
                resolveHarvestSeason(request.harvestSeasonId(), pending.getFarm().getId()));
        pending.setPaymentMethod(request.paymentMethod());
        pending.setNotes(trimNullable(request.notes()));
        if (category != null) {
            pending.setRawCategoryName(null);
        }
        return mapper.toResponse(repository.save(pending));
    }

    @Transactional
    public PendingFinancialTransactionResponse approve(
            Long id, ApprovePendingFinancialTransactionRequest approval) {
        PendingFinancialTransaction pending = findForUpdate(id);
        ensurePending(pending);
        validateApproval(approval);
        applyApprovalDetails(pending, approval);
        validateFinalTransactionDetails(pending);
        FinancialTransactionRequest request =
                new FinancialTransactionRequest(
                        pending.getDescription(),
                        pending.getAmount(),
                        pending.getType(),
                        approval.status(),
                        pending.getPaymentMethod(),
                        pending.getTransactionDate(),
                        approval.dueDate(),
                        paidAt(approval, pending),
                        pending.getNotes(),
                        pending.getFarm().getId(),
                        pending.getSuggestedCategory() == null
                                ? null
                                : pending.getSuggestedCategory().getId(),
                        pending.getHarvestSeason() == null
                                ? null
                                : pending.getHarvestSeason().getId());
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
        String content = "A movimentação enviada foi rejeitada no Gestão Direta.";
        if (pending.getRejectionReason() != null && !pending.getRejectionReason().isBlank()) {
            content += "\nMotivo: " + pending.getRejectionReason();
        }
        notifyAfterCommit(pending, content);
        return response;
    }

    private void notifyAfterCommit(PendingFinancialTransaction pending, String content) {
        Long conversationId = pending.getMessagingConversation().getId();
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            transactionTemplate.executeWithoutResult(
                                    status ->
                                            messagingConversationRepository
                                                    .findById(conversationId)
                                                    .ifPresent(
                                                            conversation ->
                                                                    outgoing.send(
                                                                            conversation,
                                                                            content)));
                        } catch (RuntimeException exception) {
                            // A notification failure must not affect an already committed decision.
                        }
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
            throw new ConflictException(PROCESSED_MESSAGE);
        }
    }

    private void validateApproval(ApprovePendingFinancialTransactionRequest approval) {
        if (approval.status() != PaymentStatus.PAID && approval.status() != PaymentStatus.PENDING) {
            throw new BusinessException("Only paid or pending approval statuses are allowed");
        }

        if (approval.status() == PaymentStatus.PAID && approval.dueDate() != null) {
            throw new BusinessException("Due date must be null for a paid approval");
        }

        if (approval.status() == PaymentStatus.PENDING && approval.dueDate() == null) {
            throw new BusinessException("Due date is required for a pending approval");
        }

        if (approval.status() == PaymentStatus.PENDING && approval.paidAt() != null) {
            throw new BusinessException("Paid at must be null for a pending approval");
        }
    }

    private void applyApprovalDetails(
            PendingFinancialTransaction pending,
            ApprovePendingFinancialTransactionRequest approval) {
        if (approval.type() != null) {
            pending.setType(approval.type());
        }
        if (approval.amount() != null) {
            pending.setAmount(approval.amount());
        }
        if (approval.description() != null) {
            pending.setDescription(approval.description().trim());
        }
        if (approval.transactionDate() != null) {
            pending.setTransactionDate(approval.transactionDate());
        }

        Long categoryId =
                approval.categoryId() == null
                        ? pending.getSuggestedCategory() == null
                                ? null
                                : pending.getSuggestedCategory().getId()
                        : approval.categoryId();
        FinancialCategory category =
                resolveCategory(categoryId, pending.getFarm().getId(), pending.getType());
        pending.setSuggestedCategory(category);
        if (category != null) {
            pending.setRawCategoryName(null);
        }

        Long harvestSeasonId =
                approval.harvestSeasonId() == null
                        ? pending.getHarvestSeason() == null
                                ? null
                                : pending.getHarvestSeason().getId()
                        : approval.harvestSeasonId();
        pending.setHarvestSeason(resolveHarvestSeason(harvestSeasonId, pending.getFarm().getId()));
        if (approval.paymentMethod() != null) {
            pending.setPaymentMethod(approval.paymentMethod());
        }
        if (approval.notes() != null) {
            pending.setNotes(trimNullable(approval.notes()));
        }
    }

    private void validateFinalTransactionDetails(PendingFinancialTransaction pending) {
        if (pending.getType() == null) {
            throw new BusinessException("Type is required to approve a pending transaction");
        }
        if (pending.getAmount() == null || pending.getAmount().signum() <= 0) {
            throw new BusinessException("Amount is required to approve a pending transaction");
        }
        if (pending.getDescription() == null || pending.getDescription().isBlank()) {
            throw new BusinessException("Description is required to approve a pending transaction");
        }
        if (pending.getTransactionDate() == null) {
            throw new BusinessException(
                    "Transaction date is required to approve a pending transaction");
        }
    }

    private HarvestSeason resolveHarvestSeason(Long harvestSeasonId, Long farmId) {
        if (harvestSeasonId == null) {
            return null;
        }

        HarvestSeason harvestSeason =
                harvestSeasonRepository
                        .findByIdWithRelations(harvestSeasonId)
                        .orElseThrow(
                                () -> new ResourceNotFoundException("Harvest season not found"));
        if (!harvestSeason.getFarm().getId().equals(farmId)) {
            throw new BusinessException(
                    "Harvest season does not belong to pending transaction farm");
        }
        if (harvestSeason.getStatus() == HarvestSeasonStatus.INACTIVE) {
            throw new BusinessException(
                    "Inactive harvest season cannot be linked to pending transaction");
        }
        return harvestSeason;
    }

    private String trimNullable(String value) {
        return value == null ? null : value.trim();
    }

    private java.time.LocalDate paidAt(
            ApprovePendingFinancialTransactionRequest approval,
            PendingFinancialTransaction pending) {
        if (approval.status() != PaymentStatus.PAID) {
            return null;
        }

        return approval.paidAt() == null ? pending.getTransactionDate() : approval.paidAt();
    }

    private User currentUser() {
        return userRepository
                .findById(SecurityUtils.getAuthenticatedUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
