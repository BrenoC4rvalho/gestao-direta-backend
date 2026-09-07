package br.com.gestaodireta.harvest.repository;

import br.com.gestaodireta.harvest.entity.HarvestSeason;
import br.com.gestaodireta.harvest.enumeration.HarvestSeasonStatus;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HarvestSeasonRepository extends JpaRepository<HarvestSeason, Long> {

    @Query(
            value =
                    """
                    select season
                    from HarvestSeason season
                    join fetch season.farm
                    join fetch season.productionActivity
                    where season.farm.id = :farmId
                      and (:filterStatuses = false or season.status in :statuses)
                      and (:filterStatuses = true
                        or :includeInactive = true
                        or season.status <> br.com.gestaodireta.harvest.enumeration.HarvestSeasonStatus.INACTIVE)
                      and (:filterProductionActivityIds = false
                        or season.productionActivity.id in :productionActivityIds)
                      and (:filterPeriodStart = false or season.endDate is null or season.endDate >= :periodStart)
                      and (:filterPeriodEnd = false or season.startDate <= :periodEnd)
                    """,
            countQuery =
                    """
                    select count(season)
                    from HarvestSeason season
                    where season.farm.id = :farmId
                      and (:filterStatuses = false or season.status in :statuses)
                      and (:filterStatuses = true
                        or :includeInactive = true
                        or season.status <> br.com.gestaodireta.harvest.enumeration.HarvestSeasonStatus.INACTIVE)
                      and (:filterProductionActivityIds = false
                        or season.productionActivity.id in :productionActivityIds)
                      and (:filterPeriodStart = false or season.endDate is null or season.endDate >= :periodStart)
                      and (:filterPeriodEnd = false or season.startDate <= :periodEnd)
                    """)
    Page<HarvestSeason> findByFarmId(
            @Param("farmId") Long farmId,
            @Param("includeInactive") boolean includeInactive,
            @Param("filterStatuses") boolean filterStatuses,
            @Param("statuses") Collection<HarvestSeasonStatus> statuses,
            @Param("filterProductionActivityIds") boolean filterProductionActivityIds,
            @Param("productionActivityIds") Collection<Long> productionActivityIds,
            @Param("filterPeriodStart") boolean filterPeriodStart,
            @Param("periodStart") LocalDate periodStart,
            @Param("filterPeriodEnd") boolean filterPeriodEnd,
            @Param("periodEnd") LocalDate periodEnd,
            Pageable pageable);

    @Query(
            value =
                    """
                    select
                      season.id as id,
                      farm.id as farmId,
                      farm.name as farmName,
                      productionActivity.id as productionActivityId,
                      productionActivity.name as productionActivityName,
                      season.name as name,
                      season.description as description,
                      season.startDate as startDate,
                      season.endDate as endDate,
                      coalesce((
                        select sum(budgetItem.plannedAmount)
                        from HarvestSeasonBudgetItem budgetItem
                        where budgetItem.harvestSeason.id = season.id
                          and budgetItem.type = br.com.gestaodireta.financial.enumeration.TransactionType.EXPENSE
                      ), 0) as expectedCost,
                      coalesce((
                        select sum(budgetItem.plannedAmount)
                        from HarvestSeasonBudgetItem budgetItem
                        where budgetItem.harvestSeason.id = season.id
                          and budgetItem.type = br.com.gestaodireta.financial.enumeration.TransactionType.INCOME
                      ), 0) as expectedRevenue,
                      season.areaHectares as areaHectares,
                      season.status as status,
                      season.createdAt as createdAt,
                      season.updatedAt as updatedAt,
                      coalesce(sum(case
                        when transaction.type = br.com.gestaodireta.financial.enumeration.TransactionType.EXPENSE
                          and transaction.status = br.com.gestaodireta.financial.enumeration.PaymentStatus.PAID
                        then transaction.amount
                        else 0
                      end), 0) as realizedCost,
                      coalesce(sum(case
                        when transaction.type = br.com.gestaodireta.financial.enumeration.TransactionType.INCOME
                          and transaction.status = br.com.gestaodireta.financial.enumeration.PaymentStatus.PAID
                        then transaction.amount
                        else 0
                      end), 0) as realizedRevenue,
                      coalesce(sum(case
                        when transaction.type = br.com.gestaodireta.financial.enumeration.TransactionType.EXPENSE
                          and transaction.status = br.com.gestaodireta.financial.enumeration.PaymentStatus.PENDING
                        then transaction.amount
                        else 0
                      end), 0) as pendingExpenses,
                      coalesce(sum(case
                        when transaction.type = br.com.gestaodireta.financial.enumeration.TransactionType.EXPENSE
                          and transaction.status = br.com.gestaodireta.financial.enumeration.PaymentStatus.OVERDUE
                        then transaction.amount
                        else 0
                      end), 0) as overdueExpenses,
                      coalesce(sum(case
                        when transaction.type = br.com.gestaodireta.financial.enumeration.TransactionType.INCOME
                          and transaction.status = br.com.gestaodireta.financial.enumeration.PaymentStatus.PENDING
                        then transaction.amount
                        else 0
                      end), 0) as pendingRevenue,
                      count(transaction) as transactionCount,
                      coalesce(sum(case
                        when transaction.type = br.com.gestaodireta.financial.enumeration.TransactionType.INCOME
                        then 1
                        else 0
                      end), 0) as incomeCount,
                      coalesce(sum(case
                        when transaction.type = br.com.gestaodireta.financial.enumeration.TransactionType.EXPENSE
                        then 1
                        else 0
                      end), 0) as expenseCount
                    from HarvestSeason season
                    join season.farm farm
                    join season.productionActivity productionActivity
                    left join br.com.gestaodireta.financial.entity.FinancialTransaction transaction
                      on transaction.harvestSeason.id = season.id
                      and transaction.recordStatus = br.com.gestaodireta.financial.enumeration.FinancialRecordStatus.ACTIVE
                      and transaction.status in (
                        br.com.gestaodireta.financial.enumeration.PaymentStatus.PAID,
                        br.com.gestaodireta.financial.enumeration.PaymentStatus.PENDING,
                        br.com.gestaodireta.financial.enumeration.PaymentStatus.OVERDUE
                      )
                    where farm.id = :farmId
                      and (:filterStatuses = false or season.status in :statuses)
                      and (:filterStatuses = true
                        or season.status <> br.com.gestaodireta.harvest.enumeration.HarvestSeasonStatus.INACTIVE)
                      and (:filterProductionActivityIds = false
                        or productionActivity.id in :productionActivityIds)
                      and (:filterPeriodStart = false or season.endDate is null or season.endDate >= :periodStart)
                      and (:filterPeriodEnd = false or season.startDate <= :periodEnd)
                      and (:search is null
                        or lower(season.name) like concat('%', cast(:search as string), '%')
                        or lower(coalesce(season.description, ' ')) like concat('%', cast(:search as string), '%')
                        or lower(productionActivity.name) like concat('%', cast(:search as string), '%'))
                    group by
                      season.id,
                      farm.id,
                      farm.name,
                      productionActivity.id,
                      productionActivity.name,
                      season.name,
                      season.description,
                      season.startDate,
                      season.endDate,
                      season.areaHectares,
                      season.status,
                      season.createdAt,
                      season.updatedAt
                    """,
            countQuery =
                    """
                    select count(season)
                    from HarvestSeason season
                    join season.productionActivity productionActivity
                    where season.farm.id = :farmId
                      and (:filterStatuses = false or season.status in :statuses)
                      and (:filterStatuses = true
                        or season.status <> br.com.gestaodireta.harvest.enumeration.HarvestSeasonStatus.INACTIVE)
                      and (:filterProductionActivityIds = false
                        or productionActivity.id in :productionActivityIds)
                      and (:filterPeriodStart = false or season.endDate is null or season.endDate >= :periodStart)
                      and (:filterPeriodEnd = false or season.startDate <= :periodEnd)
                      and (:search is null
                        or lower(season.name) like concat('%', cast(:search as string), '%')
                        or lower(coalesce(season.description, ' ')) like concat('%', cast(:search as string), '%')
                        or lower(productionActivity.name) like concat('%', cast(:search as string), '%'))
                    """)
    Page<HarvestSeasonSummaryListProjection> findSummaryList(
            @Param("farmId") Long farmId,
            @Param("filterStatuses") boolean filterStatuses,
            @Param("statuses") Collection<HarvestSeasonStatus> statuses,
            @Param("filterProductionActivityIds") boolean filterProductionActivityIds,
            @Param("productionActivityIds") Collection<Long> productionActivityIds,
            @Param("filterPeriodStart") boolean filterPeriodStart,
            @Param("periodStart") LocalDate periodStart,
            @Param("filterPeriodEnd") boolean filterPeriodEnd,
            @Param("periodEnd") LocalDate periodEnd,
            @Param("search") String search,
            Pageable pageable);

    @Query(
            """
            select season
            from HarvestSeason season
            join fetch season.productionActivity
            where season.farm.id = :farmId
              and (:filterStatuses = false or season.status in :statuses)
              and (:filterStatuses = true
                or season.status <> br.com.gestaodireta.harvest.enumeration.HarvestSeasonStatus.INACTIVE)
              and (:filterProductionActivityIds = false
                or season.productionActivity.id in :productionActivityIds)
              and (:filterPeriodStart = false or season.endDate is null or season.endDate >= :periodStart)
              and (:filterPeriodEnd = false or season.startDate <= :periodEnd)
              and (:search is null
                or lower(season.name) like concat('%', cast(:search as string), '%')
                or lower(coalesce(season.description, ' ')) like concat('%', cast(:search as string), '%')
                or lower(season.productionActivity.name) like concat('%', cast(:search as string), '%'))
            """)
    List<HarvestSeason> findAllForFinancialSummary(
            @Param("farmId") Long farmId,
            @Param("filterStatuses") boolean filterStatuses,
            @Param("statuses") Collection<HarvestSeasonStatus> statuses,
            @Param("filterProductionActivityIds") boolean filterProductionActivityIds,
            @Param("productionActivityIds") Collection<Long> productionActivityIds,
            @Param("filterPeriodStart") boolean filterPeriodStart,
            @Param("periodStart") LocalDate periodStart,
            @Param("filterPeriodEnd") boolean filterPeriodEnd,
            @Param("periodEnd") LocalDate periodEnd,
            @Param("search") String search);

    @Query(
            """
            select season
            from HarvestSeason season
            join fetch season.farm
            join fetch season.productionActivity productionActivity
            where season.farm.id = :farmId
              and season.status = br.com.gestaodireta.harvest.enumeration.HarvestSeasonStatus.IN_PROGRESS
              and productionActivity.farm.id = season.farm.id
            order by season.startDate desc, season.createdAt desc, season.id desc
            """)
    List<HarvestSeason> findDashboardInProgressByFarmId(
            @Param("farmId") Long farmId, Pageable pageable);

    @Query(
            """
            select season
            from HarvestSeason season
            join fetch season.farm
            join fetch season.productionActivity
            where season.id = :seasonId
            """)
    Optional<HarvestSeason> findByIdWithRelations(@Param("seasonId") Long seasonId);

    @Query(
            """
            select season.farm.id as farmId, season.status as status
            from HarvestSeason season
            where season.id = :seasonId
            """)
    Optional<HarvestSeasonAccessProjection> findAccessById(@Param("seasonId") Long seasonId);

    @Query(
            """
            select count(season) > 0
            from HarvestSeason season
            where season.farm.id = :farmId
              and lower(trim(season.name)) = :normalizedName
            """)
    boolean existsByFarmIdAndNormalizedName(
            @Param("farmId") Long farmId, @Param("normalizedName") String normalizedName);

    @Query(
            """
            select count(season) > 0
            from HarvestSeason season
            where season.farm.id = :farmId
              and season.id <> :seasonId
              and lower(trim(season.name)) = :normalizedName
            """)
    boolean existsByFarmIdAndNormalizedNameAndIdNot(
            @Param("farmId") Long farmId,
            @Param("seasonId") Long seasonId,
            @Param("normalizedName") String normalizedName);
}
