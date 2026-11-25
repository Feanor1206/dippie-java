package com.dippie.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "catalog_snapshots",
        indexes = {
                @Index(name = "idx_cat_snap_status", columnList = "status"),
                @Index(name = "uq_cat_snap_version", columnList = "snapshot_version", unique = true)
        })
@Check(name = "ck_catalog_snapshot_status",
        constraints = "status IN ('PENDING', 'BUILDING', 'READY', 'BLOCKED', 'SYNC_FAILED')")
public class CatalogSnapshot {

    @Id
    @Column(name = "id", length = 100, nullable = false)
    private String id;

    @Column(name = "snapshot_version", nullable = false)
    private long snapshotVersion;

    @Column(name = "content_hash", length = 100, nullable = false)
    private String contentHash;

    @Column(name = "total_items", nullable = false)
    private int totalItems;

    @Column(name = "status", length = 50, nullable = false)
    private String status;

    @Column(name = "retry_count")
    @ColumnDefault("0")
    private Integer retryCount;

    @Column(name = "error_message", columnDefinition = "LONGTEXT")
    private String errorMessage;

    @Column(name = "created_at", columnDefinition = "TIMESTAMP", updatable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;

    @Column(name = "completed_at", columnDefinition = "TIMESTAMP")
    private LocalDateTime completedAt;

    @Column(name = "updated_at", columnDefinition = "TIMESTAMP")
    @ColumnDefault("CURRENT_TIMESTAMP")
    private LocalDateTime updatedAt;

    protected CatalogSnapshot() {
    }

    public CatalogSnapshot(String id, long snapshotVersion, String contentHash, int totalItems, String status,
            Integer retryCount, String errorMessage, LocalDateTime completedAt) {
        this.id = id;
        this.snapshotVersion = snapshotVersion;
        this.contentHash = contentHash;
        this.totalItems = totalItems;
        this.status = status;
        this.retryCount = retryCount;
        this.errorMessage = errorMessage;
        this.completedAt = completedAt;
    }

    public String getId() {
        return id;
    }

    public long getSnapshotVersion() {
        return snapshotVersion;
    }

    public String getContentHash() {
        return contentHash;
    }

    public int getTotalItems() {
        return totalItems;
    }

    public String getStatus() {
        return status;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void recordCreation(LocalDateTime time) {
        createdAt = time;
        updatedAt = time;
    }

    public void completeIndexSync(String outcome, LocalDateTime time) {
        status = outcome;
        completedAt = "READY".equals(outcome) ? time : null;
        updatedAt = time;
    }

    public void recordRetry(String outcome, LocalDateTime time) {
        retryCount = retryCount == null ? 1 : retryCount + 1;
        completeIndexSync(outcome, time);
    }

    public boolean isAwaitingIndex() {
        return "PENDING".equals(status) || "BUILDING".equals(status);
    }

    public void supersede(LocalDateTime time) {
        completeIndexSync("SYNC_FAILED", time);
    }

    public void normalizeLegacyStatus(LocalDateTime time) {
        if ("CACHE_REQUIRED".equals(status)) {
            status = "BLOCKED";
            updatedAt = time;
        }
    }

    @PrePersist
    private void initializeTimestamps() {
        if (createdAt == null) {
            recordCreation(LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS));
        }
    }
}
