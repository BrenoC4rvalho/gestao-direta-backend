package br.com.gestaodireta.financial.repository;

import br.com.gestaodireta.financial.entity.PendingFinancialTransaction;
import br.com.gestaodireta.financial.enumeration.PendingFinancialTransactionStatus;
import br.com.gestaodireta.financial.enumeration.TransactionType;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PendingFinancialTransactionRepository
        extends JpaRepository<PendingFinancialTransaction, Long> {

    Optional<PendingFinancialTransaction> findBySourceMessageId(Long sourceMessageId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select pending from PendingFinancialTransaction pending where pending.id = :id")
    Optional<PendingFinancialTransaction> findByIdForUpdate(@Param("id") Long id);

    @Query(
            """
            select pending from PendingFinancialTransaction pending
            where pending.farm.id = :farmId
              and (:status is null or pending.status = :status)
              and (:type is null or pending.type = :type)
              and (:startDate is null or pending.transactionDate >= :startDate)
              and (:endDate is null or pending.transactionDate <= :endDate)
              and (:requestedByUserId is null or pending.requestedByUser.id = :requestedByUserId)
            """)
    Page<PendingFinancialTransaction> findAllFiltered(
            @Param("farmId") Long farmId,
            @Param("status") PendingFinancialTransactionStatus status,
            @Param("type") TransactionType type,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("requestedByUserId") Long requestedByUserId,
            Pageable pageable);

    @Query("select pending.farm.id from PendingFinancialTransaction pending where pending.id = :id")
    Optional<Long> findFarmIdById(@Param("id") Long id);
}
