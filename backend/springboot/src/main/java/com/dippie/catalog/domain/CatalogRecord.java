package com.dippie.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

@MappedSuperclass
public abstract class CatalogRecord {

    @Id
    @Column(name = "id", length = 100, nullable = false)
    private String id;

    @Column(name = "record_json", columnDefinition = "LONGTEXT")
    private String recordJson;

    @Column(name = "raw_json", columnDefinition = "LONGTEXT")
    private String rawJson;

    @Column(name = "revision", nullable = false)
    @ColumnDefault("1")
    private int revision;

    @Version
    @Column(name = "opt_lock_version")
    @ColumnDefault("0")
    private Long optLockVersion;

    @Column(name = "updated_at", columnDefinition = "TIMESTAMP")
    @ColumnDefault("CURRENT_TIMESTAMP")
    private LocalDateTime updatedAt;

    @Transient
    private LocalDateTime importTimestamp;

    protected CatalogRecord() {
    }

    protected CatalogRecord(String id, String recordJson, String rawJson, int revision) {
        this.id = id;
        this.recordJson = recordJson;
        this.rawJson = rawJson;
        this.revision = revision;
    }

    public String getId() {
        return id;
    }

    public String getRecordJson() {
        return recordJson;
    }

    public boolean isPublished() {
        return PublicationStatus.of(recordJson).isPublished();
    }

    protected void replaceRecordJson(String recordJson) {
        this.recordJson = recordJson;
    }

    protected void replaceRecordFrom(CatalogRecord imported) {
        recordJson = imported.recordJson;
        rawJson = imported.rawJson;
        revision = imported.revision;
    }

    public void recordImportTime(LocalDateTime time) {
        updatedAt = time;
        importTimestamp = time;
    }

    public String getRawJson() {
        return rawJson;
    }

    public int getRevision() {
        return revision;
    }

    public Long getOptLockVersion() {
        return optLockVersion;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    @PrePersist
    @PreUpdate
    protected void updateTimestamp() {
        if (importTimestamp != null) {
            updatedAt = importTimestamp;
            importTimestamp = null;
        } else {
            updatedAt = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS);
        }
    }
}
