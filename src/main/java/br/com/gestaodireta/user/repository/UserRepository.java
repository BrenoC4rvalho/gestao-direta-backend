package br.com.gestaodireta.user.repository;

import br.com.gestaodireta.user.entity.User;
import br.com.gestaodireta.user.enumeration.UserStatus;
import br.com.gestaodireta.user.enumeration.UserType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByEmail(String email);

    Optional<User> findByEmail(String email);

    Optional<User> findByEmailIgnoreCase(String email);

    @Query(
            """
            select u
            from User u
            where (:search is null
                or lower(u.name) like concat('%', cast(:search as string), '%')
                or lower(u.email) like concat('%', cast(:search as string), '%'))
              and (:userType is null or u.userType = :userType)
              and (:status is null or u.status = :status)
            """)
    Page<User> findAllFiltered(
            @Param("search") String search,
            @Param("userType") UserType userType,
            @Param("status") UserStatus status,
            Pageable pageable);
}
