package br.com.gestaodireta.farm.repository;

import br.com.gestaodireta.farm.entity.Farm;
import br.com.gestaodireta.farm.entity.FarmUser;
import br.com.gestaodireta.farm.enumeration.FarmStatus;
import br.com.gestaodireta.farm.enumeration.FarmUserRole;
import br.com.gestaodireta.farm.enumeration.ProductionType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FarmUserRepository extends JpaRepository<FarmUser, Long> {

    boolean existsByFarmIdAndUserId(Long farmId, Long userId);

    @Query(
            """
            select count(fu) > 0
            from FarmUser fu
            where fu.user.id = :userId
              and fu.user.status = br.com.gestaodireta.user.enumeration.UserStatus.ACTIVE
              and fu.farm.status = br.com.gestaodireta.farm.enumeration.FarmStatus.ACTIVE
              and fu.role = br.com.gestaodireta.farm.enumeration.FarmUserRole.PRODUCER
            """)
    boolean existsActiveProducerByUserId(@Param("userId") Long userId);

    Optional<FarmUser> findByFarmIdAndUserId(Long farmId, Long userId);

    List<FarmUser> findByFarmId(Long farmId);

    long countByFarmIdAndRole(Long farmId, FarmUserRole role);

    @Query(
            """
            select fu.role
            from FarmUser fu
            where fu.farm.id = :farmId
              and fu.user.id = :userId
            """)
    Optional<FarmUserRole> findRoleByFarmIdAndUserId(
            @Param("farmId") Long farmId, @Param("userId") Long userId);

    @Query(
            """
            select fu.farm
            from FarmUser fu
            where fu.user.id = :userId
              and fu.user.status = br.com.gestaodireta.user.enumeration.UserStatus.ACTIVE
              and fu.farm.status = :farmStatus
              and fu.role <> :inactiveRole
            """)
    Page<Farm> findActiveFarmsByUserId(
            @Param("userId") Long userId,
            @Param("farmStatus") FarmStatus farmStatus,
            @Param("inactiveRole") FarmUserRole inactiveRole,
            Pageable pageable);

    @Query(
            """
            select fu.farm
            from FarmUser fu
            where fu.user.id = :userId
              and fu.user.status = br.com.gestaodireta.user.enumeration.UserStatus.ACTIVE
              and fu.farm.status = :farmStatus
              and fu.role <> :inactiveRole
              and (:search is null or lower(fu.farm.name) like concat('%', cast(:search as string), '%'))
              and (:document is null
                or cast(function('regexp_replace', coalesce(fu.farm.document, ''), '[^0-9]', '', 'g') as string)
                    like concat('%', cast(:document as string), '%'))
              and (:filterProductionTypes = false or fu.farm.productionType in :productionTypes)
            """)
    Page<Farm> findActiveFarmsByUserIdFiltered(
            @Param("userId") Long userId,
            @Param("farmStatus") FarmStatus farmStatus,
            @Param("inactiveRole") FarmUserRole inactiveRole,
            @Param("search") String search,
            @Param("document") String document,
            @Param("filterProductionTypes") boolean filterProductionTypes,
            @Param("productionTypes") List<ProductionType> productionTypes,
            Pageable pageable);

    @Query(
            value =
                    """
                    select fu
                    from FarmUser fu
                    join fetch fu.farm
                    join fetch fu.user
                    where fu.farm.id = :farmId
                      and (:search is null
                        or lower(fu.user.name) like concat('%', cast(:search as string), '%')
                        or lower(fu.user.email) like concat('%', cast(:search as string), '%'))
                      and (:filterRoles = false or fu.role in :roles)
                    """,
            countQuery =
                    """
                    select count(fu)
                    from FarmUser fu
                    where fu.farm.id = :farmId
                      and (:search is null
                        or lower(fu.user.name) like concat('%', cast(:search as string), '%')
                        or lower(fu.user.email) like concat('%', cast(:search as string), '%'))
                      and (:filterRoles = false or fu.role in :roles)
                    """)
    Page<FarmUser> findByFarmFiltered(
            @Param("farmId") Long farmId,
            @Param("search") String search,
            @Param("filterRoles") boolean filterRoles,
            @Param("roles") List<FarmUserRole> roles,
            Pageable pageable);
}
