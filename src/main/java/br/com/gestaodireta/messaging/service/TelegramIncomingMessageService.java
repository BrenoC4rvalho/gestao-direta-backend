package br.com.gestaodireta.messaging.service;

import br.com.gestaodireta.messaging.domain.*;
import br.com.gestaodireta.messaging.dto.IncomingMessagingMessage;
import br.com.gestaodireta.messaging.enumeration.*;
import br.com.gestaodireta.messaging.repository.*;
import java.time.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TelegramIncomingMessageService {
    private final MessagingAccountRepository accounts;
    private final MessagingMessageRepository messages;
    private final MessagingConversationService conversations;
    private final TelegramCommandDispatcher dispatcher;
    private final Clock clock;

    public TelegramIncomingMessageService(
            MessagingAccountRepository accounts,
            MessagingMessageRepository messages,
            MessagingConversationService conversations,
            TelegramCommandDispatcher dispatcher,
            Clock clock) {
        this.accounts = accounts;
        this.messages = messages;
        this.conversations = conversations;
        this.dispatcher = dispatcher;
        this.clock = clock;
    }

    @Transactional
    public void receive(IncomingMessagingMessage incoming) {
        if (messages.existsByChannelAndProviderUpdateId(
                incoming.channel(), incoming.providerUpdateId())) return;
        LocalDateTime time = LocalDateTime.ofInstant(incoming.receivedAt(), clock.getZone());
        MessagingAccount account =
                accounts.findWithLockByChannelAndExternalUserIdAndExternalChatId(
                                incoming.channel(),
                                incoming.externalUserId(),
                                incoming.externalChatId())
                        .orElseGet(() -> createAccount(incoming));
        account.setUsername(incoming.username());
        account.setDisplayName(incoming.displayName());
        account.setLastInteractionAt(time);
        account = accounts.save(account);
        MessagingMessage message = new MessagingMessage();
        message.setMessagingConversation(conversations.forIncoming(account, time));
        message.setChannel(incoming.channel());
        message.setProviderUpdateId(incoming.providerUpdateId());
        message.setProviderMessageId(incoming.providerMessageId());
        message.setExternalUserId(incoming.externalUserId());
        message.setExternalChatId(incoming.externalChatId());
        message.setDirection(MessagingDirection.INBOUND);
        message.setMessageType(incoming.messageType());
        message.setContent(incoming.content());
        message.setStatus(MessagingMessageStatus.RECEIVED);
        message.setReceivedAt(time);
        message.setRawPayload(incoming.rawPayload());
        message = messages.save(message);
        dispatcher.dispatch(account, message.getMessagingConversation(), incoming.content());
        message.setStatus(MessagingMessageStatus.PROCESSED);
        message.setProcessedAt(LocalDateTime.now(clock));
        messages.save(message);
    }

    private MessagingAccount createAccount(IncomingMessagingMessage incoming) {
        MessagingAccount account = new MessagingAccount();
        account.setChannel(incoming.channel());
        account.setExternalUserId(incoming.externalUserId());
        account.setExternalChatId(incoming.externalChatId());
        account.setUsername(incoming.username());
        account.setDisplayName(incoming.displayName());
        account.setStatus(MessagingAccountStatus.PENDING);
        return account;
    }
}
