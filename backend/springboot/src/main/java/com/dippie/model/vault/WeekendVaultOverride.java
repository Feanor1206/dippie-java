package com.dippie.model.vault;

import com.dippie.model.profile.ChildProfile;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDate;
import java.time.LocalDateTime;

// bản ghi phụ huynh tự mở khóa két sắt phim cuối tuần bằng mã PIN cho 1 hồ sơ bé trong tuần đó
@Entity
@Table(
        name = "weekend_vault_overrides",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_weekend_vault_profile_week", columnNames = {"profile_id", "week_start"})
        }
)
public class WeekendVaultOverride {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "profile_id", nullable = false)
    private Long profileId;

    @Column(name = "week_start", nullable = false)
    private LocalDate weekStart;

    @Column(name = "unlocked", nullable = false)
    @ColumnDefault("TRUE")
    private boolean unlocked = true;

    @Column(name = "updated_at", columnDefinition = "TIMESTAMP", nullable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "profile_id",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_weekend_vault_profile")
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ChildProfile profile;

    protected WeekendVaultOverride() {
    }

    public WeekendVaultOverride(Long profileId, LocalDate weekStart, boolean unlocked, LocalDateTime updatedAt) {
        this.profileId = profileId;
        this.weekStart = weekStart;
        this.unlocked = unlocked;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getProfileId() {
        return profileId;
    }

    public LocalDate getWeekStart() {
        return weekStart;
    }

    public boolean isUnlocked() {
        return unlocked;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUnlocked(boolean unlocked, LocalDateTime updatedAt) {
        this.unlocked = unlocked;
        this.updatedAt = updatedAt;
    }
}
