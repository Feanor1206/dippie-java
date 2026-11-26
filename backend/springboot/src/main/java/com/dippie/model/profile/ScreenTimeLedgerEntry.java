package com.dippie.model.profile;

import com.dippie.exception.InvalidRequestException;

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

// sổ cái ghi từng lượt xem, thưởng toán hay ba mẹ cộng thêm phút theo ngày giờ Việt Nam
@Entity
@Table(
        name = "screen_time_ledger",
        indexes = {
                @Index(name = "idx_screen_time_ledger_profile_day", columnList = "profile_id, usage_day, kind"),
                @Index(name = "idx_screen_time_ledger_profile_kind_created", columnList = "profile_id, kind, created_at")
        }
)
public class ScreenTimeLedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "profile_id", nullable = false)
    private Long profileId;

    @Column(name = "usage_day", nullable = false)
    private LocalDate usageDay;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 20)
    private ScreenTimeLedgerKind kind;

    @Column(name = "seconds", nullable = false)
    private int seconds;

    @Column(name = "created_at", columnDefinition = "TIMESTAMP", nullable = false, updatable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "profile_id",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_screen_time_ledger_profile")
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ChildProfile profile;

    protected ScreenTimeLedgerEntry() {
    }

    public ScreenTimeLedgerEntry(
            Long profileId,
            LocalDate usageDay,
            ScreenTimeLedgerKind kind,
            int seconds,
            LocalDateTime createdAt
    ) {
        if (profileId == null || usageDay == null || kind == null || seconds <= 0 || createdAt == null) {
            throw new InvalidRequestException("Dữ liệu ghi nhận thời gian xem chưa hợp lệ.");
        }
        this.profileId = profileId;
        this.usageDay = usageDay;
        this.kind = kind;
        this.seconds = seconds;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getProfileId() {
        return profileId;
    }

    public LocalDate getUsageDay() {
        return usageDay;
    }

    public ScreenTimeLedgerKind getKind() {
        return kind;
    }

    public int getSeconds() {
        return seconds;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
