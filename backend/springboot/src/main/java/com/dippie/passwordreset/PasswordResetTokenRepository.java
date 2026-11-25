package com.dippie.passwordreset;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// Repository cho bảng password_reset_tokens, dùng khóa PESSIMISTIC_WRITE lúc xác nhận token.
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    // Khóa dòng theo tokenHash để chặn hai request gửi song song cùng tiêu thụ một đường dẫn đặt lại.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from PasswordResetToken t where t.tokenHash = :tokenHash")
    Optional<PasswordResetToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    List<PasswordResetToken> findByUserId(Long userId);

    void deleteByUserId(Long userId);

    // Khi một token được dùng thành công, đánh dấu hết các token chưa dùng còn lại của cùng tài khoản.
    @Modifying(flushAutomatically = true)
    @Query("""
            update PasswordResetToken t
            set t.usedAt = :usedAt
            where t.userId = :userId and t.usedAt is null and t.id <> :consumedId
            """)
    int invalidateOtherActiveTokens(
            @Param("userId") Long userId,
            @Param("consumedId") Long consumedId,
            @Param("usedAt") LocalDateTime usedAt
    );
}
