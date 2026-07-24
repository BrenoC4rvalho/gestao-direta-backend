package br.com.gestaodireta.messaging.service;

import br.com.gestaodireta.messaging.domain.*;
import br.com.gestaodireta.messaging.enumeration.*;
import br.com.gestaodireta.messaging.repository.MessagingConversationRepository;
import java.time.*;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class MessagingConversationService {
    private final MessagingConversationRepository conversations;
    private final int expirationHours;

    public MessagingConversationService(
            MessagingConversationRepository conversations,
            @Value("${messaging.conversation.expiration-hours:24}") int expirationHours) {
        this.conversations = conversations;
        this.expirationHours = expirationHours;
    }

    public MessagingConversation active(MessagingAccount account, LocalDateTime now) {
        var current =
                conversations.findByMessagingAccountIdAndStatusIn(
                        account.getId(),
                        List.of(
                                MessagingConversationStatus.ACTIVE,
                                MessagingConversationStatus.WAITING_FARM_SELECTION));
        if (current.isPresent() && current.get().getExpiresAt().isAfter(now)) {
            touch(current.get(), now);
            return conversations.save(current.get());
        }
        current.ifPresent(
                c -> {
                    c.setStatus(MessagingConversationStatus.EXPIRED);
                    conversations.save(c);
                });
        MessagingConversation c = new MessagingConversation();
        c.setMessagingAccount(account);
        c.setStatus(MessagingConversationStatus.ACTIVE);
        c.setCurrentStep(MessagingConversationStep.NONE);
        touch(c, now);
        return conversations.save(c);
    }

    public MessagingConversation forIncoming(MessagingAccount account, LocalDateTime now) {
        if (account.getStatus() == MessagingAccountStatus.BLOCKED
                || account.getStatus() == MessagingAccountStatus.INACTIVE) {
            return conversations
                    .findFirstByMessagingAccountIdOrderByCreatedAtDesc(account.getId())
                    .orElseGet(() -> canceled(account, now));
        }
        return active(account, now);
    }

    private MessagingConversation canceled(MessagingAccount account, LocalDateTime now) {
        MessagingConversation conversation = new MessagingConversation();
        conversation.setMessagingAccount(account);
        conversation.setStatus(MessagingConversationStatus.CANCELED);
        conversation.setCurrentStep(MessagingConversationStep.NONE);
        conversation.setLastInteractionAt(now);
        conversation.setExpiresAt(now);
        return conversations.save(conversation);
    }

    public void touch(MessagingConversation conversation, LocalDateTime now) {
        conversation.setLastInteractionAt(now);
        conversation.setExpiresAt(now.plusHours(expirationHours));
    }
}
