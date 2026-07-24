package br.com.gestaodireta.messaging.service;

import br.com.gestaodireta.messaging.domain.*;
import br.com.gestaodireta.messaging.dto.*;
import br.com.gestaodireta.messaging.enumeration.*;
import br.com.gestaodireta.messaging.repository.*;
import br.com.gestaodireta.messaging.telegram.client.*;
import br.com.gestaodireta.messaging.telegram.config.TelegramProperties;
import br.com.gestaodireta.shared.exception.*;
import br.com.gestaodireta.shared.pagination.PaginationParams;
import br.com.gestaodireta.shared.response.PageResponse;
import java.time.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MessagingAdministrationService {
    private final MessagingAccountRepository accounts;
    private final MessagingMessageRepository messages;
    private final TelegramBotClient client;
    private final TelegramProperties properties;
    private final MessagingConversationService conversations;
    private final MessagingConversationRepository conversationsRepository;
    private final Clock clock;

    public MessagingAdministrationService(
            MessagingAccountRepository accounts,
            MessagingMessageRepository messages,
            TelegramBotClient client,
            TelegramProperties properties,
            MessagingConversationService conversations,
            MessagingConversationRepository conversationsRepository,
            Clock clock) {
        this.accounts = accounts;
        this.messages = messages;
        this.client = client;
        this.properties = properties;
        this.conversations = conversations;
        this.conversationsRepository = conversationsRepository;
        this.clock = clock;
    }

    @Transactional
    public MessagingAccountResponse updateStatus(
            Long id, MessagingAccountStatusUpdateRequest request) {
        MessagingAccount account = lockedAccount(id);
        MessagingAccountStatus current = account.getStatus();
        MessagingAccountStatus target = request.status();
        boolean allowed =
                (current == MessagingAccountStatus.ACTIVE
                                && (target == MessagingAccountStatus.INACTIVE
                                        || target == MessagingAccountStatus.BLOCKED))
                        || (current == MessagingAccountStatus.INACTIVE
                                && target == MessagingAccountStatus.ACTIVE
                                && account.getUserContact() != null
                                && account.getVerifiedAt() != null)
                        || (current == MessagingAccountStatus.BLOCKED
                                && target == MessagingAccountStatus.INACTIVE);
        if (!allowed)
            throw new BusinessException("Messaging account status transition is not allowed");
        account.setStatus(target);
        if (target == MessagingAccountStatus.INACTIVE || target == MessagingAccountStatus.BLOCKED) {
            conversationsRepository
                    .findWithLockByMessagingAccountIdAndStatusIn(
                            account.getId(),
                            java.util.List.of(
                                    MessagingConversationStatus.ACTIVE,
                                    MessagingConversationStatus.WAITING_FARM_SELECTION))
                    .forEach(this::cancelConversation);
        }
        return toResponse(accounts.save(account));
    }

    @Transactional(readOnly = true)
    public PageResponse<MessagingAccountResponse> accounts(PaginationParams params) {
        return PageResponse.from(accounts.findAll(params.toPageable()).map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public PageResponse<MessagingMessageResponse> messages(
            MessagingMessageFilterRequest filter, PaginationParams params) {
        return PageResponse.from(
                messages.findAll(
                                MessagingMessageSpecifications.filtered(filter),
                                params.toPageable())
                        .map(this::toResponse));
    }

    public MessagingMessageResponse send(SendTelegramMessageRequest request) {
        MessagingAccount account = account(request.messagingAccountId());
        if (account.getChannel() != MessagingChannel.TELEGRAM
                || account.getStatus() != MessagingAccountStatus.ACTIVE
                || account.getExternalChatId() == null
                || account.getExternalChatId().isBlank())
            throw new BusinessException("Messaging account is not active for Telegram sending");
        MessagingMessage message = createOutbound(account, request.content());
        try {
            TelegramSendMessageResult result =
                    client.sendMessage(account.getExternalChatId(), request.content());
            return markSent(message.getId(), result.messageId());
        } catch (RuntimeException exception) {
            return markFailed(message.getId(), "Telegram message could not be sent");
        }
    }

    @Transactional
    public MessagingMessage createOutbound(MessagingAccount account, String content) {
        MessagingMessage message = new MessagingMessage();
        message.setMessagingConversation(conversations.active(account, LocalDateTime.now(clock)));
        message.setChannel(MessagingChannel.TELEGRAM);
        message.setExternalUserId(account.getExternalUserId());
        message.setExternalChatId(account.getExternalChatId());
        message.setDirection(MessagingDirection.OUTBOUND);
        message.setMessageType(MessagingMessageType.TEXT);
        message.setContent(content.trim());
        message.setStatus(MessagingMessageStatus.PENDING);
        return messages.save(message);
    }

    @Transactional
    public MessagingMessageResponse markSent(Long id, String providerMessageId) {
        MessagingMessage message = message(id);
        message.setProviderMessageId(providerMessageId);
        message.setStatus(MessagingMessageStatus.SENT);
        message.setSentAt(LocalDateTime.now(clock));
        return toResponse(messages.save(message));
    }

    @Transactional
    public MessagingMessageResponse markFailed(Long id, String error) {
        MessagingMessage message = message(id);
        message.setStatus(MessagingMessageStatus.FAILED);
        message.setErrorMessage(error);
        return toResponse(messages.save(message));
    }

    @Transactional(readOnly = true)
    public PageResponse<MessagingConversationResponse> conversations(
            MessagingConversationFilterRequest filter, PaginationParams params) {
        return PageResponse.from(
                conversationsRepository
                        .findFiltered(
                                filter.messagingAccountId(),
                                filter.userId(),
                                filter.farmId(),
                                filter.status(),
                                filter.startDate(),
                                filter.endDate(),
                                params.toPageable())
                        .map(this::toConversationResponse));
    }

    @Transactional(readOnly = true)
    public MessagingConversationResponse conversation(Long id) {
        return toConversationResponse(
                conversationsRepository
                        .findById(id)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Messaging conversation not found")));
    }

    public TelegramStatus status() {
        if (!properties.isEnabled()) return new TelegramStatus(false, false, null, null);
        TelegramBotIdentity identity = client.getMe();
        return new TelegramStatus(true, true, identity.id(), identity.username());
    }

    private void cancelConversation(MessagingConversation conversation) {
        conversation.setStatus(MessagingConversationStatus.CANCELED);
        conversation.setCurrentStep(MessagingConversationStep.NONE);
        conversation.setFarm(null);
    }

    private MessagingAccount lockedAccount(Long id) {
        return accounts.findWithLockById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Messaging account not found"));
    }

    private MessagingAccount account(Long id) {
        return accounts.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Messaging account not found"));
    }

    private MessagingMessage message(Long id) {
        return messages.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Messaging message not found"));
    }

    private MessagingAccountResponse toResponse(MessagingAccount a) {
        return new MessagingAccountResponse(
                a.getId(),
                a.getChannel(),
                a.getExternalUserId(),
                a.getExternalChatId(),
                a.getUsername(),
                a.getDisplayName(),
                a.getStatus(),
                a.getLastInteractionAt(),
                a.getCreatedAt());
    }

    private MessagingConversationResponse toConversationResponse(MessagingConversation c) {
        MessagingAccount a = c.getMessagingAccount();
        var contact = a.getUserContact();
        var user = contact == null ? null : contact.getUser();
        var farm = c.getFarm();
        return new MessagingConversationResponse(
                c.getId(),
                a.getId(),
                a.getChannel(),
                a.getDisplayName(),
                a.getUsername(),
                user == null ? null : user.getId(),
                user == null ? null : user.getName(),
                farm == null ? null : farm.getId(),
                farm == null ? null : farm.getName(),
                c.getStatus(),
                c.getCurrentStep(),
                c.getLastInteractionAt(),
                c.getExpiresAt(),
                c.getCreatedAt(),
                c.getUpdatedAt());
    }

    private MessagingMessageResponse toResponse(MessagingMessage m) {
        return new MessagingMessageResponse(
                m.getId(),
                m.getMessagingConversation().getMessagingAccount().getId(),
                m.getMessagingConversation().getId(),
                m.getChannel(),
                m.getDirection(),
                m.getMessageType(),
                m.getContent(),
                m.getStatus(),
                m.getProviderMessageId(),
                m.getReceivedAt(),
                m.getSentAt(),
                m.getProcessedAt(),
                m.getErrorMessage(),
                m.getCreatedAt());
    }

    public record TelegramStatus(
            boolean enabled, boolean reachable, String botId, String botUsername) {}
}
