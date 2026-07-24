package br.com.gestaodireta.messaging.repository;

import br.com.gestaodireta.messaging.domain.MessagingMessage;
import br.com.gestaodireta.messaging.enumeration.MessagingChannel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface MessagingMessageRepository
        extends JpaRepository<MessagingMessage, Long>, JpaSpecificationExecutor<MessagingMessage> {
    boolean existsByChannelAndProviderUpdateId(MessagingChannel channel, String providerUpdateId);
}
