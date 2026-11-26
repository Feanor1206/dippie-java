package com.dippie.model.vip;

import com.dippie.model.catalog.CatalogSeries;
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

// đánh dấu bộ phim VIP riêng cho từng hồ sơ bé
@Entity
@Table(
        name = "child_vip_series",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_child_vip_series", columnNames = {"profile_id", "series_id"})
        },
        indexes = {
                @Index(name = "idx_child_vip_series_profile", columnList = "profile_id")
        }
)
public class ChildVipSeries {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "profile_id", nullable = false)
    private Long profileId;

    @Column(name = "series_id", nullable = false, length = 100)
    private String seriesId;

    @Column(name = "created_at", columnDefinition = "TIMESTAMP", nullable = false, updatable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "profile_id",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_child_vip_series_profile")
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ChildProfile profile;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "series_id",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_child_vip_series_series")
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
    private CatalogSeries series;

    protected ChildVipSeries() {
    }

    public ChildVipSeries(Long profileId, String seriesId, LocalDateTime createdAt) {
        this.profileId = profileId;
        this.seriesId = seriesId;
        this.createdAt = createdAt;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
