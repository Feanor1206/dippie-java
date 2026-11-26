package com.dippie.model.quiz;

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
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

// bảng quiz_questions lưu từng câu trắc nghiệm 4 lựa chọn A-D thuộc một bộ câu hỏi
@Entity
@Table(
        name = "quiz_questions",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_quiz_questions_seed_key", columnNames = "seed_key")
        },
        indexes = {
                @Index(name = "idx_quiz_questions_set_active_grade", columnList = "set_id, active, grade")
        }
)
public class QuizQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "set_id", nullable = false)
    private Long setId;

    @Column(name = "seed_key", length = 64)
    private String seedKey;

    @Column(name = "question_text", nullable = false, length = 400)
    private String questionText;

    @Column(name = "option_a", nullable = false, length = 200)
    private String optionA;

    @Column(name = "option_b", nullable = false, length = 200)
    private String optionB;

    @Column(name = "option_c", nullable = false, length = 200)
    private String optionC;

    @Column(name = "option_d", nullable = false, length = 200)
    private String optionD;

    @Enumerated(EnumType.STRING)
    @Column(name = "correct_option", nullable = false, length = 1)
    private QuizOptionKey correctOption;

    @Column(name = "explanation", length = 400)
    private String explanation;

    @Enumerated(EnumType.STRING)
    @Column(name = "subject", nullable = false, length = 20)
    private QuizSubject subject;

    @Enumerated(EnumType.STRING)
    @Column(name = "grade", nullable = false, length = 20)
    private QuizGrade grade;

    @Column(name = "active", nullable = false)
    @ColumnDefault("true")
    private boolean active = true;

    @Column(name = "created_at", columnDefinition = "TIMESTAMP", nullable = false, updatable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", columnDefinition = "TIMESTAMP", nullable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "set_id",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_quiz_questions_set")
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
    private QuizSet quizSet;

    protected QuizQuestion() {
    }

    public QuizQuestion(
            Long setId,
            String seedKey,
            String questionText,
            String optionA,
            String optionB,
            String optionC,
            String optionD,
            QuizOptionKey correctOption,
            String explanation,
            QuizSubject subject,
            QuizGrade grade,
            boolean active,
            LocalDateTime now
    ) {
        this.setId = setId;
        this.seedKey = seedKey;
        applyContent(questionText, optionA, optionB, optionC, optionD, correctOption, explanation, subject, grade, active);
        this.createdAt = now;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getSetId() {
        return setId;
    }

    public String getSeedKey() {
        return seedKey;
    }

    public String getQuestionText() {
        return questionText;
    }

    public String getOptionA() {
        return optionA;
    }

    public String getOptionB() {
        return optionB;
    }

    public String getOptionC() {
        return optionC;
    }

    public String getOptionD() {
        return optionD;
    }

    public QuizOptionKey getCorrectOption() {
        return correctOption;
    }

    public String getExplanation() {
        return explanation;
    }

    public QuizSubject getSubject() {
        return subject;
    }

    public QuizGrade getGrade() {
        return grade;
    }

    public boolean isActive() {
        return active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public String optionText(QuizOptionKey key) {
        return switch (key) {
            case A -> optionA;
            case B -> optionB;
            case C -> optionC;
            case D -> optionD;
        };
    }

    public void update(
            String questionText,
            String optionA,
            String optionB,
            String optionC,
            String optionD,
            QuizOptionKey correctOption,
            String explanation,
            QuizSubject subject,
            QuizGrade grade,
            Boolean active,
            LocalDateTime updatedAt
    ) {
        applyContent(
                questionText,
                optionA,
                optionB,
                optionC,
                optionD,
                correctOption,
                explanation,
                subject,
                grade,
                active == null ? this.active : active
        );
        this.updatedAt = updatedAt;
    }

    private void applyContent(
            String rawQuestion,
            String rawA,
            String rawB,
            String rawC,
            String rawD,
            QuizOptionKey correct,
            String rawExplanation,
            QuizSubject subject,
            QuizGrade grade,
            boolean active
    ) {
        String cleanQuestion = validateQuestionText(rawQuestion);
        String cleanA = validateOption("A", rawA);
        String cleanB = validateOption("B", rawB);
        String cleanC = validateOption("C", rawC);
        String cleanD = validateOption("D", rawD);
        ensureDistinctOptions(cleanA, cleanB, cleanC, cleanD);
        if (correct == null) {
            throw new InvalidRequestException("Đáp án đúng phải là A, B, C hoặc D.");
        }
        if (subject == null) {
            throw new InvalidRequestException("Vui lòng chọn môn học cho câu hỏi.");
        }
        if (grade == null) {
            throw new InvalidRequestException("Vui lòng chọn khối lớp cho câu hỏi.");
        }
        this.questionText = cleanQuestion;
        this.optionA = cleanA;
        this.optionB = cleanB;
        this.optionC = cleanC;
        this.optionD = cleanD;
        this.correctOption = correct;
        this.explanation = validateExplanation(rawExplanation);
        this.subject = subject;
        this.grade = grade;
        this.active = active;
    }

    public static String validateQuestionText(String raw) {
        String trimmed = raw == null ? "" : raw.trim();
        if (trimmed.isEmpty()) {
            throw new InvalidRequestException("Nội dung câu hỏi không được để trống.");
        }
        if (trimmed.length() > 400) {
            throw new InvalidRequestException("Nội dung câu hỏi tối đa 400 ký tự.");
        }
        return trimmed;
    }

    public static String validateOption(String label, String raw) {
        String trimmed = raw == null ? "" : raw.trim();
        if (trimmed.isEmpty()) {
            throw new InvalidRequestException("Thiếu đáp án " + label + ".");
        }
        if (trimmed.length() > 200) {
            throw new InvalidRequestException("Đáp án " + label + " tối đa 200 ký tự.");
        }
        return trimmed;
    }

    public static void ensureDistinctOptions(String a, String b, String c, String d) {
        Set<String> unique = new HashSet<>();
        for (String item : List.of(a, b, c, d)) {
            if (!unique.add(item.trim().toLowerCase(Locale.ROOT))) {
                throw new InvalidRequestException("4 đáp án A, B, C, D không được trùng nhau.");
            }
        }
    }

    public static String validateExplanation(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > 400) {
            throw new InvalidRequestException("Giải thích tối đa 400 ký tự.");
        }
        return trimmed;
    }
}
