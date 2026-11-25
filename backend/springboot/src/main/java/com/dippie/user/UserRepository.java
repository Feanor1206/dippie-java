package com.dippie.user;

import com.dippie.security.Role;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    List<User> findByUsernameOrEmailOrderByIdAsc(String username, String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, Long id);

    List<User> findByRole(Role role);

    Optional<User> findByUsernameAndEmailAndRole(String username, String email, Role role);

    @Query("""
            select u from User u
            where :term is null
               or locate(:term, lower(u.username)) > 0
               or locate(:term, lower(u.email)) > 0
            order by u.id asc
            """)
    Page<User> searchByUsernameOrEmail(@Param("term") String term, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.role = :role and u.enabled = true order by u.id asc")
    List<User> findEnabledByRoleForUpdate(@Param("role") Role role);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);
}
