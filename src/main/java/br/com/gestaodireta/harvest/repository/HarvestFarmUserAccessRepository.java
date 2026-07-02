package br.com.gestaodireta.harvest.repository;

import br.com.gestaodireta.farm.entity.FarmUser;
import br.com.gestaodireta.farm.enumeration.FarmUserRole;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface HarvestFarmUserAccessRepository extends Repository<FarmUser, Long> {

    @Query(
            """
            select count(fu) > 0
            from FarmUser fu
            where fu.user.id = :userId
              and fu.user.status = br.com.gestaodireta.user.enumeration.UserStatus.ACTIVE
              and fu.farm.status = br.com.gestaodireta.farm.enumeration.FarmStatus.ACTIVE
              and fu.role <> br.com.gestaodireta.farm.enumeration.FarmUserRole.INACTIVE
            """)
    boolean existsActiveFarmAccessByUserId(@Param("userId") Long userId);

    @Query(
            """
            select fu.role
            from FarmUser fu
            where fu.farm.id = :farmId
              and fu.user.id = :userId
            """)
    Optional<FarmUserRole> findRoleByFarmIdAndUserId(
            @Param("farmId") Long farmId, @Param("userId") Long userId);
}
