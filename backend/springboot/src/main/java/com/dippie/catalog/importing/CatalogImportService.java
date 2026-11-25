package com.dippie.catalog.importing;

import com.dippie.catalog.domain.CatalogPackageRepository;
import com.dippie.catalog.domain.CatalogPackageView;
import com.dippie.catalog.domain.CatalogSnapshotView;
import com.dippie.common.error.ConflictException;
import com.dippie.common.error.DomainException;
import com.dippie.common.error.InvalidRequestException;
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

    @Transactional(readOnly = true)
    public CatalogValidation validate(CatalogDocument input) {
        JsonNode document = input.json();
        var identity = validator.validate(document);
        ContentHash hash = ContentHash.of(document);
        return CatalogValidation.valid(identity, hash, diff.calculate(document, identity, hash, false).counts());
    }

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

    public List<CatalogPackageView> history() {
        return packages.findAllByOrderByImportedAtDesc().stream().map(CatalogPackageView::from).toList();
    }

    public List<CatalogSnapshotView> snapshots() {
        return withLock(snapshots::list);
    }

    public CatalogSnapshotView retrySnapshot(String id) {
        return withLock(() -> snapshots.retry(id));
    }

    private CatalogImportTransaction.Commit write(CatalogDocument input) {
        try {
            JsonNode document = input.json();
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
        return new InvalidRequestException("Gói danh mục có dữ liệu hoặc liên kết không hợp lệ.");
    }

    private static ConflictException concurrentImport() {
        return new ConflictException("Đang có lần nhập khác. Vui lòng thử lại.");
    }

    private static ConflictException changedCatalog() {
        return new ConflictException("Danh mục vừa thay đổi. Kiểm tra lại gói rồi thử lại.");
    }

    private static InvalidRequestException invalidData() {
        return new InvalidRequestException("Dữ liệu gói danh mục không hợp lệ.");
    }
}
