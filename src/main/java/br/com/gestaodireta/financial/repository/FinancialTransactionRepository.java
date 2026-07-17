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
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FinancialTransactionRepository extends JpaRepository<FinancialTransaction, Long> {

    Page<FinancialTransaction> findByFarmIdAndRecordStatus(
            Long farmId, FinancialRecordStatus recordStatus, Pageable pageable);

    @EntityGraph(attributePaths = {"harvestSeason"})
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
              and (:harvestSeasonId is null
                or transaction.harvestSeason.id = :harvestSeasonId)
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
            @Param("harvestSeasonId") Long harvestSeasonId,
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
            select
              coalesce(sum(case
                when transaction.type = br.com.gestaodireta.financial.enumeration.TransactionType.INCOME
                  and transaction.status = br.com.gestaodireta.financial.enumeration.PaymentStatus.PAID
                then transaction.amount
                else 0
              end), 0) as paidIncome,
              coalesce(sum(case
                when transaction.type = br.com.gestaodireta.financial.enumeration.TransactionType.EXPENSE
                  and transaction.status = br.com.gestaodireta.financial.enumeration.PaymentStatus.PAID
                then transaction.amount
                else 0
              end), 0) as paidExpense,
              coalesce(sum(case
                when transaction.type = br.com.gestaodireta.financial.enumeration.TransactionType.INCOME
                  and transaction.status in (
                    br.com.gestaodireta.financial.enumeration.PaymentStatus.PENDING,
                    br.com.gestaodireta.financial.enumeration.PaymentStatus.OVERDUE
                  )
                then transaction.amount
                else 0
              end), 0) as expectedIncome,
              coalesce(sum(case
                when transaction.type = br.com.gestaodireta.financial.enumeration.TransactionType.EXPENSE
                  and transaction.status in (
                    br.com.gestaodireta.financial.enumeration.PaymentStatus.PENDING,
                    br.com.gestaodireta.financial.enumeration.PaymentStatus.OVERDUE
                  )
                then transaction.amount
                else 0
              end), 0) as expectedExpense,
              coalesce(sum(case
                when transaction.type = br.com.gestaodireta.financial.enumeration.TransactionType.EXPENSE
                  and transaction.status = br.com.gestaodireta.financial.enumeration.PaymentStatus.PENDING
                  and transaction.dueDate >= :today
                  and transaction.dueDate <= :next30Days
                then transaction.amount
                else 0
              end), 0) as payableNext30Days,
              coalesce(sum(case
                when transaction.type = br.com.gestaodireta.financial.enumeration.TransactionType.EXPENSE
                  and transaction.status = br.com.gestaodireta.financial.enumeration.PaymentStatus.OVERDUE
                then transaction.amount
                else 0
              end), 0) as overdueExpenses,
              coalesce(sum(case
                when transaction.type = br.com.gestaodireta.financial.enumeration.TransactionType.INCOME
                  and transaction.status = br.com.gestaodireta.financial.enumeration.PaymentStatus.OVERDUE
                then transaction.amount
                else 0
              end), 0) as overdueIncome,
              coalesce(sum(case
                when transaction.type = br.com.gestaodireta.financial.enumeration.TransactionType.INCOME
                  and transaction.status = br.com.gestaodireta.financial.enumeration.PaymentStatus.PENDING
                  and transaction.dueDate >= :today
                  and transaction.dueDate <= :next30Days
                then transaction.amount
                else 0
              end), 0) as receivablePendingNext30Days
            from FinancialTransaction transaction
            where transaction.farm.id = :farmId
              and transaction.recordStatus = br.com.gestaodireta.financial.enumeration.FinancialRecordStatus.ACTIVE
            """)
    FinancialSummaryProjection summarizeFinancialDashboard(
            @Param("farmId") Long farmId,
            @Param("today") LocalDate today,
            @Param("next30Days") LocalDate next30Days);

    @Query(
            """
            select
              transaction.id as transactionId,
              transaction.description as description,
              category.name as categoryName,
              transaction.amount as amount,
              transaction.dueDate as dueDate
            from FinancialTransaction transaction
            left join transaction.category category
            where transaction.farm.id = :farmId
              and transaction.recordStatus = br.com.gestaodireta.financial.enumeration.FinancialRecordStatus.ACTIVE
              and transaction.type = br.com.gestaodireta.financial.enumeration.TransactionType.EXPENSE
              and transaction.dueDate is not null
              and (
                transaction.status = br.com.gestaodireta.financial.enumeration.PaymentStatus.OVERDUE
                or (
                  transaction.status = br.com.gestaodireta.financial.enumeration.PaymentStatus.PENDING
                  and transaction.dueDate < :today
                )
              )
            order by transaction.dueDate asc, transaction.amount desc
            """)
    List<OverdueBillAlertProjection> findOverdueBillAlerts(
            @Param("farmId") Long farmId, @Param("today") LocalDate today, Pageable pageable);

    @Query(
            """
            select
              count(transaction) as count,
              coalesce(sum(transaction.amount), 0) as totalAmount
            from FinancialTransaction transaction
            where transaction.farm.id = :farmId
              and transaction.recordStatus = br.com.gestaodireta.financial.enumeration.FinancialRecordStatus.ACTIVE
              and transaction.type = br.com.gestaodireta.financial.enumeration.TransactionType.EXPENSE
              and transaction.status = br.com.gestaodireta.financial.enumeration.PaymentStatus.PENDING
              and transaction.dueDate = :today
            """)
    DueBillsSummaryProjection summarizeDueToday(
            @Param("farmId") Long farmId, @Param("today") LocalDate today);

    @Query(
            """
            select
              count(transaction) as count,
              coalesce(sum(transaction.amount), 0) as totalAmount
            from FinancialTransaction transaction
            where transaction.farm.id = :farmId
              and transaction.recordStatus = br.com.gestaodireta.financial.enumeration.FinancialRecordStatus.ACTIVE
              and transaction.type = br.com.gestaodireta.financial.enumeration.TransactionType.EXPENSE
              and transaction.status = br.com.gestaodireta.financial.enumeration.PaymentStatus.PENDING
              and transaction.dueDate > :today
              and transaction.dueDate <= :next7Days
            """)
    DueBillsSummaryProjection summarizeDueNext7Days(
            @Param("farmId") Long farmId,
            @Param("today") LocalDate today,
            @Param("next7Days") LocalDate next7Days);

    @Query(
            """
            select
              coalesce(sum(case when transaction.type = br.com.gestaodireta.financial.enumeration.TransactionType.EXPENSE and transaction.status = br.com.gestaodireta.financial.enumeration.PaymentStatus.PAID then transaction.amount else 0 end), 0) as realizedCost,
              coalesce(sum(case when transaction.type = br.com.gestaodireta.financial.enumeration.TransactionType.INCOME and transaction.status = br.com.gestaodireta.financial.enumeration.PaymentStatus.PAID then transaction.amount else 0 end), 0) as realizedRevenue,
              coalesce(sum(case when transaction.type = br.com.gestaodireta.financial.enumeration.TransactionType.EXPENSE and transaction.status in (br.com.gestaodireta.financial.enumeration.PaymentStatus.PENDING, br.com.gestaodireta.financial.enumeration.PaymentStatus.OVERDUE) then transaction.amount else 0 end), 0) as openCost,
              coalesce(sum(case when transaction.type = br.com.gestaodireta.financial.enumeration.TransactionType.INCOME and transaction.status in (br.com.gestaodireta.financial.enumeration.PaymentStatus.PENDING, br.com.gestaodireta.financial.enumeration.PaymentStatus.OVERDUE) then transaction.amount else 0 end), 0) as openRevenue,
              coalesce(sum(case when transaction.type = br.com.gestaodireta.financial.enumeration.TransactionType.EXPENSE and transaction.status = br.com.gestaodireta.financial.enumeration.PaymentStatus.PENDING then transaction.amount else 0 end), 0) as pendingPayableAmount,
              coalesce(sum(case when transaction.type = br.com.gestaodireta.financial.enumeration.TransactionType.INCOME and transaction.status = br.com.gestaodireta.financial.enumeration.PaymentStatus.PENDING then transaction.amount else 0 end), 0) as pendingReceivableAmount,
              coalesce(sum(case when transaction.type = br.com.gestaodireta.financial.enumeration.TransactionType.EXPENSE and (transaction.status = br.com.gestaodireta.financial.enumeration.PaymentStatus.OVERDUE or (transaction.status = br.com.gestaodireta.financial.enumeration.PaymentStatus.PENDING and transaction.dueDate < :today)) then transaction.amount else 0 end), 0) as overduePayableAmount,
              coalesce(sum(case when transaction.type = br.com.gestaodireta.financial.enumeration.TransactionType.INCOME and (transaction.status = br.com.gestaodireta.financial.enumeration.PaymentStatus.OVERDUE or (transaction.status = br.com.gestaodireta.financial.enumeration.PaymentStatus.PENDING and transaction.dueDate < :today)) then transaction.amount else 0 end), 0) as overdueReceivableAmount,
              count(transaction) as transactionCount,
              coalesce(sum(case when transaction.type = br.com.gestaodireta.financial.enumeration.TransactionType.INCOME then 1 else 0 end), 0) as incomeCount,
              coalesce(sum(case when transaction.type = br.com.gestaodireta.financial.enumeration.TransactionType.EXPENSE then 1 else 0 end), 0) as expenseCount
            from FinancialTransaction transaction
            where transaction.farm.id = :farmId
              and transaction.harvestSeason.id in :harvestSeasonIds
              and transaction.recordStatus = br.com.gestaodireta.financial.enumeration.FinancialRecordStatus.ACTIVE
              and transaction.status in (br.com.gestaodireta.financial.enumeration.PaymentStatus.PAID, br.com.gestaodireta.financial.enumeration.PaymentStatus.PENDING, br.com.gestaodireta.financial.enumeration.PaymentStatus.OVERDUE)
            """)
    HarvestSeasonFinancialTotalsProjection summarizeHarvestSeasonFinancialTotals(
            @Param("farmId") Long farmId,
            @Param("harvestSeasonIds") Collection<Long> harvestSeasonIds,
            @Param("today") LocalDate today);

    @Query(
            """
            select transaction
            from FinancialTransaction transaction
            where transaction.farm.id = :farmId
              and transaction.harvestSeason.id = :harvestSeasonId
              and transaction.harvestSeason.farm.id = :farmId
              and transaction.recordStatus = br.com.gestaodireta.financial.enumeration.FinancialRecordStatus.ACTIVE
              and transaction.status in (
                br.com.gestaodireta.financial.enumeration.PaymentStatus.PAID,
                br.com.gestaodireta.financial.enumeration.PaymentStatus.PENDING,
                br.com.gestaodireta.financial.enumeration.PaymentStatus.OVERDUE
              )
            """)
    List<FinancialTransaction> findDashboardTransactionsByFarmIdAndHarvestSeasonId(
            @Param("farmId") Long farmId, @Param("harvestSeasonId") Long harvestSeasonId);

    Page<FinancialTransaction> findByFarmIdAndTypeAndRecordStatusAndStatusInAndDueDateIsNotNull(
            Long farmId,
            TransactionType type,
            FinancialRecordStatus recordStatus,
            Collection<PaymentStatus> statuses,
            Pageable pageable);

    @EntityGraph(attributePaths = {"farm", "category", "harvestSeason"})
    @Query(
            """
            select transaction
            from FinancialTransaction transaction
            where transaction.farm.id = :farmId
              and transaction.recordStatus = br.com.gestaodireta.financial.enumeration.FinancialRecordStatus.ACTIVE
              and transaction.status in (
                br.com.gestaodireta.financial.enumeration.PaymentStatus.PENDING,
                br.com.gestaodireta.financial.enumeration.PaymentStatus.OVERDUE
              )
              and transaction.dueDate is not null
              and (:type is null or transaction.type = :type)
              and (:filterHarvestSeasonIds = false
                or transaction.harvestSeason.id in :harvestSeasonIds)
              and (
                :includeAll = true
                or (
                  :includePending = true
                  and transaction.status = br.com.gestaodireta.financial.enumeration.PaymentStatus.PENDING
                  and transaction.dueDate >= :today
                )
                or (
                  :includeOverdue = true
                  and (
                    transaction.status = br.com.gestaodireta.financial.enumeration.PaymentStatus.OVERDUE
                    or (
                      transaction.status = br.com.gestaodireta.financial.enumeration.PaymentStatus.PENDING
                      and transaction.dueDate < :today
                    )
                  )
                )
              )
              and (
                :periodDays is null
                or (
                  :includeOverdue = true
                  and (
                    transaction.status = br.com.gestaodireta.financial.enumeration.PaymentStatus.OVERDUE
                    or (
                      transaction.status = br.com.gestaodireta.financial.enumeration.PaymentStatus.PENDING
                      and transaction.dueDate < :today
                    )
                  )
                )
                or (
                  :includePending = true
                  and transaction.status = br.com.gestaodireta.financial.enumeration.PaymentStatus.PENDING
                  and transaction.dueDate >= :today
                  and transaction.dueDate <= :endDate
                )
                or (
                  :includeAll = true
                  and (
                    transaction.status = br.com.gestaodireta.financial.enumeration.PaymentStatus.OVERDUE
                    or transaction.dueDate < :today
                    or (
                      transaction.status = br.com.gestaodireta.financial.enumeration.PaymentStatus.PENDING
                      and transaction.dueDate >= :today
                      and transaction.dueDate <= :endDate
                    )
                  )
                )
              )
            order by transaction.dueDate asc
            """)
    List<FinancialTransaction> findAgendaTransactions(
            @Param("farmId") Long farmId,
            @Param("type") TransactionType type,
            @Param("includeAll") boolean includeAll,
            @Param("includePending") boolean includePending,
            @Param("includeOverdue") boolean includeOverdue,
            @Param("periodDays") Integer periodDays,
            @Param("today") LocalDate today,
            @Param("endDate") LocalDate endDate,
            @Param("filterHarvestSeasonIds") boolean filterHarvestSeasonIds,
            @Param("harvestSeasonIds") Collection<Long> harvestSeasonIds);

    @EntityGraph(attributePaths = {"farm", "category", "harvestSeason"})
    @Query(
            value =
                    """
                    select transaction
                    from FinancialTransaction transaction
                    where transaction.farm.id = :farmId
                      and transaction.recordStatus = br.com.gestaodireta.financial.enumeration.FinancialRecordStatus.ACTIVE
                      and transaction.status in (
                        br.com.gestaodireta.financial.enumeration.PaymentStatus.PENDING,
                        br.com.gestaodireta.financial.enumeration.PaymentStatus.OVERDUE
                      )
                      and transaction.dueDate is not null
                      and (:type is null or transaction.type = :type)
                      and (:filterHarvestSeasonIds = false
                        or transaction.harvestSeason.id in :harvestSeasonIds)
                      and (
                        :includeAll = true
                        or (
                          :includePending = true
                          and transaction.status = br.com.gestaodireta.financial.enumeration.PaymentStatus.PENDING
                          and transaction.dueDate >= :today
                        )
                        or (
                          :includeOverdue = true
                          and (
                            transaction.status = br.com.gestaodireta.financial.enumeration.PaymentStatus.OVERDUE
                            or (
                              transaction.status = br.com.gestaodireta.financial.enumeration.PaymentStatus.PENDING
                              and transaction.dueDate < :today
                            )
                          )
                        )
                      )
                      and (
                        :periodDays is null
                        or (
                          :includeOverdue = true
                          and (
                            transaction.status = br.com.gestaodireta.financial.enumeration.PaymentStatus.OVERDUE
                            or (
                              transaction.status = br.com.gestaodireta.financial.enumeration.PaymentStatus.PENDING
                              and transaction.dueDate < :today
                            )
                          )
                        )
                        or (
                          :includePending = true
                          and transaction.status = br.com.gestaodireta.financial.enumeration.PaymentStatus.PENDING
                          and transaction.dueDate >= :today
                          and transaction.dueDate <= :endDate
                        )
                        or (
                          :includeAll = true
                          and (
                            transaction.status = br.com.gestaodireta.financial.enumeration.PaymentStatus.OVERDUE
                            or transaction.dueDate < :today
                            or (
                              transaction.status = br.com.gestaodireta.financial.enumeration.PaymentStatus.PENDING
                              and transaction.dueDate >= :today
                              and transaction.dueDate <= :endDate
                            )
                          )
                        )
                      )
                    """,
            countQuery =
                    """
                    select count(transaction)
                    from FinancialTransaction transaction
                    where transaction.farm.id = :farmId
                      and transaction.recordStatus = br.com.gestaodireta.financial.enumeration.FinancialRecordStatus.ACTIVE
                      and transaction.status in (
                        br.com.gestaodireta.financial.enumeration.PaymentStatus.PENDING,
                        br.com.gestaodireta.financial.enumeration.PaymentStatus.OVERDUE
                      )
                      and transaction.dueDate is not null
                      and (:type is null or transaction.type = :type)
                      and (:filterHarvestSeasonIds = false
                        or transaction.harvestSeason.id in :harvestSeasonIds)
                      and (
                        :includeAll = true
                        or (
                          :includePending = true
                          and transaction.status = br.com.gestaodireta.financial.enumeration.PaymentStatus.PENDING
                          and transaction.dueDate >= :today
                        )
                        or (
                          :includeOverdue = true
                          and (
                            transaction.status = br.com.gestaodireta.financial.enumeration.PaymentStatus.OVERDUE
                            or (
                              transaction.status = br.com.gestaodireta.financial.enumeration.PaymentStatus.PENDING
                              and transaction.dueDate < :today
                            )
                          )
                        )
                      )
                      and (
                        :periodDays is null
                        or (
                          :includeOverdue = true
                          and (
                            transaction.status = br.com.gestaodireta.financial.enumeration.PaymentStatus.OVERDUE
                            or (
                              transaction.status = br.com.gestaodireta.financial.enumeration.PaymentStatus.PENDING
                              and transaction.dueDate < :today
                            )
                          )
                        )
                        or (
                          :includePending = true
                          and transaction.status = br.com.gestaodireta.financial.enumeration.PaymentStatus.PENDING
                          and transaction.dueDate >= :today
                          and transaction.dueDate <= :endDate
                        )
                        or (
                          :includeAll = true
                          and (
                            transaction.status = br.com.gestaodireta.financial.enumeration.PaymentStatus.OVERDUE
                            or transaction.dueDate < :today
                            or (
                              transaction.status = br.com.gestaodireta.financial.enumeration.PaymentStatus.PENDING
                              and transaction.dueDate >= :today
                              and transaction.dueDate <= :endDate
                            )
                          )
                        )
                      )
                    """)
    Page<FinancialTransaction> findAgendaTransactions(
            @Param("farmId") Long farmId,
            @Param("type") TransactionType type,
            @Param("includeAll") boolean includeAll,
            @Param("includePending") boolean includePending,
            @Param("includeOverdue") boolean includeOverdue,
            @Param("periodDays") Integer periodDays,
            @Param("today") LocalDate today,
            @Param("endDate") LocalDate endDate,
            @Param("filterHarvestSeasonIds") boolean filterHarvestSeasonIds,
            @Param("harvestSeasonIds") Collection<Long> harvestSeasonIds,
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
