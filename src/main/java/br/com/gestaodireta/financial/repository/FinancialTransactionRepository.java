package br.com.gestaodireta.financial.repository;

import br.com.gestaodireta.financial.entity.FinancialTransaction;
import br.com.gestaodireta.financial.enumeration.FinancialRecordStatus;
import br.com.gestaodireta.financial.enumeration.PaymentStatus;
import br.com.gestaodireta.financial.enumeration.TransactionType;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FinancialTransactionRepository extends JpaRepository<FinancialTransaction, Long> {

    Page<FinancialTransaction> findByFarmIdAndRecordStatus(
            Long farmId, FinancialRecordStatus recordStatus, Pageable pageable);

    @Query(
            """
            select transaction.farm.id
            from FinancialTransaction transaction
            where transaction.id = :transactionId
            """)
    Optional<Long> findFarmIdById(@Param("transactionId") Long transactionId);

    @Query(
            """
            select coalesce(sum(transaction.amount), 0)
            from FinancialTransaction transaction
            where transaction.farm.id = :farmId
              and transaction.recordStatus = :recordStatus
              and transaction.status <> :ignoredStatus
              and transaction.type = :type
            """)
    BigDecimal sumByFarmAndType(
            @Param("farmId") Long farmId,
            @Param("recordStatus") FinancialRecordStatus recordStatus,
            @Param("ignoredStatus") PaymentStatus ignoredStatus,
            @Param("type") TransactionType type);

    @Query(
            """
            select coalesce(sum(transaction.amount), 0)
            from FinancialTransaction transaction
            where transaction.farm.id = :farmId
              and transaction.recordStatus = :recordStatus
              and transaction.status = :status
            """)
    BigDecimal sumByFarmAndPaymentStatus(
            @Param("farmId") Long farmId,
            @Param("recordStatus") FinancialRecordStatus recordStatus,
            @Param("status") PaymentStatus status);

    Page<FinancialTransaction> findByFarmIdAndTypeAndRecordStatusAndStatusInAndDueDateIsNotNull(
            Long farmId,
            TransactionType type,
            FinancialRecordStatus recordStatus,
            Collection<PaymentStatus> statuses,
            Pageable pageable);
}
