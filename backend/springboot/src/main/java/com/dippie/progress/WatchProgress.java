package com.dippie.progress;

import com.dippie.catalog.domain.CatalogEpisode;
import com.dippie.common.error.ConflictException;
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
import org.hibernate.annotations.Check;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

// Entity ánh xạ bảng watch_progress, giữ vị trí xem từng tập cùng cột optLockVersion cho optimistic locking.
// Khi client gửi expectedVersion lệch với optLockVersion đang lưu, lệnh cập nhật sẽ bị từ chối với lỗi 409 để hai tab không ghi đè lẫn nhau.
@Entity
@Table(
        name = "watch_progress",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_watch_progress_user_episode", columnNames = {"user_id", "episode_id"})
        },
        indexes = {
                @Index(name = "idx_watch_progress_user_updated", columnList = "user_id, updated_at"),
                @Index(name = "idx_watch_progress_episode", columnList = "episode_id")
        }
)
@Check(name = "ck_watch_progress_position", constraints = "position_ms >= 0")
public class WatchProgress {

    public static final String CONFLICT_MESSAGE = "Tiến độ xem vừa thay đổi ở nơi khác. Bạn hãy tải lại trang rồi thử lại.";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "episode_id", length = 100, nullable = false)
    private String episodeId;

    @Column(name = "asset_id", length = 100, nullable = false)
    private String assetId;

    @Column(name = "position_ms", nullable = false)
    private long positionMs;

    @Column(name = "opt_lock_version", nullable = false)
    @ColumnDefault("0")
    private long optLockVersion;

    @Column(name = "updated_at", columnDefinition = "TIMESTAMP", nullable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "user_id",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_watch_progress_user")
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "episode_id",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_watch_progress_episode")
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
    private CatalogEpisode episode;

    protected WatchProgress() {
    }

    private WatchProgress(Long userId, String episodeId, String assetId, long positionMs, long optLockVersion,
            LocalDateTime updatedAt) {
        this.userId = userId;
        this.episodeId = episodeId;
        this.assetId = assetId;
        this.positionMs = positionMs;
        this.optLockVersion = optLockVersion;
        this.updatedAt = updatedAt;
    }

    // Tạo bản ghi mới ở version 0 với thời điểm hiện tại đã cắt xuống từng giây cho khớp cột TIMESTAMP.
    public static WatchProgress start(Long userId, String episodeId, String assetId, long positionMs, Clock clock) {
        return startAt(userId, episodeId, assetId, positionMs, nowFrom(clock));
    }

    // Bản nạp chồng nhận sẵn mốc updatedAt, hay dùng trong test và phần seed dữ liệu.
    public static WatchProgress startAt(Long userId, String episodeId, String assetId, long positionMs,
            LocalDateTime updatedAt) {
        return new WatchProgress(userId, episodeId, assetId, positionMs, 0L, updatedAt);
    }

    // Chỉ cập nhật khi expectedVersion khớp với optLockVersion đang giữ, rồi tăng version thêm 1.
    public void advance(long expectedVersion, String newAssetId, long newPositionMs, Clock clock) {
        if (this.optLockVersion != expectedVersion) {
            throw new ConflictException(CONFLICT_MESSAGE);
        }
        this.assetId = newAssetId;
        this.positionMs = newPositionMs;
        this.optLockVersion = expectedVersion + 1;
        this.updatedAt = nowFrom(clock);
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getEpisodeId() {
        return episodeId;
    }

    public String getAssetId() {
        return assetId;
    }

    public long getPositionMs() {
        return positionMs;
    }

    public long getOptLockVersion() {
        return optLockVersion;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    private static LocalDateTime nowFrom(Clock clock) {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
    }
}
