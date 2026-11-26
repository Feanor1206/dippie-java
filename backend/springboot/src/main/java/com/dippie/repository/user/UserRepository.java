package com.dippie.repository.user;

import com.dippie.model.user.Role;
import com.dippie.model.user.User;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

// repo JPA bảng users, có thêm mấy câu khóa PESSIMISTIC_WRITE chặn race condition
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    List<User> findByUsernameOrEmailOrderByIdAsc(String username, String email);

    List<User> findByEmailIgnoreCaseOrderByIdAsc(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, Long id);

    List<User> findByRole(Role role);

    Optional<User> findByUsernameAndEmailAndRole(String username, String email, Role role);

    // dùng locate thay vì like để khỏi phải escape % hay _ lúc admin tìm user
    @Query("""
            select u from User u
            where :term is null
               or locate(:term, lower(u.username)) > 0
               or locate(:term, lower(u.email)) > 0
            order by u.id asc
            """)
    Page<User> searchByUsernameOrEmail(@Param("term") String term, Pageable pageable);

    // khóa ghi theo id tăng dần để AdminUserService với AccountErasureService luôn khóa cùng chiều, tránh deadlock
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.role = :role and u.enabled = true order by u.id asc")
    List<User> findEnabledByRoleForUpdate(@Param("role") Role role);

    // khóa ghi đúng dòng user lúc sửa tokenVersion hay xóa tài khoản cho an toàn khi nhiều request đụng nhau
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);
}
