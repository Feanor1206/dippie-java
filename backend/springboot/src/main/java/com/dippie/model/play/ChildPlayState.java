package com.dippie.model.play;

import com.dippie.model.profile.ChildProfile;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Duration;
import java.time.LocalDateTime;

// trạng thái chạy thực tế trên server của thú ảo canh giờ nghỉ mắt và rương báu bay ngang cho từng hồ sơ bé
@Entity
@Table(name = "child_play_state")
public class ChildPlayState {

    public static final int LOOT_MIN_THRESHOLD_SECONDS = 300;
    public static final int LOOT_MAX_THRESHOLD_SECONDS = 420;

    @Id
    @Column(name = "profile_id")
    private Long profileId;

    @Column(name = "continuous_watch_seconds", nullable = false)
    @ColumnDefault("0")
    private int continuousWatchSeconds;

    @Column(name = "last_watch_heartbeat_at", columnDefinition = "TIMESTAMP")
    private LocalDateTime lastWatchHeartbeatAt;

    @Column(name = "pet_fainted", nullable = false)
    @ColumnDefault("false")
    private boolean petFainted;

    @Column(name = "rest_lock_until", columnDefinition = "TIMESTAMP")
    private LocalDateTime restLockUntil;

    @Column(name = "watch_seconds_since_loot", nullable = false)
    @ColumnDefault("0")
    private int watchSecondsSinceLoot;

    @Column(name = "next_loot_threshold_seconds", nullable = false)
    @ColumnDefault("360")
    private int nextLootThresholdSeconds = 360;

    @Column(name = "loot_combo", nullable = false)
    @ColumnDefault("0")
    private int lootCombo;

    @Column(name = "loot_drop_pending", nullable = false)
    @ColumnDefault("false")
    private boolean lootDropPending;

    @Column(name = "last_loot_issued_at", columnDefinition = "TIMESTAMP")
    private LocalDateTime lastLootIssuedAt;

    @Column(name = "updated_at", columnDefinition = "TIMESTAMP", nullable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    private LocalDateTime updatedAt;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "profile_id",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_child_play_state_profile")
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ChildProfile profile;

    protected ChildPlayState() {
    }

    public ChildPlayState(Long profileId, int initialLootThresholdSeconds, LocalDateTime now) {
        this.profileId = profileId;
        this.nextLootThresholdSeconds = clampLootThreshold(initialLootThresholdSeconds);
        this.updatedAt = now;
    }

    public Long getProfileId() {
        return profileId;
    }

    public int getContinuousWatchSeconds() {
        return continuousWatchSeconds;
    }

    public LocalDateTime getLastWatchHeartbeatAt() {
        return lastWatchHeartbeatAt;
    }

    public boolean isPetFainted() {
        return petFainted;
    }

    public LocalDateTime getRestLockUntil() {
        return restLockUntil;
    }

    public int getWatchSecondsSinceLoot() {
        return watchSecondsSinceLoot;
    }

    public int getNextLootThresholdSeconds() {
        return nextLootThresholdSeconds;
    }

    public int getLootCombo() {
        return lootCombo;
    }

    public boolean isLootDropPending() {
        return lootDropPending;
    }

    public LocalDateTime getLastLootIssuedAt() {
        return lastLootIssuedAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public boolean isRestingAt(LocalDateTime now) {
        return restLockUntil != null && now.isBefore(restLockUntil);
    }

    public long restRemainingSecondsAt(LocalDateTime now) {
        if (!isRestingAt(now)) {
            return 0L;
        }
        long seconds = Duration.between(now, restLockUntil).getSeconds();
        return Math.max(1L, seconds);
    }

    // kiểm tra xem bé đã nghỉ đủ 2 phút giữa 2 nhịp xem để tự xóa bộ đếm xem liên tục chưa
    public boolean hasPauseResetAt(LocalDateTime now) {
        if (petFainted || isRestingAt(now) || lastWatchHeartbeatAt == null) {
            return false;
        }
        return Duration.between(lastWatchHeartbeatAt, now).getSeconds() >= 120L;
    }

    // hết giờ nghỉ mắt thì đánh thức thú ảo và đưa bộ đếm liên tục về 0
    public boolean clearExpiredRestIfNeeded(LocalDateTime now) {
        if (restLockUntil != null && !now.isBefore(restLockUntil)) {
            this.restLockUntil = null;
            this.petFainted = false;
            this.continuousWatchSeconds = 0;
            this.updatedAt = now;
            return true;
        }
        return false;
    }

    // cập nhật bộ đếm xem liên tục cho thú ảo và đồng hồ tích lũy cho rương báu mỗi khi heartbeat hợp lệ
    public void recordWatchSeconds(int creditedSeconds, ChildPlaySettings settings, LocalDateTime now) {
        if (creditedSeconds <= 0) {
            return;
        }
        clearExpiredRestIfNeeded(now);
        if (settings.isWatchPetEnabled() && !petFainted && !isRestingAt(now)) {
            if (hasPauseResetAt(now)) {
                this.continuousWatchSeconds = 0;
            }
            this.continuousWatchSeconds += creditedSeconds;
            if (this.continuousWatchSeconds >= WatchPetStage.faintThresholdSeconds(settings.getWatchPetFaintMinutes())) {
                this.petFainted = true;
            }
        }
        if (settings.isLootDropEnabled()) {
            this.watchSecondsSinceLoot += creditedSeconds;
        }
        this.lastWatchHeartbeatAt = now;
        this.updatedAt = now;
    }

    // bé chọn cho thú ngủ nghỉ mắt: đặt mốc khóa đến now + restMinutes
    public void startRest(int restMinutes, LocalDateTime now) {
        this.petFainted = true;
        this.restLockUntil = now.plusMinutes(restMinutes);
        this.updatedAt = now;
    }

    // bé giải xong toán mua thuốc hồi máu: xóa ngất và đặt lại đồng hồ xem liên tục về 0
    public void healWithMedicine(LocalDateTime now) {
        this.petFainted = false;
        this.restLockUntil = null;
        this.continuousWatchSeconds = 0;
        this.lastWatchHeartbeatAt = now;
        this.updatedAt = now;
    }

    public boolean isLootReady() {
        return watchSecondsSinceLoot >= nextLootThresholdSeconds;
    }

    // khi phát rương mới: nếu rương trước bị bỏ qua chưa trả lời đúng thì tự xóa combo về 0
    public void markLootSpawned(int nextThresholdSeconds, LocalDateTime now) {
        if (this.lootDropPending) {
            this.lootCombo = 0;
        }
        this.watchSecondsSinceLoot = 0;
        this.nextLootThresholdSeconds = clampLootThreshold(nextThresholdSeconds);
        this.lootDropPending = true;
        this.lastLootIssuedAt = now;
        this.updatedAt = now;
    }

    public int recordLootCorrect(LocalDateTime now) {
        this.lootDropPending = false;
        this.lootCombo += 1;
        this.updatedAt = now;
        return this.lootCombo;
    }

    public void recordLootMissOrWrong(LocalDateTime now) {
        this.lootDropPending = false;
        this.lootCombo = 0;
        this.updatedAt = now;
    }

    private static int clampLootThreshold(int seconds) {
        return Math.max(LOOT_MIN_THRESHOLD_SECONDS, Math.min(LOOT_MAX_THRESHOLD_SECONDS, seconds));
    }
}
