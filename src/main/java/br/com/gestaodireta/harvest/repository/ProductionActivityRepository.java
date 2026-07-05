package br.com.gestaodireta.harvest.repository;

import br.com.gestaodireta.harvest.entity.ProductionActivity;
import br.com.gestaodireta.harvest.enumeration.ProductionActivityStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductionActivityRepository extends JpaRepository<ProductionActivity, Long> {

    @Query(
            value =
                    """
                    select activity
                    from ProductionActivity activity
                    join fetch activity.farm
                    where activity.farm.id = :farmId
                      and (:status is null or activity.status = :status)
                    """,
            countQuery =
                    """
                    select count(activity)
                    from ProductionActivity activity
                    where activity.farm.id = :farmId
                      and (:status is null or activity.status = :status)
                    """)
    Page<ProductionActivity> findByFarmIdAndStatus(
            @Param("farmId") Long farmId,
            @Param("status") ProductionActivityStatus status,
            Pageable pageable);

    @Query(
            """
            select activity
            from ProductionActivity activity
            join fetch activity.farm
            where activity.id = :activityId
            """)
    java.util.Optional<ProductionActivity> findByIdWithFarm(@Param("activityId") Long activityId);

    @Query(
            """
            select activity.farm.id
            from ProductionActivity activity
            where activity.id = :activityId
            """)
    java.util.Optional<Long> findFarmIdById(@Param("activityId") Long activityId);

    @Query(
            """
            select count(activity) > 0
            from ProductionActivity activity
            where activity.farm.id = :farmId
              and lower(trim(activity.name)) = :normalizedName
            """)
    boolean existsByFarmIdAndNormalizedName(
            @Param("farmId") Long farmId, @Param("normalizedName") String normalizedName);

    @Query(
            """
            select count(activity) > 0
            from ProductionActivity activity
            where activity.farm.id = :farmId
              and activity.id <> :activityId
              and lower(trim(activity.name)) = :normalizedName
            """)
    boolean existsByFarmIdAndNormalizedNameAndIdNot(
            @Param("farmId") Long farmId,
            @Param("activityId") Long activityId,
            @Param("normalizedName") String normalizedName);

    long countByFarmId(Long farmId);

    long countByFarmIdAndStatus(Long farmId, ProductionActivityStatus status);

    @Query(
            """
            select count(distinct activity.id)
            from ProductionActivity activity
            join HarvestSeason season on season.productionActivity.id = activity.id
            where activity.farm.id = :farmId
              and season.farm.id = :farmId
              and season.status = br.com.gestaodireta.harvest.enumeration.HarvestSeasonStatus.IN_PROGRESS
            """)
    long countDistinctInProgressByFarmId(@Param("farmId") Long farmId);
}
