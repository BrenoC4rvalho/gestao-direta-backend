package br.com.gestaodireta.auth.entity;

import br.com.gestaodireta.auth.enumeration.PasswordRecoveryCodeStatus;
import br.com.gestaodireta.shared.audit.BaseEntity;
import br.com.gestaodireta.user.entity.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "password_recovery_codes")
public class PasswordRecoveryCode extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "code_hash")
    private String codeHash;

    @Enumerated(EnumType.STRING)
    private PasswordRecoveryCodeStatus status;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "attempt_count")
    private int attemptCount;

    @Column(name = "max_attempts")
    private int maxAttempts;

    @Column(name = "requested_at")
    private LocalDateTime requestedAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "invalidated_at")
    private LocalDateTime invalidatedAt;

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User v) {
        user = v;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public void setCodeHash(String v) {
        codeHash = v;
    }

    public PasswordRecoveryCodeStatus getStatus() {
        return status;
    }

    public void setStatus(PasswordRecoveryCodeStatus v) {
        status = v;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime v) {
        expiresAt = v;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(int v) {
        attemptCount = v;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int v) {
        maxAttempts = v;
    }

    public LocalDateTime getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(LocalDateTime v) {
        requestedAt = v;
    }

    public LocalDateTime getUsedAt() {
        return usedAt;
    }

    public void setUsedAt(LocalDateTime v) {
        usedAt = v;
    }

    public LocalDateTime getInvalidatedAt() {
        return invalidatedAt;
    }

    public void setInvalidatedAt(LocalDateTime v) {
        invalidatedAt = v;
    }
}
