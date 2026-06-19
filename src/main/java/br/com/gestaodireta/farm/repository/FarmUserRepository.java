package br.com.gestaodireta.farm.repository;

import br.com.gestaodireta.farm.entity.Farm;
import br.com.gestaodireta.farm.entity.FarmUser;
import br.com.gestaodireta.farm.enumeration.FarmStatus;
import br.com.gestaodireta.farm.enumeration.FarmUserRole;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FarmUserRepository extends JpaRepository<FarmUser, Long> {

    boolean existsByFarmIdAndUserId(Long farmId, Long userId);

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
}
