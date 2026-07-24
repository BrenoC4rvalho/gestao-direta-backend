package br.com.gestaodireta.messaging.repository;

import br.com.gestaodireta.messaging.domain.MessagingMessage;
import br.com.gestaodireta.messaging.dto.MessagingMessageFilterRequest;
import org.springframework.data.jpa.domain.Specification;

public final class MessagingMessageSpecifications {
    private MessagingMessageSpecifications() {}

    public static Specification<MessagingMessage> filtered(MessagingMessageFilterRequest filter) {
        return (root, query, builder) -> {
            var predicate = builder.conjunction();
            if (filter.messagingConversationId() != null) {
                predicate =
                        builder.and(
                                predicate,
                                builder.equal(
                                        root.get("messagingConversation").get("id"),
                                        filter.messagingConversationId()));
            }
            if (filter.messageDirection() != null) {
                predicate =
                        builder.and(
                                predicate,
                                builder.equal(root.get("direction"), filter.messageDirection()));
            }
            if (filter.status() != null) {
                predicate =
                        builder.and(predicate, builder.equal(root.get("status"), filter.status()));
            }
            return predicate;
        };
    }
}
