package br.com.gestaodireta.messaging.domain;

import br.com.gestaodireta.farm.entity.Farm;
import br.com.gestaodireta.messaging.enumeration.*;
import br.com.gestaodireta.shared.audit.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "messaging_conversations")
public class MessagingConversation extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "messaging_account_id", nullable = false)
    private MessagingAccount messagingAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "farm_id")
    private Farm farm;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private MessagingConversationStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_step", nullable = false, length = 40)
    private MessagingConversationStep currentStep;

    @Column(name = "last_interaction_at", nullable = false)
    private LocalDateTime lastInteractionAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    public Long getId() {
        return id;
    }

    public MessagingAccount getMessagingAccount() {
        return messagingAccount;
    }

    public void setMessagingAccount(MessagingAccount v) {
        messagingAccount = v;
    }

    public Farm getFarm() {
        return farm;
    }

    public void setFarm(Farm v) {
        farm = v;
    }

    public MessagingConversationStatus getStatus() {
        return status;
    }

    public void setStatus(MessagingConversationStatus v) {
        status = v;
    }

    public MessagingConversationStep getCurrentStep() {
        return currentStep;
    }

    public void setCurrentStep(MessagingConversationStep v) {
        currentStep = v;
    }

    public LocalDateTime getLastInteractionAt() {
        return lastInteractionAt;
    }

    public void setLastInteractionAt(LocalDateTime v) {
        lastInteractionAt = v;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime v) {
        expiresAt = v;
    }
}
