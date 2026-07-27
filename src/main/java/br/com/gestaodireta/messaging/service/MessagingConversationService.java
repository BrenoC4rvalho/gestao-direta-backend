package br.com.gestaodireta.messaging.service;

import br.com.gestaodireta.messaging.domain.MessagingAccount;
import br.com.gestaodireta.messaging.domain.MessagingConversation;
import br.com.gestaodireta.messaging.enumeration.MessagingAccountStatus;
import br.com.gestaodireta.messaging.enumeration.MessagingConversationStatus;
import br.com.gestaodireta.messaging.enumeration.MessagingConversationStep;
import br.com.gestaodireta.messaging.repository.MessagingConversationRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MessagingConversationService {
    private static final List<MessagingConversationStatus> OPEN_STATUSES =
            List.of(
                    MessagingConversationStatus.ACTIVE,
                    MessagingConversationStatus.WAITING_FARM_SELECTION);

    private final MessagingConversationRepository conversations;
    private final int expirationHours;

    public MessagingConversationService(
            MessagingConversationRepository conversations,
            @Value("${messaging.conversation.expiration-hours:24}") int expirationHours) {
        this.conversations = conversations;
        this.expirationHours = expirationHours;
    }

    @Transactional
    public MessagingConversation forIncoming(MessagingAccount account, LocalDateTime now) {
        if (account.getStatus() == MessagingAccountStatus.BLOCKED
                || account.getStatus() == MessagingAccountStatus.INACTIVE) {
            return conversations
                    .findFirstByMessagingAccountIdOrderByCreatedAtDesc(account.getId())
                    .orElseGet(() -> createCanceled(account, now));
        }

        return openOrCreate(account, now);
    }

    @Transactional
    public MessagingConversation active(MessagingAccount account, LocalDateTime now) {
        return openOrCreate(account, now);
    }

    @Transactional
    public void cancelOpenConversations(MessagingAccount account) {
        conversations
                .findWithLockByMessagingAccountIdAndStatusIn(account.getId(), OPEN_STATUSES)
                .forEach(this::cancel);
        conversations.flush();
    }

    @Transactional
    public void closeResidualOpenConversations(MessagingAccount account, LocalDateTime now) {
        conversations
                .findOpenByMessagingAccountIdForUpdate(account.getId(), OPEN_STATUSES)
                .ifPresent(
                        conversation -> {
                            if (isExpired(conversation, now)) {
                                expire(conversation);
                            } else {
                                cancel(conversation);
                            }
                            conversations.saveAndFlush(conversation);
                        });
    }

    public void touch(MessagingConversation conversation, LocalDateTime now) {
        conversation.setLastInteractionAt(now);
        conversation.setExpiresAt(now.plusHours(expirationHours));
    }

    private MessagingConversation openOrCreate(MessagingAccount account, LocalDateTime now) {
        return conversations
                .findOpenByMessagingAccountIdForUpdate(account.getId(), OPEN_STATUSES)
                .map(
                        conversation -> {
                            if (!isExpired(conversation, now)) {
                                touch(conversation, now);
                                return conversations.save(conversation);
                            }
                            expire(conversation);
                            conversations.saveAndFlush(conversation);
                            return createOpen(account, now);
                        })
                .orElseGet(() -> createOpen(account, now));
    }

    private boolean isExpired(MessagingConversation conversation, LocalDateTime now) {
        return conversation.getExpiresAt() == null || !conversation.getExpiresAt().isAfter(now);
    }

    private MessagingConversation createOpen(MessagingAccount account, LocalDateTime now) {
        MessagingConversation conversation = new MessagingConversation();
        conversation.setMessagingAccount(account);
        conversation.setStatus(MessagingConversationStatus.ACTIVE);
        conversation.setCurrentStep(MessagingConversationStep.NONE);
        touch(conversation, now);
        return conversations.save(conversation);
    }

    private MessagingConversation createCanceled(MessagingAccount account, LocalDateTime now) {
        MessagingConversation conversation = new MessagingConversation();
        conversation.setMessagingAccount(account);
        conversation.setStatus(MessagingConversationStatus.CANCELED);
        conversation.setCurrentStep(MessagingConversationStep.NONE);
        conversation.setLastInteractionAt(now);
        conversation.setExpiresAt(now);
        return conversations.save(conversation);
    }

    private void expire(MessagingConversation conversation) {
        conversation.setStatus(MessagingConversationStatus.EXPIRED);
        conversation.setCurrentStep(MessagingConversationStep.NONE);
        conversation.setFarm(null);
    }

    private void cancel(MessagingConversation conversation) {
        conversation.setStatus(MessagingConversationStatus.CANCELED);
        conversation.setCurrentStep(MessagingConversationStep.NONE);
        conversation.setFarm(null);
    }
}
