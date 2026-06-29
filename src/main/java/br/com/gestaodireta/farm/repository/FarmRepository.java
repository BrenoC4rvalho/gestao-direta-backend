package br.com.gestaodireta.farm.repository;

import br.com.gestaodireta.farm.entity.Farm;
import br.com.gestaodireta.farm.enumeration.FarmStatus;
import br.com.gestaodireta.farm.enumeration.ProductionType;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FarmRepository extends JpaRepository<Farm, Long> {

    @Query(
            """
            select f
            from Farm f
            where (:search is null or lower(f.name) like concat('%', cast(:search as string), '%'))
              and (:document is null
                or cast(function('regexp_replace', coalesce(f.document, ''), '[^0-9]', '', 'g') as string)
                    like concat('%', cast(:document as string), '%'))
              and (:filterProductionTypes = false or f.productionType in :productionTypes)
              and (:status is null or f.status = :status)
            """)
    Page<Farm> findAllFilteredForAdmin(
            @Param("search") String search,
            @Param("document") String document,
            @Param("filterProductionTypes") boolean filterProductionTypes,
            @Param("productionTypes") List<ProductionType> productionTypes,
            @Param("status") FarmStatus status,
            Pageable pageable);
}
