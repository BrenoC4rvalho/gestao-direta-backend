package br.com.gestaodireta.harvest.repository;

import br.com.gestaodireta.harvest.entity.HarvestSeason;
import br.com.gestaodireta.harvest.enumeration.HarvestSeasonStatus;
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
                      and (:includeInactive = true
                        or season.status <> br.com.gestaodireta.harvest.enumeration.HarvestSeasonStatus.INACTIVE)
                    """,
            countQuery =
                    """
                    select count(season)
                    from HarvestSeason season
                    where season.farm.id = :farmId
                      and (:includeInactive = true
                        or season.status <> br.com.gestaodireta.harvest.enumeration.HarvestSeasonStatus.INACTIVE)
                    """)
    Page<HarvestSeason> findByFarmId(
            @Param("farmId") Long farmId,
            @Param("includeInactive") boolean includeInactive,
            Pageable pageable);

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
              and season.status <> :inactiveStatus
            """)
    boolean existsActiveByFarmIdAndNormalizedName(
            @Param("farmId") Long farmId,
            @Param("normalizedName") String normalizedName,
            @Param("inactiveStatus") HarvestSeasonStatus inactiveStatus);

    @Query(
            """
            select count(season) > 0
            from HarvestSeason season
            where season.farm.id = :farmId
              and season.id <> :seasonId
              and lower(trim(season.name)) = :normalizedName
              and season.status <> :inactiveStatus
            """)
    boolean existsActiveByFarmIdAndNormalizedNameAndIdNot(
            @Param("farmId") Long farmId,
            @Param("seasonId") Long seasonId,
            @Param("normalizedName") String normalizedName,
            @Param("inactiveStatus") HarvestSeasonStatus inactiveStatus);
}
