package br.com.gestaodireta.financial.repository;

import br.com.gestaodireta.financial.entity.FinancialCategory;
import br.com.gestaodireta.financial.enumeration.FinancialCategoryStatus;
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
            where category.status = :status
              and (
                    (category.farm is null and category.defaultCategory = true)
                    or category.farm.id = :farmId
              )
            """)
    Page<FinancialCategory> findVisibleByFarmId(
            @Param("farmId") Long farmId,
            @Param("status") FinancialCategoryStatus status,
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
}
