package br.com.gestaodireta.financial.repository;

import br.com.gestaodireta.financial.entity.FinancialCategory;
import br.com.gestaodireta.financial.enumeration.FinancialCategoryStatus;
import br.com.gestaodireta.financial.enumeration.FinancialRecordStatus;
import br.com.gestaodireta.financial.enumeration.TransactionType;
import java.util.Collection;
import java.util.List;
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
            where category.farm.id = :farmId
              and (:search is null or lower(category.name) like :search)
              and (
                    :status is not null and category.status = :status
                    or :status is null and (
                        :includeInactive = true
                        or category.status = br.com.gestaodireta.financial.enumeration.FinancialCategoryStatus.ACTIVE
                    )
              )
            """)
    Page<FinancialCategory> findByFarmIdAndFilters(
            @Param("farmId") Long farmId,
            @Param("includeInactive") boolean includeInactive,
            @Param("search") String search,
            @Param("status") FinancialCategoryStatus status,
            Pageable pageable);

    default Page<FinancialCategory> findByFarmId(
            Long farmId, boolean includeInactive, Pageable pageable) {
        return findByFarmIdAndFilters(farmId, includeInactive, null, null, pageable);
    }

    @Query(
            """
            select distinct category
            from FinancialTransaction transaction
            join transaction.category category
            join fetch category.farm
            where transaction.farm.id = :farmId
              and category.farm.id = :farmId
              and transaction.recordStatus = :recordStatus
            order by category.name asc
            """)
    List<FinancialCategory> findUsedInTransactionsByFarmId(
            @Param("farmId") Long farmId,
            @Param("recordStatus") FinancialRecordStatus recordStatus);

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
            where category.farm.id = :farmId
              and lower(trim(category.name)) = :normalizedName
              and category.type = :type
            """)
    boolean existsFarmByNormalizedNameAndType(
            @Param("farmId") Long farmId,
            @Param("normalizedName") String normalizedName,
            @Param("type") TransactionType type);

    @Query(
            """
            select count(category) > 0
            from FinancialCategory category
            where category.farm.id = :farmId
              and category.id <> :categoryId
              and lower(trim(category.name)) = :normalizedName
              and category.type = :type
            """)
    boolean existsFarmByNormalizedNameAndTypeAndIdNot(
            @Param("farmId") Long farmId,
            @Param("normalizedName") String normalizedName,
            @Param("type") TransactionType type,
            @Param("categoryId") Long categoryId);

    @Query(
            """
            select count(category)
            from FinancialCategory category
            where category.farm.id = :farmId
              and category.id in :categoryIds
            """)
    long countByFarmIdAndIdIn(
            @Param("farmId") Long farmId, @Param("categoryIds") Collection<Long> categoryIds);
}
