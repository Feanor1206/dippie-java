package com.dippie.media;

import com.dippie.movie.Movie;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

@Entity
@Table(name = "media_assets",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_movie_kind_version", columnNames = {"movie_id", "kind", "version"})
        })
@Check(name = "ck_media_kind",
        constraints = "kind IN ('feature', 'trailer', 'extra', 'opening', 'ending', 'dub', 'audio_description')")
public class MediaAsset {

    @Id
    @Column(name = "id", length = 64, nullable = false)
    private String id;

    @Column(name = "movie_id", length = 100, nullable = false)
    private String movieId;

    @Column(name = "kind", length = 20, nullable = false)
    private String kind;

    @Column(name = "storage_provider", length = 30, nullable = false)
    @ColumnDefault("'local'")
    private String storageProvider;

    @Column(name = "storage_key", length = 100)
    private String storageKey;

    @Column(name = "relative_path", length = 500, nullable = false)
    private String relativePath;

    @Column(name = "version", length = 50, nullable = false)
    @ColumnDefault("'v1'")
    private String version;

    @Column(name = "mime_type", length = 100, nullable = false)
    @ColumnDefault("'video/mp4'")
    private String mimeType;

    @Column(name = "byte_length", nullable = false)
    private long byteLength;

    @Column(name = "duration_seconds")
    private Double durationSeconds;

    @Column(name = "is_public", nullable = false)
    @ColumnDefault("TRUE")
    private boolean publicAsset;

    @Column(name = "is_available", nullable = false)
    @ColumnDefault("TRUE")
    private boolean available;

    @Column(name = "created_at", columnDefinition = "TIMESTAMP", updatable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", columnDefinition = "TIMESTAMP")
    @ColumnDefault("CURRENT_TIMESTAMP")
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "movie_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_media_movie"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Movie movie;

    protected MediaAsset() {
    }

    public MediaAsset(String id, String movieId, String kind, String storageProvider, String storageKey,
            String relativePath, String version, String mimeType, long byteLength, Double durationSeconds,
            boolean publicAsset, boolean available) {
        this.id = id;
        this.movieId = movieId;
        this.kind = kind;
        this.storageProvider = Objects.requireNonNullElse(storageProvider, "local");
        this.storageKey = storageKey;
        this.relativePath = relativePath;
        this.version = Objects.requireNonNullElse(version, "v1");
        this.mimeType = Objects.requireNonNullElse(mimeType, "video/mp4");
        this.byteLength = byteLength;
        this.durationSeconds = durationSeconds;
        this.publicAsset = publicAsset;
        this.available = available;
    }

    public String getId() {
        return id;
    }

    public String getMovieId() {
        return movieId;
    }

    public String getKind() {
        return kind;
    }

    public String getStorageProvider() {
        return storageProvider;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getRelativePath() {
        return relativePath;
    }

    public String getVersion() {
        return version;
    }

    public String getMimeType() {
        return mimeType;
    }

    public long getByteLength() {
        return byteLength;
    }

    public Double getDurationSeconds() {
        return durationSeconds;
    }

    public boolean isPublic() {
        return publicAsset;
    }

    public boolean isAvailable() {
        return available;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void applyCatalogImport(MediaAsset imported) {
        movieId = imported.movieId;
        kind = imported.kind;
        storageProvider = imported.storageProvider;
        storageKey = imported.storageKey;
        relativePath = imported.relativePath;
        version = imported.version;
        mimeType = imported.mimeType;
        byteLength = imported.byteLength;
        durationSeconds = imported.durationSeconds;
        publicAsset = imported.publicAsset;
        available = imported.available;
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
