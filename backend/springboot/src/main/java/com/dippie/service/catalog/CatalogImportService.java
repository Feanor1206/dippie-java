package com.dippie.service.catalog;

import com.dippie.dto.catalog.CatalogImportResult;
import com.dippie.dto.catalog.CatalogPackageView;
import com.dippie.dto.catalog.CatalogSnapshotView;
import com.dippie.exception.ConflictException;
import com.dippie.exception.DomainException;
import com.dippie.exception.InvalidRequestException;
import com.dippie.model.catalog.CatalogDocument;
import com.dippie.model.catalog.CatalogImportedEvent;
import com.dippie.model.catalog.ContentHash;
import com.dippie.repository.catalog.CatalogPackageRepository;
import com.dippie.security.CurrentViewer;
import com.dippie.security.Viewer;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

// kiểu Facade ôm trọn luồng nhập danh mục từ kiểm tra, ghi DB, đồng bộ chỉ mục tới bắn CatalogImportedEvent cho mấy listener
// để Propagation.NOT_SUPPORTED kèm khóa ReentrantLock cho transaction DB commit xong hẳn rồi mới đồng bộ chỉ mục với bắn event
@Service
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class CatalogImportService {

    private final ReentrantLock catalogLock = new ReentrantLock();
    private final CatalogPackageValidator validator;
    private final CatalogDiff diff;
    private final CatalogImportTransaction transaction;
    private final CatalogPackageRepository packages;
    private final CatalogSnapshotService snapshots;
    private final CurrentViewer currentViewer;
    private final ApplicationEventPublisher events;

    public CatalogImportService(CatalogPackageValidator validator, CatalogDiff diff, CatalogImportTransaction transaction,
            CatalogPackageRepository packages, CatalogSnapshotService snapshots, CurrentViewer currentViewer,
            ApplicationEventPublisher events) {
        this.validator = validator;
        this.diff = diff;
        this.transaction = transaction;
        this.packages = packages;
        this.snapshots = snapshots;
        this.currentViewer = currentViewer;
        this.events = events;
    }

    // kiểm tra gói JSON rồi ước lượng số thay đổi ở chế độ chỉ đọc
    @Transactional(readOnly = true)
    public CatalogValidation validate(CatalogDocument input) {
        JsonNode document = input.readOnlyJson();
        var identity = validator.validate(document);
        ContentHash hash = ContentHash.of(document);
        return CatalogValidation.valid(identity, hash, diff.calculate(document, identity, hash, false).counts());
    }

    // xếp hàng từng đợt nhập qua catalogLock, chỉ đẩy chỉ mục với bắn CatalogImportedEvent sau khi DB commit xong và có thay đổi
    public CatalogImportResult importPackage(CatalogDocument input) {
        String username = currentViewer.find().filter(Viewer::isAdmin).map(Viewer::username).orElse(null);
        return withLock(() -> {
            CatalogImportTransaction.Commit commit = write(input);
            if (!commit.unchanged()) {
                snapshots.synchronize(commit.snapshot());
                events.publishEvent(new CatalogImportedEvent(commit.identity().id(), commit.identity().revision(),
                        commit.snapshot().getSnapshotVersion(), commit.snapshot().getId(), username, commit.importedAt()));
            }
            return commit.response();
        });
    }

    // lấy lịch sử mấy gói đã nhập, mới nhất xếp lên đầu
    @Transactional(readOnly = true)
    public List<CatalogPackageView> history() {
        return packages.findAllByOrderByImportedAtDesc().stream().map(CatalogPackageView::from).toList();
    }

    // đọc danh sách snapshot dưới khóa chung kẻo đọc dính lúc một đợt nhập đang ghi dở
    public List<CatalogSnapshotView> snapshots() {
        return withLock(snapshots::list);
    }

    // rà mấy snapshot còn dang dở dưới khóa chung kẻo chạy đè lên luồng nhập
    public void reconcilePending() {
        withLock(() -> {
            snapshots.reconcilePending();
            return null;
        });
    }

    // đồng bộ lại snapshot mới nhất dưới khóa chung
    public CatalogSnapshotView retrySnapshot(String id) {
        return withLock(() -> snapshots.retry(id));
    }

    private CatalogImportTransaction.Commit write(CatalogDocument input) {
        try {
            JsonNode document = input.readOnlyJson();
            var identity = validator.validate(document);
            return transaction.write(document, identity, ContentHash.of(document));
        } catch (PessimisticLockingFailureException exception) {
            throw concurrentImport();
        } catch (OptimisticLockingFailureException exception) {
            throw changedCatalog();
        } catch (DataIntegrityViolationException exception) {
            throw translateIntegrityFailure(exception);
        } catch (IllegalArgumentException | ArithmeticException exception) {
            throw invalidData();
        }
    }

    private <T> T withLock(Supplier<T> action) {
        catalogLock.lock();
        try {
            return action.get();
        } finally {
            catalogLock.unlock();
        }
    }

    // tách mã lỗi SQL về deadlock, trùng khóa unique hay sai kiểu dữ liệu để trả mã HTTP đúng bệnh
    private static DomainException translateIntegrityFailure(DataIntegrityViolationException exception) {
        if (exception.getMostSpecificCause() instanceof SQLException sql) {
            int code = sql.getErrorCode();
            String state = sql.getSQLState();
            if (code == 1205 || code == 1213 || "40001".equals(state)) {
                return concurrentImport();
            }
            if (code == 1062 || code == 1555 || code == 2067 || "23505".equals(state)) {
                return changedCatalog();
            }
            if (state != null && state.startsWith("22")) {
                return invalidData();
            }
        }
        return new InvalidRequestException("Dữ liệu hoặc liên kết trong gói danh mục không hợp lệ.");
    }

    private static ConflictException concurrentImport() {
        return new ConflictException("Hệ thống đang xử lý một lần nhập khác, bạn hãy thử lại sau.");
    }

    private static ConflictException changedCatalog() {
        return new ConflictException("Danh mục vừa được cập nhật. Bạn hãy kiểm tra lại gói rồi thử lại.");
    }

    private static InvalidRequestException invalidData() {
        return new InvalidRequestException("Dữ liệu gói danh mục không hợp lệ.");
    }
}
