package com.dippie.model.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

// lưu lịch sử với metadata (version, contentHash, trạng thái COMMITTED) của từng gói catalog đã import
@Entity
@Table(name = "catalog_packages", indexes = @Index(name = "idx_cat_pkg_imported", columnList = "imported_at"))
@Check(name = "ck_catalog_package_status", constraints = "status IN ('COMMITTED')")
public class CatalogPackage {

    @Id
    @Column(name = "id", length = 100, nullable = false)
    private String id;

    @Column(name = "version", length = 50, nullable = false)
    private String version;

    @Column(name = "revision", nullable = false)
    private int revision;

    @Column(name = "default_language", length = 20, nullable = false)
    private String defaultLanguage;

    @Column(name = "import_mode", length = 50, nullable = false)
    private String importMode;

    @Column(name = "reference_scope", length = 50, nullable = false)
    private String referenceScope;

    @Column(name = "name", length = 200)
    private String name;

    @Column(name = "description", columnDefinition = "LONGTEXT")
    private String description;

    @Column(name = "content_hash", length = 100, nullable = false)
    private String contentHash;

    @Column(name = "status", length = 50, nullable = false)
    private String status;

    @Column(name = "error_message", columnDefinition = "LONGTEXT")
    private String errorMessage;

    @Version
    @Column(name = "opt_lock_version")
    @ColumnDefault("0")
    private Long optLockVersion;

    @Column(name = "created_at", columnDefinition = "TIMESTAMP", nullable = false, updatable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;

    @Column(name = "imported_at", columnDefinition = "TIMESTAMP")
    @ColumnDefault("CURRENT_TIMESTAMP")
    private LocalDateTime importedAt;

    protected CatalogPackage() {
    }

    public CatalogPackage(String id, String version, int revision, String defaultLanguage, String importMode,
            String referenceScope, String name, String description, String contentHash, String status,
            String errorMessage) {
        this.id = id;
        this.version = version;
        this.revision = revision;
        this.defaultLanguage = defaultLanguage;
        this.importMode = importMode;
        this.referenceScope = referenceScope;
        this.name = name;
        this.description = description;
        this.contentHash = contentHash;
        this.status = status;
        this.errorMessage = errorMessage;
    }

    public String getId() {
        return id;
    }

    public String getVersion() {
        return version;
    }

    public int getRevision() {
        return revision;
    }

    public String getDefaultLanguage() {
        return defaultLanguage;
    }

    public String getImportMode() {
        return importMode;
    }

    public String getReferenceScope() {
        return referenceScope;
    }

    public String getDescription() {
        return description;
    }

    public String getContentHash() {
        return contentHash;
    }

    public String getStatus() {
        return status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Long getOptLockVersion() {
        return optLockVersion;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getImportedAt() {
        return importedAt;
    }

    public void recordImportTime(LocalDateTime time) {
        importedAt = time;
    }

    // cập nhật version, hash với giờ nhập từ lần import mới hơn, giữ nguyên name, description với errorMessage cũ
    public void applyImport(CatalogPackage imported) {
        version = imported.version;
        revision = imported.revision;
        defaultLanguage = imported.defaultLanguage;
        importMode = imported.importMode;
        referenceScope = imported.referenceScope;
        contentHash = imported.contentHash;
        status = imported.status;
        importedAt = imported.importedAt;
    }

    // đã gán sẵn importedAt thì lấy luôn làm createdAt, chưa có mới lấy giờ UTC cắt tròn đến giây
    @PrePersist
    private void initTimestamps() {
        createdAt = importedAt == null ? LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS) : importedAt;
        importedAt = createdAt;
    }
}
