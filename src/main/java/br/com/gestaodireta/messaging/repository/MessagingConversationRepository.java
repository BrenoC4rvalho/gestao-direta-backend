package br.com.gestaodireta.messaging.repository;

import br.com.gestaodireta.messaging.domain.MessagingConversation;
import br.com.gestaodireta.messaging.enumeration.MessagingConversationStatus;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessagingConversationRepository
        extends JpaRepository<MessagingConversation, Long> {
    Optional<MessagingConversation> findByMessagingAccountIdAndStatusIn(
            Long accountId, Collection<MessagingConversationStatus> statuses);
}
