package br.com.gestaodireta.messaging.repository;

import br.com.gestaodireta.messaging.domain.MessagingAccount;
import br.com.gestaodireta.messaging.enumeration.*;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessagingAccountRepository extends JpaRepository<MessagingAccount, Long> {
    Optional<MessagingAccount> findByChannelAndExternalUserIdAndExternalChatId(
            MessagingChannel channel, String externalUserId, String externalChatId);

    Optional<MessagingAccount> findByChannelAndExternalUserId(
            MessagingChannel channel, String externalUserId);

    boolean existsByUserContactIdAndChannelAndStatus(
            Long userContactId, MessagingChannel channel, MessagingAccountStatus status);
}
