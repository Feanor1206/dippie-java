package com.dippie.model.bounty;

import com.dippie.exception.InvalidRequestException;
import com.dippie.model.profile.ChildProfile;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

import java.time.LocalDate;
import java.time.LocalDateTime;

// lượt bé bấm nộp nhiệm vụ chờ ba mẹ nhập PIN duyệt hoặc từ chối
@Entity
@Table(
        name = "bounty_submissions",
        indexes = {
                @Index(name = "idx_bounty_submissions_profile_day", columnList = "profile_id, usage_day, status"),
                @Index(name = "idx_bounty_submissions_task_profile_status", columnList = "task_id, profile_id, status")
        }
)
public class BountySubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "profile_id", nullable = false)
    private Long profileId;

    @Column(name = "usage_day", nullable = false)
    private LocalDate usageDay;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BountySubmissionStatus status;

    @Column(name = "reward_minutes", nullable = false)
    private int rewardMinutes;

    @Column(name = "submitted_at", columnDefinition = "TIMESTAMP", nullable = false, updatable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    private LocalDateTime submittedAt;

    @Column(name = "decided_at", columnDefinition = "TIMESTAMP")
    private LocalDateTime decidedAt;

    @Column(name = "reject_note", length = 255)
    private String rejectNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "task_id",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_bounty_submissions_task")
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
    private BountyTask task;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "profile_id",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_bounty_submissions_profile")
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ChildProfile profile;

    protected BountySubmission() {
    }

    public BountySubmission(Long taskId, Long profileId, LocalDate usageDay, int rewardMinutes, LocalDateTime submittedAt) {
        this.taskId = taskId;
        this.profileId = profileId;
        this.usageDay = usageDay;
        this.status = BountySubmissionStatus.PENDING;
        this.rewardMinutes = BountyTask.validateRewardMinutes(rewardMinutes);
        this.submittedAt = submittedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getTaskId() {
        return taskId;
    }

    public Long getProfileId() {
        return profileId;
    }

    public LocalDate getUsageDay() {
        return usageDay;
    }

    public BountySubmissionStatus getStatus() {
        return status;
    }

    public int getRewardMinutes() {
        return rewardMinutes;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public LocalDateTime getDecidedAt() {
        return decidedAt;
    }

    public String getRejectNote() {
        return rejectNote;
    }

    public void approve(LocalDateTime now) {
        this.status = BountySubmissionStatus.APPROVED;
        this.decidedAt = now;
        this.rejectNote = null;
    }

    public void reject(String note, LocalDateTime now) {
        String cleanNote = note == null ? null : note.trim();
        if (cleanNote != null && cleanNote.isEmpty()) {
            cleanNote = null;
        }
        if (cleanNote != null && cleanNote.length() > 255) {
            throw new InvalidRequestException("Ghi chú từ chối tối đa 255 ký tự.");
        }
        this.status = BountySubmissionStatus.REJECTED;
        this.decidedAt = now;
        this.rejectNote = cleanNote;
    }
}
