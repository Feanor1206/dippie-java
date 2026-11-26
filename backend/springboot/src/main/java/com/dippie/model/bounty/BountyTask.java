package com.dippie.model.bounty;

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

// nhiệm vụ đời thực phụ huynh tạo theo tài khoản, làm xong bé được thưởng từ 1 đến 120 phút xem phim
@Entity
@Table(
        name = "bounty_tasks",
        indexes = {
                @Index(name = "idx_bounty_tasks_user_active", columnList = "user_id, active")
        }
)
public class BountyTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "title", nullable = false, length = 120)
    private String title;

    @Column(name = "reward_minutes", nullable = false)
    private int rewardMinutes;

    @Column(name = "icon", length = 32)
    private String icon;

    @Column(name = "active", nullable = false)
    @ColumnDefault("TRUE")
    private boolean active = true;

    @Column(name = "repeatable_daily", nullable = false)
    @ColumnDefault("TRUE")
    private boolean repeatableDaily = true;

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
            foreignKey = @ForeignKey(name = "fk_bounty_tasks_user")
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    protected BountyTask() {
    }

    public BountyTask(
            Long userId,
            String title,
            Integer rewardMinutes,
            String icon,
            Boolean active,
            Boolean repeatableDaily,
            LocalDateTime now
    ) {
        this.userId = userId;
        this.title = validateTitle(title);
        this.rewardMinutes = validateRewardMinutes(rewardMinutes);
        this.icon = normalizeIcon(icon);
        this.active = active == null || active;
        this.repeatableDaily = repeatableDaily == null || repeatableDaily;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getTitle() {
        return title;
    }

    public int getRewardMinutes() {
        return rewardMinutes;
    }

    public String getIcon() {
        return icon;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isRepeatableDaily() {
        return repeatableDaily;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void update(
            String newTitle,
            Integer newRewardMinutes,
            String newIcon,
            Boolean newActive,
            Boolean newRepeatableDaily,
            LocalDateTime now
    ) {
        this.title = validateTitle(newTitle);
        this.rewardMinutes = validateRewardMinutes(newRewardMinutes);
        this.icon = normalizeIcon(newIcon);
        if (newActive != null) {
            this.active = newActive;
        }
        if (newRepeatableDaily != null) {
            this.repeatableDaily = newRepeatableDaily;
        }
        this.updatedAt = now;
    }

    public static String validateTitle(String rawTitle) {
        String trimmed = rawTitle == null ? "" : rawTitle.trim();
        if (trimmed.isEmpty() || trimmed.length() > 120) {
            throw new InvalidRequestException("Tên nhiệm vụ cần dài từ 1 đến 120 ký tự.");
        }
        return trimmed;
    }

    public static int validateRewardMinutes(Integer value) {
        if (value == null || value < 1 || value > 120) {
            throw new InvalidRequestException("Số phút thưởng nhiệm vụ cần từ 1 đến 120 phút.");
        }
        return value;
    }

    public static String normalizeIcon(String rawIcon) {
        if (rawIcon == null) {
            return null;
        }
        String trimmed = rawIcon.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > 32) {
            throw new InvalidRequestException("Biểu tượng nhiệm vụ tối đa 32 ký tự.");
        }
        return trimmed;
    }
}
