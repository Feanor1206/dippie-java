package com.dippie.model.bounty;

import java.io.Serializable;
import java.util.Objects;

// khóa chính ghép cho bảng bounty_task_profiles
public class BountyTaskProfileId implements Serializable {

    private Long taskId;
    private Long profileId;

    protected BountyTaskProfileId() {
    }

    public BountyTaskProfileId(Long taskId, Long profileId) {
        this.taskId = taskId;
        this.profileId = profileId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof BountyTaskProfileId that)) {
            return false;
        }
        return Objects.equals(taskId, that.taskId) && Objects.equals(profileId, that.profileId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(taskId, profileId);
    }
}
