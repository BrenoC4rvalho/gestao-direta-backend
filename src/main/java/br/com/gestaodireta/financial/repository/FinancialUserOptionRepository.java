package br.com.gestaodireta.financial.repository;

import br.com.gestaodireta.farm.enumeration.FarmUserRole;
import br.com.gestaodireta.financial.enumeration.FinancialRecordStatus;
import br.com.gestaodireta.user.dto.UserOptionResponse;
import br.com.gestaodireta.user.entity.User;
import br.com.gestaodireta.user.enumeration.UserStatus;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface FinancialUserOptionRepository extends Repository<User, Long> {

    @Query(
            """
            select distinct new br.com.gestaodireta.user.dto.UserOptionResponse(u.id, u.name)
            from User u
            where exists (
                select 1
                from FarmUser farmUser
                where farmUser.user = u
                  and farmUser.farm.id = :farmId
                  and farmUser.role <> :inactiveRole
                  and u.status = :activeStatus
            )
            or exists (
                select 1
                from FinancialTransaction t
                where t.createdByUser = u
                  and t.farm.id = :farmId
                  and t.recordStatus = :activeRecordStatus
            )
            order by u.name asc
            """)
    List<UserOptionResponse> findUserOptionsForTransactionFilter(
            @Param("farmId") Long farmId,
            @Param("inactiveRole") FarmUserRole inactiveRole,
            @Param("activeStatus") UserStatus activeStatus,
            @Param("activeRecordStatus") FinancialRecordStatus activeRecordStatus);
}
