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

// Repository JPA cho bảng users, có thêm truy vấn khóa ghi PESSIMISTIC_WRITE để chặn race condition.
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    List<User> findByUsernameOrEmailOrderByIdAsc(String username, String email);

    List<User> findByEmailIgnoreCaseOrderByIdAsc(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, Long id);

    List<User> findByRole(Role role);

    Optional<User> findByUsernameAndEmailAndRole(String username, String email, Role role);

    // Dùng locate thay vì like để khỏi phải escape ký tự % hay _ khi admin tìm kiếm người dùng.
    @Query("""
            select u from User u
            where :term is null
               or locate(:term, lower(u.username)) > 0
               or locate(:term, lower(u.email)) > 0
            order by u.id asc
            """)
    Page<User> searchByUsernameOrEmail(@Param("term") String term, Pageable pageable);

    // Khóa ghi theo thứ tự id tăng dần để AdminUserService và AccountErasureService luôn khóa cùng chiều, tránh deadlock.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.role = :role and u.enabled = true order by u.id asc")
    List<User> findEnabledByRoleForUpdate(@Param("role") Role role);

    // Khóa ghi một dòng user khi cần sửa tokenVersion hoặc xóa tài khoản an toàn dưới nhiều request song song.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);
}
