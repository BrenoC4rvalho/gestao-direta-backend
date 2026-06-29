package br.com.gestaodireta.financial.repository;

import br.com.gestaodireta.financial.entity.FinancialCategory;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FinancialCategoryRepository extends JpaRepository<FinancialCategory, Long> {

    @Query(
            """
            select category
            from FinancialCategory category
            where (
                    :includeInactive = true
                    or category.status = br.com.gestaodireta.financial.enumeration.FinancialCategoryStatus.ACTIVE
              )
              and (
                    (category.farm is null and category.defaultCategory = true)
                    or category.farm.id = :farmId
              )
            """)
    Page<FinancialCategory> findVisibleByFarmId(
            @Param("farmId") Long farmId,
            @Param("includeInactive") boolean includeInactive,
            Pageable pageable);

    @Query(
            """
            select category
            from FinancialCategory category
            where category.farm is null
              and category.defaultCategory = true
            """)
    Page<FinancialCategory> findGlobal(Pageable pageable);

    @Query(
            """
            select category.farm.id
            from FinancialCategory category
            where category.id = :categoryId
            """)
    Optional<Long> findFarmIdById(@Param("categoryId") Long categoryId);

    @Query(
            """
            select count(category) > 0
            from FinancialCategory category
            where category.farm is null
              and category.defaultCategory = true
              and lower(trim(category.name)) = :normalizedName
            """)
    boolean existsGlobalByNormalizedName(@Param("normalizedName") String normalizedName);

    @Query(
            """
            select count(category) > 0
            from FinancialCategory category
            where category.farm is null
              and category.defaultCategory = true
              and category.id <> :categoryId
              and lower(trim(category.name)) = :normalizedName
            """)
    boolean existsGlobalByNormalizedNameAndIdNot(
            @Param("normalizedName") String normalizedName, @Param("categoryId") Long categoryId);

    @Query(
            """
            select count(category) > 0
            from FinancialCategory category
            where category.farm.id = :farmId
              and lower(trim(category.name)) = :normalizedName
            """)
    boolean existsFarmByNormalizedName(
            @Param("farmId") Long farmId, @Param("normalizedName") String normalizedName);

    @Query(
            """
            select count(category) > 0
            from FinancialCategory category
            where category.farm.id = :farmId
              and category.id <> :categoryId
              and lower(trim(category.name)) = :normalizedName
            """)
    boolean existsFarmByNormalizedNameAndIdNot(
            @Param("farmId") Long farmId,
            @Param("normalizedName") String normalizedName,
            @Param("categoryId") Long categoryId);
}
