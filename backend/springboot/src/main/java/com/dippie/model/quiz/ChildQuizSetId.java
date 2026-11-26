package com.dippie.model.quiz;

import java.io.Serializable;
import java.util.Objects;

// khóa chính ghép (profile_id, set_id) cho bảng child_quiz_sets
public class ChildQuizSetId implements Serializable {

    private Long profileId;
    private Long setId;

    public ChildQuizSetId() {
    }

    public ChildQuizSetId(Long profileId, Long setId) {
        this.profileId = profileId;
        this.setId = setId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ChildQuizSetId that)) {
            return false;
        }
        return Objects.equals(profileId, that.profileId) && Objects.equals(setId, that.setId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(profileId, setId);
    }
}
