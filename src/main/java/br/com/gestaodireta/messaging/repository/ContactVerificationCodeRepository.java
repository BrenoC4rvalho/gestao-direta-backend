package br.com.gestaodireta.messaging.repository;

import br.com.gestaodireta.messaging.domain.ContactVerificationCode;
import br.com.gestaodireta.messaging.enumeration.*;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.*;

public interface ContactVerificationCodeRepository
        extends JpaRepository<ContactVerificationCode, Long> {
    List<ContactVerificationCode> findByVerificationTypeAndChannelAndStatusAndExpiresAtAfter(
            ContactVerificationType type,
            MessagingChannel channel,
            ContactVerificationStatus status,
            LocalDateTime now);

    List<ContactVerificationCode> findByUserContactIdAndVerificationTypeAndChannelAndStatus(
            Long contactId,
            ContactVerificationType type,
            MessagingChannel channel,
            ContactVerificationStatus status);

    boolean existsByUserContactIdAndVerificationTypeAndChannelAndCreatedAtAfter(
            Long contactId,
            ContactVerificationType type,
            MessagingChannel channel,
            LocalDateTime since);
}
