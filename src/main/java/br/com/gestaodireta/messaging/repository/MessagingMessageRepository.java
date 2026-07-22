package br.com.gestaodireta.messaging.repository;

import br.com.gestaodireta.messaging.domain.MessagingMessage;
import br.com.gestaodireta.messaging.enumeration.MessagingChannel;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessagingMessageRepository extends JpaRepository<MessagingMessage, Long> {
    boolean existsByChannelAndProviderUpdateId(MessagingChannel channel, String providerUpdateId);
}
