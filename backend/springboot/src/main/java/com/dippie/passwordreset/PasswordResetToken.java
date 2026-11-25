package com.dippie.passwordreset;

import com.dippie.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

// Bảng password_reset_tokens lưu mã băm SHA-256 và hạn dùng của token đặt lại mật khẩu để DB không bao giờ chứa token gốc.
// Khóa ngoại tới User đặt CASCADE để khi xóa tài khoản thì token cũng được dọn sạch theo.
@Entity
@Table(
        name = "password_reset_tokens",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_password_reset_token_hash", columnNames = {"token_hash"})
        },
        indexes = {
                @Index(name = "idx_password_reset_user", columnList = "user_id"),
                @Index(name = "idx_password_reset_expires", columnList = "expires_at")
        }
)
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "token_hash", length = 64, nullable = false)
    private String tokenHash;

    @Column(name = "expires_at", columnDefinition = "TIMESTAMP", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "used_at", columnDefinition = "TIMESTAMP")
    private LocalDateTime usedAt;

    @Column(name = "created_at", columnDefinition = "TIMESTAMP", nullable = false, updatable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "user_id",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_password_reset_user")
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    protected PasswordResetToken() {
    }

    public PasswordResetToken(Long userId, String tokenHash, LocalDateTime expiresAt, LocalDateTime createdAt) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public LocalDateTime getUsedAt() {
        return usedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    // Coi như hết hạn ngay khi chạm mốc expiresAt (không cho dùng ở đúng phút thứ 30).
    public boolean isExpiredAt(LocalDateTime now) {
        return !now.isBefore(expiresAt);
    }

    // Ghi nhận thời điểm tiêu thụ để token không dùng lại được lần hai.
    public void markUsed(LocalDateTime usedAt) {
        this.usedAt = usedAt;
    }
}
