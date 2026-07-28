package br.com.gestaodireta.messaging.service;

import br.com.gestaodireta.ai.service.FinancialTransactionExtractionService;
import br.com.gestaodireta.ai.service.dto.FinancialTransactionExtractionResult;
import br.com.gestaodireta.farm.enumeration.FarmUserRole;
import br.com.gestaodireta.farm.repository.FarmUserRepository;
import br.com.gestaodireta.financial.entity.FinancialCategory;
import br.com.gestaodireta.financial.entity.PendingFinancialTransaction;
import br.com.gestaodireta.financial.enumeration.*;
import br.com.gestaodireta.financial.repository.FinancialCategoryRepository;
import br.com.gestaodireta.financial.repository.PendingFinancialTransactionRepository;
import br.com.gestaodireta.messaging.domain.*;
import br.com.gestaodireta.messaging.enumeration.*;
import br.com.gestaodireta.user.enumeration.UserContactStatus;
import br.com.gestaodireta.user.enumeration.UserStatus;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class TelegramFinancialExtractionProcessor {
    private static final Logger LOGGER =
            LoggerFactory.getLogger(TelegramFinancialExtractionProcessor.class);
    private static final String INSUFFICIENT_MESSAGE =
            "Não identifiquei uma movimentação financeira completa.\n\nEnvie uma mensagem como:\n“Gastei R$ 250,00 com combustível hoje.”\nou\n“Recebi R$ 1.000,00 pela venda de milho.”";
    private static final String LOW_CONFIDENCE_MESSAGE =
            "Não consegui interpretar a movimentação com segurança.";
    private static final String AI_FAILURE_MESSAGE =
            "Não foi possível interpretar sua mensagem agora.";

    private final FinancialTransactionExtractionService extractionService;
    private final PendingFinancialTransactionRepository pendingRepository;
    private final FinancialCategoryRepository categoryRepository;
    private final FarmUserRepository farmUsers;
    private final OutgoingMessagingService outgoing;
    private final TelegramFinancialMessageEligibilityValidator eligibilityValidator;
    private final FinancialTransactionExtractionResultValidator resultValidator;
    private final Clock clock;

    public TelegramFinancialExtractionProcessor(
            FinancialTransactionExtractionService extractionService,
            PendingFinancialTransactionRepository pendingRepository,
            FinancialCategoryRepository categoryRepository,
            FarmUserRepository farmUsers,
            OutgoingMessagingService outgoing,
            TelegramFinancialMessageEligibilityValidator eligibilityValidator,
            FinancialTransactionExtractionResultValidator resultValidator,
            Clock clock) {
        this.extractionService = extractionService;
        this.pendingRepository = pendingRepository;
        this.categoryRepository = categoryRepository;
        this.farmUsers = farmUsers;
        this.outgoing = outgoing;
        this.eligibilityValidator = eligibilityValidator;
        this.resultValidator = resultValidator;
        this.clock = clock;
    }

    public void process(
            MessagingAccount account,
            MessagingConversation conversation,
            MessagingMessage message) {
        if (!eligible(account, conversation, message)
                || pendingRepository.findBySourceMessageId(message.getId()).isPresent()) return;
        if (!eligibilityValidator.isEligible(message.getContent())) {
            LOGGER.info(
                    "financial extraction rejected: stage=ELIGIBILITY reason=INSUFFICIENT_CONTEXT messageId={}",
                    message.getId());
            outgoing.send(conversation, INSUFFICIENT_MESSAGE);
            return;
        }
        if (!extractionService.isEnabled()) {
            outgoing.send(conversation, "O registro inteligente está indisponível no momento.");
            return;
        }
        try {
            List<FinancialCategory> categories =
                    categoryRepository
                            .findByFarmId(
                                    conversation.getFarm().getId(), false, PageRequest.of(0, 100))
                            .getContent();
            FinancialTransactionExtractionResult result =
                    extractionService.extract(
                            message.getContent(), conversation.getFarm().getName(), categories);
            LOGGER.info(
                    "financial extraction result: messageId={} financial={} type={} amount={} confidence={} missingFields={}",
                    message.getId(),
                    result.isFinancialTransaction(),
                    result.type(),
                    result.amount(),
                    result.confidence(),
                    result.missingFields());
            if (extractionService.isDiagnosticOnly()) {
                LOGGER.info(
                        "financial extraction diagnostic-only: stage=RESULT_RECEIVED messageId={}",
                        message.getId());
                return;
            }
            FinancialTransactionExtractionResultValidator.ValidationResult validation =
                    resultValidator.validate(message.getContent(), result);
            if (!validation.valid()) {
                LOGGER.info(
                        "financial extraction rejected: stage=RESULT_VALIDATION reason={} messageId={}",
                        validation.reason(),
                        message.getId());
                outgoing.send(
                        conversation,
                        validation.reason()
                                        == FinancialTransactionExtractionResultValidator
                                                .RejectionReason.LOW_CONFIDENCE
                                ? LOW_CONFIDENCE_MESSAGE
                                : INSUFFICIENT_MESSAGE);
                return;
            }
            PendingFinancialTransaction pending = new PendingFinancialTransaction();
            pending.setFarm(conversation.getFarm());
            pending.setRequestedByUser(account.getUserContact().getUser());
            pending.setMessagingAccount(account);
            pending.setMessagingConversation(conversation);
            pending.setSourceMessage(message);
            pending.setSourceChannel(PendingTransactionSource.TELEGRAM);
            pending.setType(result.type());
            pending.setAmount(result.amount());
            pending.setTransactionDate(result.transactionDate());
            pending.setDescription(result.description().trim());
            pending.setSuggestedCategory(resolveCategory(categories, result));
            pending.setRawCategoryName(
                    pending.getSuggestedCategory() == null ? normalizedCategoryName(result) : null);
            pending.setStatus(PendingFinancialTransactionStatus.PENDING_REVIEW);
            pending.setConfidence(result.confidence());
            pending.setAiModel(extractionService.model());
            pending.setAiProcessedAt(LocalDateTime.now(clock));
            pendingRepository.save(pending);
            LOGGER.info(
                    "financial extraction persisted: stage=PENDING_CREATED messageId={} amount={} type={}",
                    message.getId(),
                    pending.getAmount(),
                    pending.getType());
            outgoing.send(conversation, confirmation(pending));
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "financial extraction rejected: stage=AI_FAILURE reason={} messageId={}",
                    exception.getClass().getSimpleName(),
                    message.getId());
            outgoing.send(conversation, AI_FAILURE_MESSAGE);
        }
    }

    private boolean eligible(
            MessagingAccount account,
            MessagingConversation conversation,
            MessagingMessage message) {
        if (message.getMessageType() != MessagingMessageType.TEXT
                || message.getContent() == null
                || message.getContent().isBlank()
                || message.getContent().trim().startsWith("/")) return false;
        if (account.getStatus() != MessagingAccountStatus.ACTIVE
                || account.getUserContact() == null
                || account.getUserContact().getStatus() != UserContactStatus.ACTIVE
                || account.getUserContact().getUser().getStatus() != UserStatus.ACTIVE)
            return false;
        if (conversation.getStatus() != MessagingConversationStatus.ACTIVE
                || conversation.getFarm() == null) return false;
        return farmUsers
                .findRoleByFarmIdAndUserId(
                        conversation.getFarm().getId(), account.getUserContact().getUser().getId())
                .map(role -> role == FarmUserRole.PRODUCER || role == FarmUserRole.EMPLOYEE)
                .orElse(false);
    }

    private FinancialCategory resolveCategory(
            List<FinancialCategory> categories, FinancialTransactionExtractionResult result) {
        if (result.categoryName() == null) return null;
        String normalized = result.categoryName().trim().toLowerCase(Locale.ROOT);
        return categories.stream()
                .filter(category -> category.getType() == result.type())
                .filter(
                        category ->
                                category.getName()
                                        .trim()
                                        .toLowerCase(Locale.ROOT)
                                        .equals(normalized))
                .findFirst()
                .orElse(null);
    }

    private String normalizedCategoryName(FinancialTransactionExtractionResult result) {
        if (result.categoryName() == null || result.categoryName().isBlank()) {
            return null;
        }
        return result.categoryName().trim();
    }

    private String confirmation(PendingFinancialTransaction pending) {
        String type = pending.getType() == TransactionType.INCOME ? "Receita" : "Despesa";
        return "Identifiquei uma movimentação para revisão:\n\n"
                + type
                + " de R$ "
                + pending.getAmount().toPlainString().replace('.', ',')
                + "\nDescrição: "
                + pending.getDescription()
                + "\nData: "
                + pending.getTransactionDate()
                        .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                + "\nFazenda: "
                + pending.getFarm().getName()
                + "\n\nAcesse o Gestão Direta para revisar e aprovar.\n\nEssa movimentação ainda não foi incluída nos cálculos financeiros.";
    }
}
