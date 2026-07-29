package br.com.gestaodireta.auth.entity;

import br.com.gestaodireta.shared.audit.BaseEntity;
import br.com.gestaodireta.user.entity.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetToken extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "password_recovery_code_id")
    private PasswordRecoveryCode recoveryCode;

    @Column(name = "token_hash")
    private String tokenHash;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

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

    public PasswordRecoveryCode getRecoveryCode() {
        return recoveryCode;
    }

    public void setRecoveryCode(PasswordRecoveryCode v) {
        recoveryCode = v;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String v) {
        tokenHash = v;
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

    public LocalDateTime getInvalidatedAt() {
        return invalidatedAt;
    }

    public void setInvalidatedAt(LocalDateTime v) {
        invalidatedAt = v;
    }
}
