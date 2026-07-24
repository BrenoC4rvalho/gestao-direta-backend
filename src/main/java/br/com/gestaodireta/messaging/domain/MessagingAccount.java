package br.com.gestaodireta.messaging.domain;

import br.com.gestaodireta.messaging.enumeration.MessagingAccountStatus;
import br.com.gestaodireta.messaging.enumeration.MessagingChannel;
import br.com.gestaodireta.shared.audit.BaseEntity;
import br.com.gestaodireta.user.entity.UserContact;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "messaging_accounts")
public class MessagingAccount extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_contact_id")
    private UserContact userContact;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MessagingChannel channel;

    @Column(name = "external_user_id", nullable = false, length = 100)
    private String externalUserId;

    @Column(name = "external_chat_id", nullable = false, length = 100)
    private String externalChatId;

    @Column(length = 100)
    private String username;

    @Column(name = "display_name", length = 200)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MessagingAccountStatus status;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "last_interaction_at")
    private LocalDateTime lastInteractionAt;

    @Column(name = "link_attempt_count", nullable = false)
    private int linkAttemptCount;

    @Column(name = "link_blocked_until")
    private LocalDateTime linkBlockedUntil;

    @Column(name = "last_link_attempt_at")
    private LocalDateTime lastLinkAttemptAt;

    public Long getId() {
        return id;
    }

    public UserContact getUserContact() {
        return userContact;
    }

    public void setUserContact(UserContact v) {
        userContact = v;
    }

    public MessagingChannel getChannel() {
        return channel;
    }

    public void setChannel(MessagingChannel v) {
        channel = v;
    }

    public String getExternalUserId() {
        return externalUserId;
    }

    public void setExternalUserId(String v) {
        externalUserId = v;
    }

    public String getExternalChatId() {
        return externalChatId;
    }

    public void setExternalChatId(String v) {
        externalChatId = v;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String v) {
        username = v;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String v) {
        displayName = v;
    }

    public MessagingAccountStatus getStatus() {
        return status;
    }

    public void setStatus(MessagingAccountStatus v) {
        status = v;
    }

    public LocalDateTime getVerifiedAt() {
        return verifiedAt;
    }

    public void setVerifiedAt(LocalDateTime v) {
        verifiedAt = v;
    }

    public LocalDateTime getLastInteractionAt() {
        return lastInteractionAt;
    }

    public void setLastInteractionAt(LocalDateTime v) {
        lastInteractionAt = v;
    }

    public int getLinkAttemptCount() {
        return linkAttemptCount;
    }

    public void setLinkAttemptCount(int v) {
        linkAttemptCount = v;
    }

    public LocalDateTime getLinkBlockedUntil() {
        return linkBlockedUntil;
    }

    public void setLinkBlockedUntil(LocalDateTime v) {
        linkBlockedUntil = v;
    }

    public LocalDateTime getLastLinkAttemptAt() {
        return lastLinkAttemptAt;
    }

    public void setLastLinkAttemptAt(LocalDateTime v) {
        lastLinkAttemptAt = v;
    }
}
