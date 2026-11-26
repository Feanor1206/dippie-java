package com.dippie.model.progress;

import com.dippie.exception.ConflictException;
import com.dippie.model.catalog.CatalogEpisode;
import com.dippie.model.profile.ChildProfile;
import com.dippie.model.user.User;

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

// bảng watch_progress lưu mốc xem từng tập kèm optLockVersion cho optimistic locking
// client gửi expectedVersion lệch với optLockVersion đang lưu thì trả 409 luôn kẻo hai tab đè nhau
@Entity
@Table(
        name = "watch_progress",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_watch_progress_user_profile_episode", columnNames = {"user_id", "profile_scope_id", "episode_id"})
        },
        indexes = {
                @Index(name = "idx_watch_progress_user_updated", columnList = "user_id, updated_at"),
                @Index(name = "idx_watch_progress_user_profile_updated", columnList = "user_id, profile_scope_id, updated_at"),
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

    @Column(name = "profile_id")
    private Long profileId;

    @Column(name = "profile_scope_id", nullable = false)
    @ColumnDefault("0")
    private long profileScopeId;

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
            name = "profile_id",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_watch_progress_profile")
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ChildProfile profile;

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

    private WatchProgress(Long userId, Long profileId, String episodeId, String assetId, long positionMs,
            long optLockVersion, LocalDateTime updatedAt) {
        this.userId = userId;
        this.profileId = profileId;
        this.profileScopeId = profileId == null ? 0L : profileId;
        this.episodeId = episodeId;
        this.assetId = assetId;
        this.positionMs = positionMs;
        this.optLockVersion = optLockVersion;
        this.updatedAt = updatedAt;
    }

    // tạo bản ghi mới ở version 0 cho hồ sơ chính, cắt giờ hiện tại xuống giây cho khớp TIMESTAMP
    public static WatchProgress start(Long userId, String episodeId, String assetId, long positionMs, Clock clock) {
        return start(userId, null, episodeId, assetId, positionMs, clock);
    }

    // tạo bản ghi mới ở version 0 gắn profileId (null là hồ sơ chính)
    public static WatchProgress start(Long userId, Long profileId, String episodeId, String assetId, long positionMs,
            Clock clock) {
        return startAt(userId, profileId, episodeId, assetId, positionMs, nowFrom(clock));
    }

    // bản overload truyền sẵn updatedAt, hay dùng cho test với seed dữ liệu
    public static WatchProgress startAt(Long userId, String episodeId, String assetId, long positionMs,
            LocalDateTime updatedAt) {
        return startAt(userId, null, episodeId, assetId, positionMs, updatedAt);
    }

    public static WatchProgress startAt(Long userId, Long profileId, String episodeId, String assetId, long positionMs,
            LocalDateTime updatedAt) {
        return new WatchProgress(userId, profileId, episodeId, assetId, positionMs, 0L, updatedAt);
    }

    // chỉ cho lưu khi expectedVersion khớp optLockVersion hiện tại, xong tăng version lên 1
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

    public Long getProfileId() {
        return profileId;
    }

    public long getProfileScopeId() {
        return profileScopeId;
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
