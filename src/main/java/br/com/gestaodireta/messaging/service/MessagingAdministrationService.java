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

    public MessagingAdministrationService(
            MessagingAccountRepository accounts,
            MessagingMessageRepository messages,
            TelegramBotClient client,
            TelegramProperties properties,
            MessagingConversationService conversations) {
        this.accounts = accounts;
        this.messages = messages;
        this.client = client;
        this.properties = properties;
        this.conversations = conversations;
    }

    @Transactional
    public MessagingAccountResponse updateStatus(
            Long id, MessagingAccountStatusUpdateRequest request) {
        MessagingAccount account = account(id);
        account.setStatus(request.status());
        if (request.status() == MessagingAccountStatus.ACTIVE && account.getVerifiedAt() == null)
            account.setVerifiedAt(LocalDateTime.now(ZoneOffset.UTC));
        return toResponse(accounts.save(account));
    }

    @Transactional(readOnly = true)
    public PageResponse<MessagingAccountResponse> accounts(PaginationParams params) {
        return PageResponse.from(accounts.findAll(params.toPageable()).map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public PageResponse<MessagingMessageResponse> messages(PaginationParams params) {
        return PageResponse.from(messages.findAll(params.toPageable()).map(this::toResponse));
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
        message.setMessagingConversation(
                conversations.active(account, LocalDateTime.now(ZoneOffset.UTC)));
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
        message.setSentAt(LocalDateTime.now(ZoneOffset.UTC));
        return toResponse(messages.save(message));
    }

    @Transactional
    public MessagingMessageResponse markFailed(Long id, String error) {
        MessagingMessage message = message(id);
        message.setStatus(MessagingMessageStatus.FAILED);
        message.setErrorMessage(error);
        return toResponse(messages.save(message));
    }

    public TelegramStatus status() {
        if (!properties.isEnabled()) return new TelegramStatus(false, false, null, null);
        TelegramBotIdentity identity = client.getMe();
        return new TelegramStatus(true, true, identity.id(), identity.username());
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

    private MessagingMessageResponse toResponse(MessagingMessage m) {
        return new MessagingMessageResponse(
                m.getId(),
                m.getMessagingConversation().getMessagingAccount().getId(),
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
