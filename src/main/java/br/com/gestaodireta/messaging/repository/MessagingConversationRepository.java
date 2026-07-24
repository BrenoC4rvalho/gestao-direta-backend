package br.com.gestaodireta.messaging.repository;

import br.com.gestaodireta.messaging.domain.MessagingConversation;
import br.com.gestaodireta.messaging.enumeration.MessagingConversationStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessagingConversationRepository
        extends JpaRepository<MessagingConversation, Long> {
    Optional<MessagingConversation> findByMessagingAccountIdAndStatusIn(
            Long accountId, Collection<MessagingConversationStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<MessagingConversation> findWithLockByMessagingAccountIdAndStatusIn(
            Long accountId, List<MessagingConversationStatus> statuses);

    Optional<MessagingConversation> findFirstByMessagingAccountIdOrderByCreatedAtDesc(
            Long accountId);

    @Query(
            "select c from MessagingConversation c join fetch c.messagingAccount a left join fetch a.userContact uc left join fetch uc.user left join fetch c.farm where (:accountId is null or a.id = :accountId) and (:userId is null or uc.user.id = :userId) and (:farmId is null or c.farm.id = :farmId) and (:status is null or c.status = :status) and (:startDate is null or c.createdAt >= :startDate) and (:endDate is null or c.createdAt <= :endDate)")
    Page<MessagingConversation> findFiltered(
            @Param("accountId") Long accountId,
            @Param("userId") Long userId,
            @Param("farmId") Long farmId,
            @Param("status") MessagingConversationStatus status,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable);
}
