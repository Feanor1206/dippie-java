package com.dippie.model.quiz;

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
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

// bảng nối lưu các bộ câu hỏi đang bật cho từng hồ sơ bé
@Entity
@Table(
        name = "child_quiz_sets",
        indexes = {
                @Index(name = "idx_child_quiz_sets_set", columnList = "set_id")
        }
)
@IdClass(ChildQuizSetId.class)
public class ChildQuizSet {

    @Id
    @Column(name = "profile_id", nullable = false)
    private Long profileId;

    @Id
    @Column(name = "set_id", nullable = false)
    private Long setId;

    @Column(name = "created_at", columnDefinition = "TIMESTAMP", nullable = false, updatable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "profile_id",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_child_quiz_sets_profile")
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ChildProfile profile;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "set_id",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_child_quiz_sets_set")
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
    private QuizSet quizSet;

    protected ChildQuizSet() {
    }

    public ChildQuizSet(Long profileId, Long setId, LocalDateTime createdAt) {
        this.profileId = profileId;
        this.setId = setId;
        this.createdAt = createdAt;
    }

    public Long getProfileId() {
        return profileId;
    }

    public Long getSetId() {
        return setId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
