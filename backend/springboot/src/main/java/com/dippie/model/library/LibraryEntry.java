package com.dippie.model.library;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDateTime;

// Lớp cha @MappedSuperclass gom các cột chung (id, userId, movieId, addedAt) cho cả bảng yêu thích và xem sau.
@MappedSuperclass
public abstract class LibraryEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "movie_id", length = 100, nullable = false)
    private String movieId;

    @Column(name = "added_at", columnDefinition = "TIMESTAMP")
    @ColumnDefault("CURRENT_TIMESTAMP")
    private LocalDateTime addedAt;

    protected LibraryEntry() {
    }

    protected LibraryEntry(Long userId, String movieId, LocalDateTime addedAt) {
        this.userId = userId;
        this.movieId = movieId;
        this.addedAt = addedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getMovieId() {
        return movieId;
    }

    public LocalDateTime getAddedAt() {
        return addedAt;
    }
}
