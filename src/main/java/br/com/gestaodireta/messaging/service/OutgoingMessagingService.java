package br.com.gestaodireta.messaging.service;

import br.com.gestaodireta.messaging.domain.*;
import br.com.gestaodireta.messaging.enumeration.*;
import br.com.gestaodireta.messaging.repository.MessagingMessageRepository;
import br.com.gestaodireta.messaging.telegram.client.*;
import java.time.*;
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

    public void send(MessagingConversation conversation, String content) {
        MessagingMessage message = create(conversation, content);
        try {
            TelegramSendMessageResult result =
                    client.sendMessage(
                            conversation.getMessagingAccount().getExternalChatId(), content);
            markSent(message.getId(), result.messageId());
        } catch (RuntimeException exception) {
            markFailed(message.getId());
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
        return messages.save(message);
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
