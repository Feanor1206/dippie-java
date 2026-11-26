package com.dippie.model.admin;

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

// Bảng admin_audit_events lưu dấu vết các thao tác quản trị như đổi quyền, khóa tài khoản hay nhập danh mục.
// Khóa ngoại tới User đặt SET_NULL để khi tài khoản người thực hiện bị xóa thì dòng lịch sử vẫn còn nguyên.
@Entity
@Table(
        name = "admin_audit_events",
        indexes = {
                @Index(name = "idx_admin_audit_created", columnList = "created_at"),
                @Index(name = "idx_admin_audit_target", columnList = "target_type, target_id")
        }
)
public class AdminAuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "actor_username", length = 50, nullable = false)
    private String actorUsername;

    @Column(name = "action", length = 50, nullable = false)
    private String action;

    @Column(name = "target_type", length = 50, nullable = false)
    private String targetType;

    @Column(name = "target_id", length = 100, nullable = false)
    private String targetId;

    @Column(name = "details_json", columnDefinition = "LONGTEXT")
    private String detailsJson;

    @Column(name = "created_at", columnDefinition = "TIMESTAMP", nullable = false, updatable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "actor_user_id",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_admin_audit_actor")
    )
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private User actor;

    protected AdminAuditEvent() {
    }

    public AdminAuditEvent(Long actorUserId, String actorUsername, String action, String targetType, String targetId,
            String detailsJson, LocalDateTime createdAt) {
        this.actorUserId = actorUserId;
        this.actorUsername = actorUsername;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.detailsJson = detailsJson;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getActorUsername() {
        return actorUsername;
    }

    public String getAction() {
        return action;
    }

    public String getTargetType() {
        return targetType;
    }

    public String getTargetId() {
        return targetId;
    }

    public String getDetailsJson() {
        return detailsJson;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
