package com.dippie.model.play;

import com.dippie.model.profile.ChildProfile;

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
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

// cổng khóa/mở khóa tập kế tiếp của từng hồ sơ bé sau khi xem hết một tập phim
@Entity
@Table(
        name = "child_episode_recap_gates",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_recap_gate_profile_target", columnNames = {"profile_id", "target_episode_id"})
        },
        indexes = {
                @Index(name = "idx_recap_gate_profile_series", columnList = "profile_id, series_id, unlocked")
        }
)
public class ChildEpisodeRecapGate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "profile_id", nullable = false)
    private Long profileId;

    @Column(name = "series_id", nullable = false, length = 100)
    private String seriesId;

    @Column(name = "source_episode_id", nullable = false, length = 100)
    private String sourceEpisodeId;

    @Column(name = "target_episode_id", nullable = false, length = 100)
    private String targetEpisodeId;

    @Column(name = "unlocked", nullable = false)
    @ColumnDefault("false")
    private boolean unlocked;

    @Column(name = "created_at", columnDefinition = "TIMESTAMP", nullable = false, updatable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;

    @Column(name = "unlocked_at", columnDefinition = "TIMESTAMP")
    private LocalDateTime unlockedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "profile_id",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_recap_gate_profile")
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ChildProfile profile;

    protected ChildEpisodeRecapGate() {
    }

    public ChildEpisodeRecapGate(
            Long profileId,
            String seriesId,
            String sourceEpisodeId,
            String targetEpisodeId,
            boolean unlocked,
            LocalDateTime now
    ) {
        this.profileId = profileId;
        this.seriesId = seriesId;
        this.sourceEpisodeId = sourceEpisodeId;
        this.targetEpisodeId = targetEpisodeId;
        this.unlocked = unlocked;
        this.createdAt = now;
        this.unlockedAt = unlocked ? now : null;
    }

    public Long getId() {
        return id;
    }

    public Long getProfileId() {
        return profileId;
    }

    public String getSeriesId() {
        return seriesId;
    }

    public String getSourceEpisodeId() {
        return sourceEpisodeId;
    }

    public String getTargetEpisodeId() {
        return targetEpisodeId;
    }

    public boolean isUnlocked() {
        return unlocked;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUnlockedAt() {
        return unlockedAt;
    }

    public void markUnlocked(LocalDateTime now) {
        if (!this.unlocked) {
            this.unlocked = true;
            this.unlockedAt = now;
        }
    }
}
