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
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class TelegramFinancialExtractionProcessor {
    private final FinancialTransactionExtractionService extractionService;
    private final PendingFinancialTransactionRepository pendingRepository;
    private final FinancialCategoryRepository categoryRepository;
    private final FarmUserRepository farmUsers;
    private final OutgoingMessagingService outgoing;
    private final Clock clock;

    public TelegramFinancialExtractionProcessor(
            FinancialTransactionExtractionService extractionService,
            PendingFinancialTransactionRepository pendingRepository,
            FinancialCategoryRepository categoryRepository,
            FarmUserRepository farmUsers,
            OutgoingMessagingService outgoing,
            Clock clock) {
        this.extractionService = extractionService;
        this.pendingRepository = pendingRepository;
        this.categoryRepository = categoryRepository;
        this.farmUsers = farmUsers;
        this.outgoing = outgoing;
        this.clock = clock;
    }

    public void process(
            MessagingAccount account,
            MessagingConversation conversation,
            MessagingMessage message) {
        if (!eligible(account, conversation, message)
                || pendingRepository.findBySourceMessageId(message.getId()).isPresent()) return;
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
            if (!result.isFinancialTransaction()) {
                outgoing.send(
                        conversation,
                        "Não identifiquei uma movimentação financeira.\n\nEnvie uma mensagem como:\n“Gastei R$ 250,00 com combustível hoje.”");
                return;
            }
            if (!result.missingFields().isEmpty() || !valid(result)) {
                outgoing.send(
                        conversation,
                        "Não consegui identificar todos os dados da movimentação.\n\nEnvie uma mensagem como:\n“Gastei R$ 250,00 com adubo hoje.”");
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
            pending.setDescription(result.description());
            pending.setRawCategoryName(result.categoryName());
            pending.setSuggestedCategory(resolveCategory(categories, result));
            if (pending.getSuggestedCategory() != null) pending.setRawCategoryName(null);
            pending.setStatus(PendingFinancialTransactionStatus.PENDING_REVIEW);
            pending.setConfidence(result.confidence());
            pending.setAiModel(extractionService.model());
            pending.setAiProcessedAt(LocalDateTime.now(clock));
            pendingRepository.save(pending);
            outgoing.send(conversation, confirmation(pending));
        } catch (RuntimeException exception) {
            outgoing.send(
                    conversation,
                    "Não foi possível interpretar sua mensagem agora. Tente novamente em alguns instantes.");
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

    private boolean valid(FinancialTransactionExtractionResult result) {
        return result.amount() != null
                && result.amount().compareTo(BigDecimal.ZERO) > 0
                && result.type() != null
                && result.transactionDate() != null
                && result.description() != null
                && !result.description().isBlank();
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
