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

// theo dõi từng bản snapshot catalog với vòng đời đồng bộ index, viết kiểu State qua enum Status và mấy hàm đổi trạng thái
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

    // mấy trạng thái lúc dựng index của snapshot
    // CACHE_REQUIRED vẫn còn trong dữ liệu cũ với chỗ check tiền tố bên admin nên Việt đừng đổi hay xóa nha
    public enum Status {
        PENDING, BUILDING, READY, BLOCKED, SYNC_FAILED;

        public static final String LEGACY_CACHE_REQUIRED = "CACHE_REQUIRED";

        public static boolean isAwaitingIndex(String status) {
            return PENDING.name().equals(status) || BUILDING.name().equals(status);
        }
    }

    // lưu kết quả đồng bộ index, chỉ gán completedAt khi lên READY còn trạng thái khác thì xóa trắng
    public void completeIndexSync(String outcome, LocalDateTime time) {
        status = outcome;
        completedAt = Status.READY.name().equals(outcome) ? time : null;
        updatedAt = time;
    }

    // cộng retryCount thêm 1 rồi cập nhật trạng thái đồng bộ mới nhất
    public void recordRetry(String outcome, LocalDateTime time) {
        retryCount = retryCount == null ? 1 : retryCount + 1;
        completeIndexSync(outcome, time);
    }

    // còn ở PENDING hay BUILDING thì trả true
    public boolean isAwaitingIndex() {
        return Status.isAwaitingIndex(status);
    }

    // có snapshot mới chèn lên thì đẩy snapshot cũ đang dở về SYNC_FAILED
    public void supersede(LocalDateTime time) {
        completeIndexSync(Status.SYNC_FAILED.name(), time);
    }

    // đổi mấy bản ghi cũ đang mang CACHE_REQUIRED sang BLOCKED theo quy ước mới
    public void normalizeLegacyStatus(LocalDateTime time) {
        if (Status.LEGACY_CACHE_REQUIRED.equals(status)) {
            status = Status.BLOCKED.name();
            updatedAt = time;
        }
    }

    // chưa gọi recordCreation trước lúc lưu thì tự điền createdAt với updatedAt theo UTC cắt xuống giây
    @PrePersist
    private void initTimestamps() {
        if (createdAt == null) {
            recordCreation(LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS));
        }
    }
}
