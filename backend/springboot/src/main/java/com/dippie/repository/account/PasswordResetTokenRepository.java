package com.dippie.repository.account;

import com.dippie.model.account.PasswordResetToken;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

// repo bảng password_reset_tokens, có khóa PESSIMISTIC_WRITE lúc xác nhận token
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    // khóa dòng theo tokenHash chặn hai request song song cùng xài chung một link đặt lại
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from PasswordResetToken t where t.tokenHash = :tokenHash")
    Optional<PasswordResetToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    List<PasswordResetToken> findByUserId(Long userId);

    void deleteByUserId(Long userId);

    // một token dùng xong thì khóa nốt mấy token chưa dùng còn lại của cùng user
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
