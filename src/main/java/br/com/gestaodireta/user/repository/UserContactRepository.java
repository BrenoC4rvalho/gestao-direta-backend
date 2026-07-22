package br.com.gestaodireta.user.repository;

import br.com.gestaodireta.user.entity.UserContact;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserContactRepository extends JpaRepository<UserContact, Long> {
    Optional<UserContact> findByUserId(Long userId);

    boolean existsByPhoneNumberAndIdNot(String phoneNumber, Long id);
}
