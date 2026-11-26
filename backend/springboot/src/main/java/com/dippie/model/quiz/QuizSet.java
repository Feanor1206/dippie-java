package com.dippie.model.quiz;

import com.dippie.exception.InvalidRequestException;
import com.dippie.model.user.User;

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

// bảng quiz_sets lưu bộ câu hỏi có sẵn của hệ thống và bộ riêng của từng tài khoản phụ huynh
@Entity
@Table(
        name = "quiz_sets",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_quiz_sets_system_key", columnNames = "system_key")
        },
        indexes = {
                @Index(name = "idx_quiz_sets_owner_active", columnList = "owner_user_id, active"),
                @Index(name = "idx_quiz_sets_built_in", columnList = "built_in, active")
        }
)
public class QuizSet {

    public static final int MAX_PER_ACCOUNT = 20;
    public static final int MAX_QUESTIONS_PER_SET = 500;
    public static final int MAX_CSV_BYTES = 1_048_576;

    public static final String SYSTEM_KEY_MATH = "BUILTIN_MATH";
    public static final String SYSTEM_KEY_ENGLISH = "BUILTIN_ENGLISH";
    public static final String SYSTEM_KEY_SCIENCE_RIDDLES = "BUILTIN_SCIENCE_RIDDLES";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_user_id")
    private Long ownerUserId;

    @Column(name = "system_key", length = 40)
    private String systemKey;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 300)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_subject", nullable = false, length = 20)
    @ColumnDefault("'KHAC'")
    private QuizSubject defaultSubject = QuizSubject.KHAC;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_grade", length = 20)
    private QuizGrade defaultGrade;

    @Column(name = "built_in", nullable = false)
    @ColumnDefault("false")
    private boolean builtIn;

    @Column(name = "dynamic_generator", nullable = false)
    @ColumnDefault("false")
    private boolean dynamicGenerator;

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
            name = "owner_user_id",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_quiz_sets_owner")
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User owner;

    protected QuizSet() {
    }

    public static QuizSet builtIn(
            String systemKey,
            String name,
            String description,
            QuizSubject defaultSubject,
            boolean dynamicGenerator,
            LocalDateTime now
    ) {
        QuizSet set = new QuizSet();
        set.ownerUserId = null;
        set.systemKey = systemKey;
        set.name = validateName(name);
        set.description = validateDescription(description);
        set.defaultSubject = defaultSubject == null ? QuizSubject.KHAC : defaultSubject;
        set.defaultGrade = null;
        set.builtIn = true;
        set.dynamicGenerator = dynamicGenerator;
        set.active = true;
        set.createdAt = now;
        set.updatedAt = now;
        return set;
    }

    public static QuizSet ownedByParent(
            Long ownerUserId,
            String name,
            String description,
            QuizSubject defaultSubject,
            QuizGrade defaultGrade,
            LocalDateTime now
    ) {
        QuizSet set = new QuizSet();
        set.ownerUserId = ownerUserId;
        set.systemKey = null;
        set.name = validateName(name);
        set.description = validateDescription(description);
        set.defaultSubject = defaultSubject == null ? QuizSubject.KHAC : defaultSubject;
        set.defaultGrade = defaultGrade;
        set.builtIn = false;
        set.dynamicGenerator = false;
        set.active = true;
        set.createdAt = now;
        set.updatedAt = now;
        return set;
    }

    public Long getId() {
        return id;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public String getSystemKey() {
        return systemKey;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public QuizSubject getDefaultSubject() {
        return defaultSubject;
    }

    public QuizGrade getDefaultGrade() {
        return defaultGrade;
    }

    public boolean isBuiltIn() {
        return builtIn;
    }

    public boolean isDynamicGenerator() {
        return dynamicGenerator;
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

    // sửa tên, mô tả, môn/lớp mặc định hoặc bật tắt bộ câu hỏi của phụ huynh
    public void update(
            String newName,
            String newDescription,
            QuizSubject newDefaultSubject,
            QuizGrade newDefaultGrade,
            Boolean newActive,
            LocalDateTime updatedAt
    ) {
        if (newName != null) {
            this.name = validateName(newName);
        }
        if (newDescription != null) {
            this.description = validateDescription(newDescription);
        }
        if (newDefaultSubject != null) {
            this.defaultSubject = newDefaultSubject;
        }
        this.defaultGrade = newDefaultGrade;
        if (newActive != null) {
            this.active = newActive;
        }
        this.updatedAt = updatedAt;
    }

    public static String validateName(String raw) {
        String trimmed = raw == null ? "" : raw.trim();
        if (trimmed.isEmpty() || trimmed.length() > 100) {
            throw new InvalidRequestException("Tên bộ câu hỏi cần từ 1 đến 100 ký tự.");
        }
        return trimmed;
    }

    public static String validateDescription(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > 300) {
            throw new InvalidRequestException("Mô tả bộ câu hỏi tối đa 300 ký tự.");
        }
        return trimmed;
    }
}
