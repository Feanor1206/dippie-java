package com.dippie.model.quiz;

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

import java.time.LocalDateTime;

// ghi nhận mọi lượt trả lời câu hỏi của từng bé để tránh hỏi lặp 30 câu gần nhất và tổng hợp báo cáo tuần
@Entity
@Table(
        name = "quiz_attempts",
        indexes = {
                @Index(name = "idx_quiz_attempts_profile_answered", columnList = "profile_id, answered_at"),
                @Index(name = "idx_quiz_attempts_profile_set_answered", columnList = "profile_id, set_id, answered_at")
        }
)
public class QuizAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "profile_id", nullable = false)
    private Long profileId;

    @Column(name = "set_id")
    private Long setId;

    @Column(name = "question_id")
    private Long questionId;

    @Column(name = "question_key", nullable = false, length = 120)
    private String questionKey;

    @Column(name = "question_text", nullable = false, length = 400)
    private String questionText;

    @Column(name = "set_name", nullable = false, length = 100)
    private String setName;

    @Enumerated(EnumType.STRING)
    @Column(name = "subject", nullable = false, length = 20)
    private QuizSubject subject;

    @Enumerated(EnumType.STRING)
    @Column(name = "grade", nullable = false, length = 20)
    private QuizGrade grade;

    @Enumerated(EnumType.STRING)
    @Column(name = "context", nullable = false, length = 30)
    private QuizChallengeContext context;

    @Column(name = "correct", nullable = false)
    private boolean correct;

    @Column(name = "response_ms", nullable = false)
    @ColumnDefault("0")
    private int responseMs;

    @Column(name = "answered_at", columnDefinition = "TIMESTAMP", nullable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    private LocalDateTime answeredAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "profile_id",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_quiz_attempts_profile")
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ChildProfile profile;

    protected QuizAttempt() {
    }

    public QuizAttempt(
            Long profileId,
            Long setId,
            Long questionId,
            String questionKey,
            String questionText,
            String setName,
            QuizSubject subject,
            QuizGrade grade,
            QuizChallengeContext context,
            boolean correct,
            int responseMs,
            LocalDateTime answeredAt
    ) {
        this.profileId = profileId;
        this.setId = setId;
        this.questionId = questionId;
        this.questionKey = truncate(questionKey, 120);
        this.questionText = truncate(questionText, 400);
        this.setName = truncate(setName, 100);
        this.subject = subject;
        this.grade = grade;
        this.context = context;
        this.correct = correct;
        this.responseMs = Math.max(0, responseMs);
        this.answeredAt = answeredAt;
    }

    public Long getId() {
        return id;
    }

    public Long getProfileId() {
        return profileId;
    }

    public Long getSetId() {
        return setId;
    }

    public Long getQuestionId() {
        return questionId;
    }

    public String getQuestionKey() {
        return questionKey;
    }

    public String getQuestionText() {
        return questionText;
    }

    public String getSetName() {
        return setName;
    }

    public QuizSubject getSubject() {
        return subject;
    }

    public QuizGrade getGrade() {
        return grade;
    }

    public QuizChallengeContext getContext() {
        return context;
    }

    public boolean isCorrect() {
        return correct;
    }

    public int getResponseMs() {
        return responseMs;
    }

    public LocalDateTime getAnsweredAt() {
        return answeredAt;
    }

    private static String truncate(String value, int maxLen) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLen ? trimmed : trimmed.substring(0, maxLen);
    }
}
