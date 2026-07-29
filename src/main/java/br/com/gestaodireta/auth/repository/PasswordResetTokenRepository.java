package br.com.gestaodireta.auth.repository;

import br.com.gestaodireta.auth.entity.PasswordResetToken;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    java.util.List<PasswordResetToken> findByUserIdAndUsedAtIsNullAndInvalidatedAtIsNull(
            Long userId);
}
