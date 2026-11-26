package com.dippie.model.profile;

import com.dippie.exception.InvalidRequestException;
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
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

// bảng child_profiles lưu từng hồ sơ bé dưới một tài khoản cha mẹ, tối đa 6 bé mỗi nhà
@Entity
@Table(
        name = "child_profiles",
        indexes = {
                @Index(name = "idx_child_profiles_user_created", columnList = "user_id, created_at")
        }
)
public class ChildProfile {

    public static final int MAX_PER_ACCOUNT = 6;
    public static final List<String> AVATAR_KEYS = List.of(
            "bear", "bunny", "cat", "dino", "fox", "owl", "panda", "star"
    );

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "avatar_key", nullable = false, length = 32)
    private String avatarKey;

    @Column(name = "daily_minutes")
    @ColumnDefault("60")
    private Integer dailyMinutes = 60;

    @Column(name = "math_level", nullable = false)
    @ColumnDefault("1")
    private int mathLevel = 1;

    @Column(name = "reward_minutes", nullable = false)
    @ColumnDefault("5")
    private int rewardMinutes = 5;

    @Column(name = "max_bonus_minutes", nullable = false)
    @ColumnDefault("30")
    private int maxBonusMinutes = 30;

    @Column(name = "created_at", columnDefinition = "TIMESTAMP", nullable = false, updatable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", columnDefinition = "TIMESTAMP", nullable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "user_id",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_child_profile_user")
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    protected ChildProfile() {
    }

    public ChildProfile(Long userId, String name, String avatarKey, LocalDateTime now) {
        this(userId, name, avatarKey, null, null, now);
    }

    public ChildProfile(
            Long userId,
            String name,
            String avatarKey,
            Integer initialDailyMinutes,
            Boolean unlimited,
            LocalDateTime now
    ) {
        this.userId = userId;
        this.name = validateName(name);
        this.avatarKey = validateAvatarKey(avatarKey);
        if (Boolean.TRUE.equals(unlimited)) {
            this.dailyMinutes = null;
        } else if (initialDailyMinutes != null) {
            this.dailyMinutes = validateDailyMinutes(initialDailyMinutes);
        } else {
            this.dailyMinutes = 60;
        }
        this.createdAt = now;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public String getAvatarKey() {
        return avatarKey;
    }

    public Integer getDailyMinutes() {
        return dailyMinutes;
    }

    public boolean isUnlimited() {
        return dailyMinutes == null;
    }

    public int getMathLevel() {
        return mathLevel;
    }

    public int getRewardMinutes() {
        return rewardMinutes;
    }

    public int getMaxBonusMinutes() {
        return maxBonusMinutes;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    // sửa tên, ảnh đại diện và tùy chọn đổi luôn hạn mức ngày nếu phụ huynh có gửi kèm
    public void updateDetails(
            String newName,
            String newAvatarKey,
            Integer newDailyMinutes,
            Boolean unlimited,
            LocalDateTime updatedAt
    ) {
        this.name = validateName(newName);
        this.avatarKey = validateAvatarKey(newAvatarKey);
        if (Boolean.TRUE.equals(unlimited)) {
            this.dailyMinutes = null;
        } else if (newDailyMinutes != null) {
            this.dailyMinutes = validateDailyMinutes(newDailyMinutes);
        }
        this.updatedAt = updatedAt;
    }

    // giữ bản cũ cho chỗ chỉ đổi tên và ảnh đại diện
    public void updateDetails(String newName, String newAvatarKey, LocalDateTime updatedAt) {
        updateDetails(newName, newAvatarKey, null, null, updatedAt);
    }

    // cập nhật giới hạn phút xem mỗi ngày (hoặc không giới hạn) với cấu hình bài toán thưởng cho bé
    public void updateScreenTimeSettings(
            Integer newDailyMinutes,
            Boolean unlimited,
            Integer newMathLevel,
            Integer newRewardMinutes,
            Integer newMaxBonusMinutes,
            LocalDateTime updatedAt
    ) {
        if (Boolean.TRUE.equals(unlimited)) {
            this.dailyMinutes = null;
        } else {
            this.dailyMinutes = validateDailyMinutes(newDailyMinutes);
        }
        if (newMathLevel != null) {
            this.mathLevel = validateMathLevel(newMathLevel);
        }
        if (newRewardMinutes != null) {
            this.rewardMinutes = validateRewardMinutes(newRewardMinutes);
        }
        if (newMaxBonusMinutes != null) {
            this.maxBonusMinutes = validateMaxBonusMinutes(newMaxBonusMinutes);
        }
        this.updatedAt = updatedAt;
    }

    // giữ bản cũ 5 tham số cho caller cũ không truyền cờ không giới hạn
    public void updateScreenTimeSettings(
            Integer newDailyMinutes,
            Integer newMathLevel,
            Integer newRewardMinutes,
            Integer newMaxBonusMinutes,
            LocalDateTime updatedAt
    ) {
        updateScreenTimeSettings(newDailyMinutes, false, newMathLevel, newRewardMinutes, newMaxBonusMinutes, updatedAt);
    }

    // giới hạn mỗi ngày nằm trong khoảng 0 đến 600 phút
    public static int validateDailyMinutes(Integer value) {
        if (value == null || value < 0 || value > 600) {
            throw new InvalidRequestException("Giới hạn thời gian mỗi ngày cần từ 0 đến 600 phút.");
        }
        return value;
    }

    // cấp độ toán chỉ nhận mức 1 đến 4
    public static int validateMathLevel(Integer value) {
        if (value == null || value < 1 || value > 4) {
            throw new InvalidRequestException("Cấp độ bài toán cần từ 1 đến 4.");
        }
        return value;
    }

    // số phút thưởng mỗi câu đúng từ 1 đến 30 phút
    public static int validateRewardMinutes(Integer value) {
        if (value == null || value < 1 || value > 30) {
            throw new InvalidRequestException("Số phút thưởng mỗi câu đúng cần từ 1 đến 30 phút.");
        }
        return value;
    }

    // trần phút thưởng mỗi ngày từ 0 đến 120 phút
    public static int validateMaxBonusMinutes(Integer value) {
        if (value == null || value < 0 || value > 120) {
            throw new InvalidRequestException("Tổng phút thưởng tối đa mỗi ngày cần từ 0 đến 120 phút.");
        }
        return value;
    }

    // tên bé gọn 1 đến 50 ký tự, cắt khoảng trắng thừa hai đầu
    public static String validateName(String rawName) {
        String trimmed = rawName == null ? "" : rawName.trim();
        if (trimmed.isEmpty() || trimmed.length() > 50) {
            throw new InvalidRequestException("Tên hồ sơ trẻ em cần từ 1 đến 50 ký tự.");
        }
        return trimmed;
    }

    // chỉ nhận mã ảnh đại diện nằm trong bộ cố định của hệ thống
    public static String validateAvatarKey(String rawAvatarKey) {
        String normalized = rawAvatarKey == null ? "" : rawAvatarKey.trim().toLowerCase(Locale.ROOT);
        if (!AVATAR_KEYS.contains(normalized)) {
            throw new InvalidRequestException("Ảnh đại diện hồ sơ trẻ em chưa hợp lệ.");
        }
        return normalized;
    }
}
