package com.dippie.model.library;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDateTime;

// lớp cha @MappedSuperclass gom mấy cột chung (id, userId, movieId, addedAt) cho hai bảng yêu thích và xem sau
@MappedSuperclass
public abstract class LibraryEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "profile_id")
    private Long profileId;

    @Column(name = "profile_scope_id", nullable = false)
    @ColumnDefault("0")
    private long profileScopeId;

    @Column(name = "movie_id", length = 100, nullable = false)
    private String movieId;

    @Column(name = "added_at", columnDefinition = "TIMESTAMP")
    @ColumnDefault("CURRENT_TIMESTAMP")
    private LocalDateTime addedAt;

    protected LibraryEntry() {
    }

    protected LibraryEntry(Long userId, String movieId, LocalDateTime addedAt) {
        this(userId, null, movieId, addedAt);
    }

    protected LibraryEntry(Long userId, Long profileId, String movieId, LocalDateTime addedAt) {
        this.userId = userId;
        this.profileId = profileId;
        this.profileScopeId = profileId == null ? 0L : profileId;
        this.movieId = movieId;
        this.addedAt = addedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getProfileId() {
        return profileId;
    }

    public long getProfileScopeId() {
        return profileScopeId;
    }

    public String getMovieId() {
        return movieId;
    }

    public LocalDateTime getAddedAt() {
        return addedAt;
    }
}
