package com.dippie.movie;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

// Entity ánh xạ bảng movies, lưu cả phim lẻ lẫn bản ghi đại diện của bộ phim và từng tập trong danh mục.
// Dấu thời gian createdAt và updatedAt tự cập nhật theo giờ UTC qua @PrePersist và @PreUpdate.
@Entity
@Table(name = "movies",
        indexes = {
                @Index(name = "idx_movies_title", columnList = "title"),
                @Index(name = "idx_movies_year", columnList = "release_year"),
                @Index(name = "idx_movies_rating", columnList = "rating")
        })
@Check(name = "ck_movie_rating_bounds", constraints = "rating BETWEEN 0 AND 10")
public class Movie {

    @Id
    @Column(name = "id", length = 100, nullable = false)
    private String id;

    @Column(name = "title", length = 255, nullable = false)
    private String title;

    @Column(name = "release_year")
    private Integer releaseYear;

    @Column(name = "rating", nullable = false)
    private double rating;

    @Column(name = "runtime", length = 20)
    private String runtime;

    @Column(name = "genres", columnDefinition = "LONGTEXT")
    private String genres;

    @Column(name = "description", columnDefinition = "LONGTEXT")
    private String description;

    @Column(name = "poster", length = 500)
    private String poster;

    @Column(name = "backdrop", length = 500)
    private String backdrop;

    @Column(name = "video_url", length = 500)
    private String videoUrl;

    @Column(name = "created_at", columnDefinition = "TIMESTAMP", updatable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", columnDefinition = "TIMESTAMP")
    @ColumnDefault("CURRENT_TIMESTAMP")
    private LocalDateTime updatedAt;

    protected Movie() {
    }

    public Movie(String id, String title, Integer releaseYear, double rating, String runtime, String genres,
            String description, String poster, String backdrop, String videoUrl) {
        this.id = id;
        this.title = title;
        this.releaseYear = releaseYear;
        this.rating = rating;
        this.runtime = runtime;
        this.genres = genres;
        this.description = description;
        this.poster = poster;
        this.backdrop = backdrop;
        this.videoUrl = videoUrl;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public Integer getReleaseYear() {
        return releaseYear;
    }

    public double getRating() {
        return rating;
    }

    public String getRuntime() {
        return runtime;
    }

    public String getGenres() {
        return genres;
    }

    public String getDescription() {
        return description;
    }

    public String getPoster() {
        return poster;
    }

    public String getBackdrop() {
        return backdrop;
    }

    public String getVideoUrl() {
        return videoUrl;
    }

    // Đồng bộ lại thông tin phim khi import danh mục mới, ép mô tả null về chuỗi rỗng để frontend không bị lỗi hiển thị.
    public void updateCatalogMetadata(String title, Integer releaseYear, String runtime, String synopsis,
            String genres) {
        this.title = title;
        this.releaseYear = releaseYear;
        this.runtime = runtime;
        this.genres = genres;
        description = synopsis == null ? "" : synopsis;
    }

    @PreUpdate
    private void updateTimestamp() {
        updatedAt = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS);
    }

    @PrePersist
    private void initializeTimestamps() {
        updateTimestamp();
        createdAt = updatedAt;
    }
}
