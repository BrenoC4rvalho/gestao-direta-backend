package br.com.gestaodireta.messaging.repository;

import br.com.gestaodireta.messaging.domain.MessagingAccount;
import br.com.gestaodireta.messaging.enumeration.*;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface MessagingAccountRepository extends JpaRepository<MessagingAccount, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<MessagingAccount> findWithLockById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<MessagingAccount>
            findFirstWithLockByChannelAndExternalUserIdAndExternalChatIdOrderByCreatedAtDesc(
                    MessagingChannel channel, String externalUserId, String externalChatId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<MessagingAccount>
            findFirstWithLockByChannelAndExternalUserIdAndStatusOrderByCreatedAtDesc(
                    MessagingChannel channel, String externalUserId, MessagingAccountStatus status);

    java.util.Optional<MessagingAccount>
            findFirstByUserContactUserIdAndChannelAndStatusAndVerifiedAtIsNotNullAndExternalChatIdIsNotNull(
                    Long userId, MessagingChannel channel, MessagingAccountStatus status);

    boolean existsByUserContactIdAndChannelAndStatus(
            Long userContactId, MessagingChannel channel, MessagingAccountStatus status);
}
