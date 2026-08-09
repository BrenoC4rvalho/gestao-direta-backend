package br.com.gestaodireta.messaging.service;

import br.com.gestaodireta.messaging.domain.MessagingAccount;
import br.com.gestaodireta.messaging.domain.MessagingConversation;
import br.com.gestaodireta.messaging.domain.MessagingMessage;
import br.com.gestaodireta.messaging.enumeration.MessagingChannel;
import br.com.gestaodireta.messaging.enumeration.MessagingDirection;
import br.com.gestaodireta.messaging.enumeration.MessagingMessageStatus;
import br.com.gestaodireta.messaging.enumeration.MessagingMessageType;
import br.com.gestaodireta.messaging.repository.MessagingMessageRepository;
import br.com.gestaodireta.messaging.telegram.client.TelegramBotClient;
import br.com.gestaodireta.messaging.telegram.client.TelegramSendMessageResult;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutgoingMessagingService {

    private final MessagingMessageRepository messages;
    private final TelegramBotClient client;

    public OutgoingMessagingService(MessagingMessageRepository messages, TelegramBotClient client) {
        this.messages = messages;
        this.client = client;
    }

    @Transactional
    public boolean send(MessagingConversation conversation, String content) {
        return send(conversation, content, content);
    }

    @Transactional
    public boolean send(
            MessagingConversation conversation, String content, String persistedContent) {
        MessagingMessage message = create(conversation, persistedContent);
        try {
            TelegramSendMessageResult result =
                    client.sendMessage(
                            conversation.getMessagingAccount().getExternalChatId(), content);
            message.setProviderMessageId(result.messageId());
            message.setStatus(MessagingMessageStatus.SENT);
            message.setSentAt(LocalDateTime.now(ZoneOffset.UTC));
            messages.save(message);
            return true;
        } catch (RuntimeException exception) {
            message.setStatus(MessagingMessageStatus.FAILED);
            message.setErrorMessage("Telegram message could not be sent");
            messages.save(message);
            return false;
        }
    }

    @Transactional
    public MessagingMessage create(MessagingConversation conversation, String content) {
        MessagingAccount account = conversation.getMessagingAccount();
        MessagingMessage message = new MessagingMessage();
        message.setMessagingConversation(conversation);
        message.setChannel(MessagingChannel.TELEGRAM);
        message.setExternalUserId(account.getExternalUserId());
        message.setExternalChatId(account.getExternalChatId());
        message.setDirection(MessagingDirection.OUTBOUND);
        message.setMessageType(MessagingMessageType.TEXT);
        message.setContent(content.trim());
        message.setStatus(MessagingMessageStatus.PENDING);
        return messages.saveAndFlush(message);
    }

    @Transactional
    public void markSent(Long messageId, String providerMessageId) {
        MessagingMessage message = messages.getReferenceById(messageId);
        message.setProviderMessageId(providerMessageId);
        message.setStatus(MessagingMessageStatus.SENT);
        message.setSentAt(LocalDateTime.now(ZoneOffset.UTC));
    }

    @Transactional
    public void markFailed(Long messageId) {
        MessagingMessage message = messages.getReferenceById(messageId);
        message.setStatus(MessagingMessageStatus.FAILED);
        message.setErrorMessage("Telegram message could not be sent");
    }
}
