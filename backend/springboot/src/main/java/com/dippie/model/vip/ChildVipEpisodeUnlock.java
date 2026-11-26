package com.dippie.model.vip;

import com.dippie.model.catalog.CatalogEpisode;
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

import java.time.LocalDate;
import java.time.LocalDateTime;

// lưu từng tập phim VIP mà bé đã giải toán mở khóa cho ngày hôm nay theo giờ Việt Nam
@Entity
@Table(
        name = "child_vip_episode_unlocks",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_child_vip_unlock", columnNames = {"profile_id", "episode_id", "unlock_day"})
        },
        indexes = {
                @Index(name = "idx_child_vip_unlock_profile_day", columnList = "profile_id, unlock_day")
        }
)
public class ChildVipEpisodeUnlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "profile_id", nullable = false)
    private Long profileId;

    @Column(name = "episode_id", nullable = false, length = 100)
    private String episodeId;

    @Column(name = "unlock_day", nullable = false)
    private LocalDate unlockDay;

    @Column(name = "unlocked_at", columnDefinition = "TIMESTAMP", nullable = false, updatable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    private LocalDateTime unlockedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "profile_id",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_child_vip_unlock_profile")
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ChildProfile profile;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "episode_id",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_child_vip_unlock_episode")
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
    private CatalogEpisode episode;

    protected ChildVipEpisodeUnlock() {
    }

    public ChildVipEpisodeUnlock(Long profileId, String episodeId, LocalDate unlockDay, LocalDateTime unlockedAt) {
        this.profileId = profileId;
        this.episodeId = episodeId;
        this.unlockDay = unlockDay;
        this.unlockedAt = unlockedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getProfileId() {
        return profileId;
    }

    public String getEpisodeId() {
        return episodeId;
    }

    public LocalDate getUnlockDay() {
        return unlockDay;
    }

    public LocalDateTime getUnlockedAt() {
        return unlockedAt;
    }
}
