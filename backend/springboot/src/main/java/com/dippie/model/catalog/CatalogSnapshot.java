package com.dippie.model.catalog;

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

// Theo dõi từng phiên bản snapshot catalog và vòng đời đồng bộ chỉ mục (kiểu State qua enum Status và các hàm chuyển trạng thái).
@Entity
@Table(name = "catalog_snapshots",
        indexes = {
                @Index(name = "idx_cat_snap_status", columnList = "status"),
                @Index(name = "idx_cat_snap_hash_version", columnList = "content_hash, snapshot_version"),
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

    // Các trạng thái vòng đời dựng chỉ mục của snapshot. Hằng CACHE_REQUIRED giữ lại cho dữ liệu cũ và giao diện admin còn kiểm tra tiền tố này nên Việt đừng đổi hay xóa nhé.
    public enum Status {
        PENDING, BUILDING, READY, BLOCKED, SYNC_FAILED;

        public static final String LEGACY_CACHE_REQUIRED = "CACHE_REQUIRED";

        public static boolean isAwaitingIndex(String status) {
            return PENDING.name().equals(status) || BUILDING.name().equals(status);
        }
    }

    // Ghi nhận kết quả sau khi chạy đồng bộ chỉ mục; chỉ gán completedAt khi đạt READY, trạng thái khác xóa trắng completedAt.
    public void completeIndexSync(String outcome, LocalDateTime time) {
        status = outcome;
        completedAt = Status.READY.name().equals(outcome) ? time : null;
        updatedAt = time;
    }

    // Tăng số lần thử lại lên 1 rồi cập nhật trạng thái đồng bộ mới nhất.
    public void recordRetry(String outcome, LocalDateTime time) {
        retryCount = retryCount == null ? 1 : retryCount + 1;
        completeIndexSync(outcome, time);
    }

    // Trả true khi snapshot vẫn đang ở PENDING hoặc BUILDING.
    public boolean isAwaitingIndex() {
        return Status.isAwaitingIndex(status);
    }

    // Khi có snapshot mới hơn chèn lên, snapshot cũ đang dở dang bị đánh dấu SYNC_FAILED.
    public void supersede(LocalDateTime time) {
        completeIndexSync(Status.SYNC_FAILED.name(), time);
    }

    // Quy đổi bản ghi cũ còn mang trạng thái CACHE_REQUIRED sang BLOCKED theo quy ước hiện tại.
    public void normalizeLegacyStatus(LocalDateTime time) {
        if (Status.LEGACY_CACHE_REQUIRED.equals(status)) {
            status = Status.BLOCKED.name();
            updatedAt = time;
        }
    }

    // Tự điền createdAt và updatedAt theo giờ UTC (cắt đến giây) nếu bên ngoài chưa gọi recordCreation trước khi lưu.
    @PrePersist
    private void initTimestamps() {
        if (createdAt == null) {
            recordCreation(LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS));
        }
    }
}
