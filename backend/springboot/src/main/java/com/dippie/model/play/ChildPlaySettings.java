package com.dippie.model.play;

import com.dippie.exception.InvalidRequestException;
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

import java.time.LocalDateTime;

// cài đặt phụ huynh cho 3 tính năng xem phim của từng hồ sơ bé: Dieppie, rương báu và ôn tập qua tập
@Entity
@Table(name = "child_play_settings")
public class ChildPlaySettings {

    public static final int DEFAULT_FAINT_MINUTES = 20;
    public static final int DEFAULT_REST_MINUTES = 5;

    @Id
    @Column(name = "profile_id")
    private Long profileId;

    @Column(name = "watch_pet_enabled", nullable = false)
    @ColumnDefault("true")
    private boolean watchPetEnabled = true;

    @Column(name = "watch_pet_faint_minutes", nullable = false)
    @ColumnDefault("20")
    private int watchPetFaintMinutes = DEFAULT_FAINT_MINUTES;

    @Column(name = "watch_pet_rest_minutes", nullable = false)
    @ColumnDefault("5")
    private int watchPetRestMinutes = DEFAULT_REST_MINUTES;

    @Column(name = "loot_drop_enabled", nullable = false)
    @ColumnDefault("true")
    private boolean lootDropEnabled = true;

    @Column(name = "recap_to_next_enabled", nullable = false)
    @ColumnDefault("true")
    private boolean recapToNextEnabled = true;

    @Column(name = "updated_at", columnDefinition = "TIMESTAMP", nullable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    private LocalDateTime updatedAt;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "profile_id",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_child_play_settings_profile")
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ChildProfile profile;

    protected ChildPlaySettings() {
    }

    public ChildPlaySettings(Long profileId, LocalDateTime now) {
        this.profileId = profileId;
        this.updatedAt = now;
    }

    public static ChildPlaySettings defaults(Long profileId, LocalDateTime now) {
        return new ChildPlaySettings(profileId, now);
    }

    public Long getProfileId() {
        return profileId;
    }

    public boolean isWatchPetEnabled() {
        return watchPetEnabled;
    }

    public int getWatchPetFaintMinutes() {
        return watchPetFaintMinutes;
    }

    public int getWatchPetRestMinutes() {
        return watchPetRestMinutes;
    }

    public boolean isLootDropEnabled() {
        return lootDropEnabled;
    }

    public boolean isRecapToNextEnabled() {
        return recapToNextEnabled;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    // lưu cấu hình mới từ phụ huynh sau khi kiểm tra đủ giới hạn phút cho Dieppie
    public void update(
            Boolean newWatchPetEnabled,
            Integer newWatchPetFaintMinutes,
            Integer newWatchPetRestMinutes,
            Boolean newLootDropEnabled,
            Boolean newRecapToNextEnabled,
            LocalDateTime now
    ) {
        if (newWatchPetEnabled == null || newLootDropEnabled == null || newRecapToNextEnabled == null) {
            throw new InvalidRequestException("Cần chọn bật hoặc tắt đầy đủ cho từng tính năng.");
        }
        this.watchPetEnabled = newWatchPetEnabled;
        this.watchPetFaintMinutes = validateFaintMinutes(newWatchPetFaintMinutes);
        this.watchPetRestMinutes = validateRestMinutes(newWatchPetRestMinutes);
        this.lootDropEnabled = newLootDropEnabled;
        this.recapToNextEnabled = newRecapToNextEnabled;
        this.updatedAt = now;
    }

    public static int validateFaintMinutes(Integer value) {
        if (value == null || value < 10 || value > 60) {
            throw new InvalidRequestException("Ngưỡng Dieppie mệt xỉu cần từ 10 đến 60 phút.");
        }
        return value;
    }

    public static int validateRestMinutes(Integer value) {
        if (value == null || value < 3 || value > 15) {
            throw new InvalidRequestException("Thời gian cho Dieppie nghỉ mắt cần từ 3 đến 15 phút.");
        }
        return value;
    }
}
