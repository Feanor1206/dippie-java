package com.dippie.model.report;

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
import org.hibernate.annotations.Check;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

// bảng playback_reports lưu báo lỗi phát video với kết quả xử lý của admin
// hai khóa ngoại sang User đều đặt SET_NULL để xóa tài khoản rồi dữ liệu báo lỗi vẫn còn nguyên
@Entity
@Table(
        name = "playback_reports",
        indexes = {
                @Index(name = "idx_playback_reports_status_created", columnList = "status, created_at"),
                @Index(name = "idx_playback_reports_episode", columnList = "episode_id")
        }
)
@Check(
        name = "ck_playback_report_reason",
        constraints = "reason IN ('khong_phat', 'sai_tap', 'am_thanh', 'noi_dung_khong_phu_hop', 'khac')"
)
@Check(
        name = "ck_playback_report_status",
        constraints = "status IN ('open', 'resolved', 'dismissed')"
)
public class PlaybackReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "episode_id", length = 100, nullable = false)
    private String episodeId;

    @Column(name = "asset_id", length = 100)
    private String assetId;

    @Column(name = "reporter_user_id")
    private Long reporterUserId;

    @Column(name = "reason", length = 40, nullable = false)
    private String reason;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "status", length = 20, nullable = false)
    @ColumnDefault("'open'")
    private String status;

    @Column(name = "resolved_by_user_id")
    private Long resolvedByUserId;

    @Column(name = "resolved_at", columnDefinition = "TIMESTAMP")
    private LocalDateTime resolvedAt;

    @Column(name = "created_at", columnDefinition = "TIMESTAMP", nullable = false, updatable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", columnDefinition = "TIMESTAMP", nullable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "reporter_user_id",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_playback_reports_reporter")
    )
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private User reporter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "resolved_by_user_id",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_playback_reports_resolver")
    )
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private User resolver;

    protected PlaybackReport() {
    }

    public PlaybackReport(
            String episodeId,
            String assetId,
            Long reporterUserId,
            PlaybackReportReason reason,
            String note,
            LocalDateTime createdAt
    ) {
        this.episodeId = episodeId;
        this.assetId = assetId;
        this.reporterUserId = reporterUserId;
        this.reason = reason.code();
        this.note = note;
        this.status = PlaybackReportStatus.OPEN.code();
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getEpisodeId() {
        return episodeId;
    }

    public String getAssetId() {
        return assetId;
    }

    public Long getReporterUserId() {
        return reporterUserId;
    }

    public String getReason() {
        return reason;
    }

    public String getNote() {
        return note;
    }

    public String getStatus() {
        return status;
    }

    public Long getResolvedByUserId() {
        return resolvedByUserId;
    }

    public LocalDateTime getResolvedAt() {
        return resolvedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    // cập nhật trạng thái xử lý, id admin xử lý với giờ cập nhật
    public void updateResolution(PlaybackReportStatus nextStatus, Long resolverUserId, LocalDateTime resolvedAt) {
        this.status = nextStatus.code();
        this.resolvedByUserId = resolverUserId;
        this.resolvedAt = resolvedAt;
        this.updatedAt = resolvedAt;
    }
}
