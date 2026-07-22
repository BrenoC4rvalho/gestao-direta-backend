package br.com.gestaodireta.messaging.domain;

import br.com.gestaodireta.messaging.enumeration.*;
import br.com.gestaodireta.shared.audit.BaseEntity;
import br.com.gestaodireta.user.entity.UserContact;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "contact_verification_codes")
public class ContactVerificationCode extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_contact_id", nullable = false)
    private UserContact userContact;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_type", nullable = false, length = 40)
    private ContactVerificationType verificationType;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private MessagingChannel channel;

    @Column(name = "code_hash", nullable = false, length = 255)
    private String codeHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ContactVerificationStatus status;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    public Long getId() {
        return id;
    }

    public UserContact getUserContact() {
        return userContact;
    }

    public void setUserContact(UserContact v) {
        userContact = v;
    }

    public ContactVerificationType getVerificationType() {
        return verificationType;
    }

    public void setVerificationType(ContactVerificationType v) {
        verificationType = v;
    }

    public MessagingChannel getChannel() {
        return channel;
    }

    public void setChannel(MessagingChannel v) {
        channel = v;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public void setCodeHash(String v) {
        codeHash = v;
    }

    public ContactVerificationStatus getStatus() {
        return status;
    }

    public void setStatus(ContactVerificationStatus v) {
        status = v;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime v) {
        expiresAt = v;
    }

    public LocalDateTime getUsedAt() {
        return usedAt;
    }

    public void setUsedAt(LocalDateTime v) {
        usedAt = v;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(int v) {
        attemptCount = v;
    }
}
