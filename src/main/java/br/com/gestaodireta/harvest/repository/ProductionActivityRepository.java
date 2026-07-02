package br.com.gestaodireta.harvest.repository;

import br.com.gestaodireta.harvest.entity.ProductionActivity;
import br.com.gestaodireta.harvest.enumeration.ProductionActivityStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductionActivityRepository extends JpaRepository<ProductionActivity, Long> {

    Page<ProductionActivity> findByStatus(ProductionActivityStatus status, Pageable pageable);

    @Query(
            """
            select count(activity) > 0
            from ProductionActivity activity
            where lower(trim(activity.name)) = :normalizedName
            """)
    boolean existsByNormalizedName(@Param("normalizedName") String normalizedName);

    @Query(
            """
            select count(activity) > 0
            from ProductionActivity activity
            where activity.id <> :activityId
              and lower(trim(activity.name)) = :normalizedName
            """)
    boolean existsByNormalizedNameAndIdNot(
            @Param("normalizedName") String normalizedName, @Param("activityId") Long activityId);
}
