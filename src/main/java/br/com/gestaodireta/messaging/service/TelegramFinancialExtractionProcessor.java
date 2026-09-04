package br.com.gestaodireta.messaging.service;

import br.com.gestaodireta.ai.service.AiModelNotAvailableException;
import br.com.gestaodireta.ai.service.AiParsingException;
import br.com.gestaodireta.ai.service.AiProviderException;
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
import org.springframework.web.client.ResourceAccessException;

@Service
public class TelegramFinancialExtractionProcessor {
    private static final Logger LOGGER =
            LoggerFactory.getLogger(TelegramFinancialExtractionProcessor.class);
    private static final String INSUFFICIENT_MESSAGE =
            "Não identifiquei uma movimentação financeira completa.\n\nEnvie uma mensagem como:\n“Gastei R$ 250,00 com combustível hoje.”\nou\n“Recebi R$ 1.000,00 pela venda de milho.”";
    private static final String LOW_CONFIDENCE_MESSAGE =
            "Não consegui interpretar a movimentação com segurança.";
    private static final String AI_UNAVAILABLE_MESSAGE =
            "O registro inteligente está indisponível no momento. Tente novamente em instantes.";
    private static final String AI_TIMEOUT_MESSAGE =
            "O registro inteligente demorou para responder. Tente novamente em instantes.";
    private static final String AI_INVALID_RESPONSE_MESSAGE =
            "Não consegui interpretar a resposta do registro inteligente. Tente reenviar a movimentação.";

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
        LOGGER.info(
                "financial extraction started: messageId={} chatId={} userId={} farmId={}",
                message.getId(),
                message.getExternalChatId(),
                (account.getUserContact() == null
                        ? null
                        : account.getUserContact().getUser().getId()),
                farmId(conversation));
        if (!eligible(account, conversation, message)
                || pendingRepository.findBySourceMessageId(message.getId()).isPresent()) return;
        if (!eligibilityValidator.isEligible(message.getContent())) {
            LOGGER.info(
                    "financial extraction rejected: stage=ELIGIBILITY reason=INSUFFICIENT_CONTEXT messageId={}",
                    message.getId());
            outgoing.send(conversation, INSUFFICIENT_MESSAGE);
            return;
        }
        LOGGER.info(
                "financial extraction eligibility: messageId={} eligible=true reason=SUFFICIENT_CONTEXT",
                message.getId());
        if (!extractionService.isEnabled()) {
            outgoing.send(conversation, AI_UNAVAILABLE_MESSAGE);
            return;
        }
        try {
            List<FinancialCategory> categories =
                    categoryRepository
                            .findByFarmId(
                                    conversation.getFarm().getId(), false, PageRequest.of(0, 100))
                            .getContent();
            LOGGER.info(
                    "financial extraction ai request: messageId={} provider={} model={}",
                    message.getId(),
                    extractionService.provider(),
                    extractionService.model());
            FinancialTransactionExtractionResult result =
                    extractionService.extract(
                            message.getContent(), conversation.getFarm().getName(), categories);
            LOGGER.info(
                    "financial extraction result: messageId={} financial={} fieldsPresent={} missingFields={}",
                    message.getId(),
                    result.isFinancialTransaction(),
                    presentRequiredFields(result),
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
                        "financial extraction validation: messageId={} valid=false reason={} missingFields={} requiredFields={} presentFields={}",
                        message.getId(),
                        validation.reason(),
                        missingRequiredFields(result),
                        "[type, amount, description]",
                        presentRequiredFields(result));
                outgoing.send(
                        conversation,
                        validation.reason()
                                        == FinancialTransactionExtractionResultValidator
                                                .RejectionReason.LOW_CONFIDENCE
                                ? LOW_CONFIDENCE_MESSAGE
                                : INSUFFICIENT_MESSAGE);
                return;
            }
            LOGGER.info(
                    "financial extraction validation: messageId={} valid=true", message.getId());
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
            pending.setDescription(result.description());
            pending.setMissingFields(String.join(",", missingFields(result)));
            pending.setSuggestedCategory(resolveCategory(categories, result));
            pending.setRawCategoryName(
                    pending.getSuggestedCategory() == null ? normalizedCategoryName(result) : null);
            pending.setStatus(PendingFinancialTransactionStatus.PENDING_REVIEW);
            pending.setConfidence(result.confidence());
            pending.setAiModel(extractionService.model());
            pending.setAiProcessedAt(LocalDateTime.now(clock));
            try {
                pendingRepository.save(pending);
            } catch (RuntimeException exception) {
                rejectProcessing(
                        message,
                        conversation,
                        "PENDING_CREATION",
                        exception,
                        AI_UNAVAILABLE_MESSAGE);
                return;
            }
            LOGGER.info(
                    "financial extraction pending movement: messageId={} status={} type={} amount={} farmId={}",
                    message.getId(),
                    pending.getStatus(),
                    pending.getType(),
                    pending.getAmount(),
                    pending.getFarm().getId());
            sendPendingConfirmation(conversation, pending, message.getId());
        } catch (AiParsingException exception) {
            rejectAi(
                    message,
                    conversation,
                    "AI_INVALID_RESPONSE",
                    exception,
                    AI_INVALID_RESPONSE_MESSAGE);
        } catch (AiModelNotAvailableException exception) {
            rejectAi(
                    message,
                    conversation,
                    exception.getReason().name(),
                    exception,
                    AI_UNAVAILABLE_MESSAGE);
        } catch (AiProviderException exception) {
            String reason =
                    exception.getReason() == AiProviderException.Reason.TIMEOUT
                            ? "AI_TIMEOUT"
                            : exception.getReason().name();
            String response =
                    exception.getReason() == AiProviderException.Reason.TIMEOUT
                            ? AI_TIMEOUT_MESSAGE
                            : AI_UNAVAILABLE_MESSAGE;
            rejectAi(message, conversation, reason, exception, response);
        } catch (ResourceAccessException exception) {
            String reason =
                    exception.getCause() instanceof java.net.SocketTimeoutException
                            ? "AI_TIMEOUT"
                            : "AI_UNAVAILABLE";
            String response =
                    reason.equals("AI_TIMEOUT") ? AI_TIMEOUT_MESSAGE : AI_UNAVAILABLE_MESSAGE;
            rejectAi(message, conversation, reason, exception, response);
        } catch (RuntimeException exception) {
            rejectAi(
                    message,
                    conversation,
                    "AI_EXTRACTION_ERROR",
                    exception,
                    AI_UNAVAILABLE_MESSAGE);
        }
    }

    private Long userId(MessagingAccount account) {
        if (account == null
                || account.getUserContact() == null
                || account.getUserContact().getUser() == null) {
            return null;
        }
        return account.getUserContact().getUser().getId();
    }

    private Long farmId(MessagingConversation conversation) {
        if (conversation == null || conversation.getFarm() == null) {
            return null;
        }
        return conversation.getFarm().getId();
    }

    private void rejectAi(
            MessagingMessage message,
            MessagingConversation conversation,
            String reason,
            RuntimeException exception,
            String response) {
        rejectProcessing(message, conversation, "AI_EXTRACTION", reason, exception, response);
    }

    private void rejectProcessing(
            MessagingMessage message,
            MessagingConversation conversation,
            String stage,
            RuntimeException exception,
            String response) {
        rejectProcessing(message, conversation, stage, stage, exception, response);
    }

    private void rejectProcessing(
            MessagingMessage message,
            MessagingConversation conversation,
            String stage,
            String reason,
            RuntimeException exception,
            String response) {
        if (exception instanceof AiProviderException providerException) {
            LOGGER.warn(
                    "financial extraction rejected: stage={} reason={} provider={} model={} messageId={} httpStatus={} providerStatus={} exception={}",
                    stage,
                    reason,
                    providerException.getProvider(),
                    providerException.getModel(),
                    message.getId(),
                    providerException.getStatusCode(),
                    providerException.getProviderStatus(),
                    exception.getClass().getSimpleName());
        } else {
            LOGGER.error(
                    "financial extraction rejected unexpectedly: stage={} reason={} messageId={}",
                    stage,
                    reason,
                    message.getId(),
                    exception);
        }
        outgoing.send(conversation, response);
    }

    private String presentRequiredFields(FinancialTransactionExtractionResult result) {
        java.util.List<String> fields = new java.util.ArrayList<>();
        if (result.type() != null) {
            fields.add("type");
        }
        if (result.amount() != null) {
            fields.add("amount");
        }
        if (result.description() != null && !result.description().isBlank()) {
            fields.add("description");
        }
        if (result.transactionDate() != null) {
            fields.add("transactionDate");
        }
        if (result.confidence() != null) {
            fields.add("confidence");
        }
        return fields.toString();
    }

    private String missingRequiredFields(FinancialTransactionExtractionResult result) {
        java.util.List<String> fields =
                new java.util.ArrayList<>(java.util.List.of("type", "amount", "description"));
        if (result.type() != null) {
            fields.remove("type");
        }
        if (result.amount() != null) {
            fields.remove("amount");
        }
        if (result.description() != null && !result.description().isBlank()) {
            fields.remove("description");
        }
        return fields.toString();
    }

    private boolean eligible(
            MessagingAccount account,
            MessagingConversation conversation,
            MessagingMessage message) {
        if ((message.getMessageType() != MessagingMessageType.TEXT
                        && message.getMessageType() != MessagingMessageType.VOICE)
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

    private java.util.List<String> missingFields(FinancialTransactionExtractionResult result) {
        java.util.Set<String> fields = new java.util.LinkedHashSet<>();
        if (result.missingFields() != null) {
            fields.addAll(result.missingFields());
        }
        if (result.type() == null) {
            fields.add("type");
        }
        if (result.amount() == null) {
            fields.add("amount");
        }
        if (result.description() == null || result.description().isBlank()) {
            fields.add("description");
        }
        if (result.transactionDate() == null) {
            fields.add("transactionDate");
        }
        if (result.categoryName() == null || result.categoryName().isBlank()) {
            fields.add("category");
        }
        return java.util.List.copyOf(fields);
    }

    private void sendPendingConfirmation(
            MessagingConversation conversation,
            PendingFinancialTransaction pending,
            Long messageId) {
        try {
            outgoing.send(conversation, confirmation(pending));
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "financial extraction notification failed: stage=TELEGRAM_SEND messageId={} exception={}",
                    messageId,
                    exception.getClass().getSimpleName());
        }
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
                + formattedTransactionDate(pending.getTransactionDate())
                + "\nFazenda: "
                + pending.getFarm().getName()
                + "\n\nAcesse o Gestão Direta para revisar e aprovar.\n\nEssa movimentação ainda não foi incluída nos cálculos financeiros.";
    }

    private String formattedTransactionDate(java.time.LocalDate transactionDate) {
        if (transactionDate == null) {
            return "A revisar";
        }
        return transactionDate.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }
}
