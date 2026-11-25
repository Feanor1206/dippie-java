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

    @Column(name = "release_year", nullable = false)
    private int releaseYear;

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

    public Movie(String id, String title, int releaseYear, double rating, String runtime, String genres,
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

    public int getReleaseYear() {
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void updateCatalogSeries(String title, Integer releaseYear, String synopsis) {
        this.title = title;
        if (releaseYear != null && releaseYear != 0) {
            this.releaseYear = releaseYear;
        }
        description = synopsis == null ? "" : synopsis;
    }

    public void updateCatalogEpisode(String title, String runtime, String synopsis) {
        this.title = title;
        this.runtime = runtime;
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
