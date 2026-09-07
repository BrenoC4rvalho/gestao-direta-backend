package br.com.gestaodireta.harvest.repository;

import br.com.gestaodireta.harvest.entity.HarvestSeasonBudgetItem;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HarvestSeasonBudgetItemRepository
        extends JpaRepository<HarvestSeasonBudgetItem, Long> {

    @Query(
            """
            select item
            from HarvestSeasonBudgetItem item
            left join fetch item.category
            where item.harvestSeason.id = :seasonId
            order by item.type, item.category.name, item.id
            """)
    List<HarvestSeasonBudgetItem> findAllByHarvestSeasonId(@Param("seasonId") Long seasonId);

    @Query(
            """
            select item
            from HarvestSeasonBudgetItem item
            left join fetch item.category
            join fetch item.harvestSeason
            where item.id = :id
              and item.harvestSeason.id = :seasonId
            """)
    Optional<HarvestSeasonBudgetItem> findByIdAndHarvestSeasonId(
            @Param("id") Long id, @Param("seasonId") Long seasonId);

    @Query(
            """
            select item
            from HarvestSeasonBudgetItem item
            where item.harvestSeason.id in :seasonIds
            """)
    List<HarvestSeasonBudgetItem> findAllByHarvestSeasonIdIn(
            @Param("seasonIds") List<Long> seasonIds);
}
