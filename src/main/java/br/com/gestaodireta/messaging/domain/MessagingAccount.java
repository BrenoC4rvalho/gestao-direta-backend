package br.com.gestaodireta.messaging.domain;

import br.com.gestaodireta.messaging.enumeration.MessagingAccountStatus;
import br.com.gestaodireta.messaging.enumeration.MessagingChannel;
import br.com.gestaodireta.shared.audit.BaseEntity;
import br.com.gestaodireta.user.entity.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "messaging_accounts")
public class MessagingAccount extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

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

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User v) {
        user = v;
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
}
