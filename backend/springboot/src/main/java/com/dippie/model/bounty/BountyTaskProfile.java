package com.dippie.model.bounty;

import com.dippie.model.profile.ChildProfile;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

// bảng nối xác định những hồ sơ trẻ em nào được thấy từng nhiệm vụ đời thực
@Entity
@Table(
        name = "bounty_task_profiles",
        indexes = {
                @Index(name = "idx_bounty_task_profiles_profile", columnList = "profile_id")
        }
)
@IdClass(BountyTaskProfileId.class)
public class BountyTaskProfile {

    @Id
    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Id
    @Column(name = "profile_id", nullable = false)
    private Long profileId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "task_id",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_bounty_task_profiles_task")
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
    private BountyTask task;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "profile_id",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_bounty_task_profiles_profile")
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ChildProfile profile;

    protected BountyTaskProfile() {
    }

    public BountyTaskProfile(Long taskId, Long profileId) {
        this.taskId = taskId;
        this.profileId = profileId;
    }

    public Long getTaskId() {
        return taskId;
    }

    public Long getProfileId() {
        return profileId;
    }
}
