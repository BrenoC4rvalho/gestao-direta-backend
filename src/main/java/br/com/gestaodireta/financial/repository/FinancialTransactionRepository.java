package br.com.gestaodireta.financial.repository;

import br.com.gestaodireta.financial.entity.FinancialTransaction;
import br.com.gestaodireta.financial.enumeration.FinancialRecordStatus;
import br.com.gestaodireta.financial.enumeration.PaymentMethod;
import br.com.gestaodireta.financial.enumeration.PaymentStatus;
import br.com.gestaodireta.financial.enumeration.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FinancialTransactionRepository extends JpaRepository<FinancialTransaction, Long> {

    Page<FinancialTransaction> findByFarmIdAndRecordStatus(
            Long farmId, FinancialRecordStatus recordStatus, Pageable pageable);

    @Query(
            """
            select transaction
            from FinancialTransaction transaction
            where transaction.farm.id = :farmId
              and transaction.recordStatus = :recordStatus
              and transaction.transactionDate >= :transactionDateStart
              and transaction.transactionDate <= :transactionDateEnd
              and (:filterPaidAt = false
                or (transaction.paidAt >= :paidAtStart and transaction.paidAt <= :paidAtEnd))
              and (:type is null or transaction.type = :type)
              and (:filterCategoryIds = false or transaction.category.id in :categoryIds)
              and (:filterPaymentStatuses = false or transaction.status in :paymentStatuses)
              and (:filterPaymentMethods = false or transaction.paymentMethod in :paymentMethods)
              and (:description is null
                or lower(transaction.description) like concat('%', cast(:description as string), '%'))
              and (:createdByUserId is null
                or transaction.createdByUser.id = :createdByUserId)
              and transaction.amount >= :minAmount
              and transaction.amount <= :maxAmount
            """)
    Page<FinancialTransaction> findAllFiltered(
            @Param("farmId") Long farmId,
            @Param("transactionDateStart") LocalDate transactionDateStart,
            @Param("transactionDateEnd") LocalDate transactionDateEnd,
            @Param("paidAtStart") LocalDate paidAtStart,
            @Param("paidAtEnd") LocalDate paidAtEnd,
            @Param("filterPaidAt") boolean filterPaidAt,
            @Param("type") TransactionType type,
            @Param("filterCategoryIds") boolean filterCategoryIds,
            @Param("categoryIds") Collection<Long> categoryIds,
            @Param("filterPaymentStatuses") boolean filterPaymentStatuses,
            @Param("paymentStatuses") Collection<PaymentStatus> paymentStatuses,
            @Param("filterPaymentMethods") boolean filterPaymentMethods,
            @Param("paymentMethods") Collection<PaymentMethod> paymentMethods,
            @Param("recordStatus") FinancialRecordStatus recordStatus,
            @Param("description") String description,
            @Param("createdByUserId") Long createdByUserId,
            @Param("minAmount") BigDecimal minAmount,
            @Param("maxAmount") BigDecimal maxAmount,
            Pageable pageable);

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

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            """
            update FinancialTransaction transaction
            set transaction.status = :overdueStatus,
                transaction.updatedAt = :updatedAt
            where transaction.type = :type
              and transaction.status = :pendingStatus
              and transaction.recordStatus = :recordStatus
              and transaction.dueDate is not null
              and transaction.dueDate < :today
            """)
    int markOverdueTransactions(
            @Param("overdueStatus") PaymentStatus overdueStatus,
            @Param("updatedAt") LocalDateTime updatedAt,
            @Param("type") TransactionType type,
            @Param("pendingStatus") PaymentStatus pendingStatus,
            @Param("recordStatus") FinancialRecordStatus recordStatus,
            @Param("today") LocalDate today);
}
