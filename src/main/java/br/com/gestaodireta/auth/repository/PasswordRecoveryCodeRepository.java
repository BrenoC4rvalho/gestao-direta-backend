package br.com.gestaodireta.auth.repository;

import br.com.gestaodireta.auth.entity.PasswordRecoveryCode;
import br.com.gestaodireta.auth.enumeration.PasswordRecoveryCodeStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface PasswordRecoveryCodeRepository extends JpaRepository<PasswordRecoveryCode, Long> {
    long countByUserIdAndRequestedAtAfter(Long userId, LocalDateTime requestedAt);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PasswordRecoveryCode> findFirstByUserIdAndStatusOrderByRequestedAtDesc(
            Long userId, PasswordRecoveryCodeStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PasswordRecoveryCode> findFirstByUserIdOrderByRequestedAtDesc(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<PasswordRecoveryCode> findByUserIdAndStatus(
            Long userId, PasswordRecoveryCodeStatus status);
}
