package com.dippie.controller.catalog;

import com.dippie.dto.catalog.CatalogImportResult;
import com.dippie.dto.catalog.CatalogPackageView;
import com.dippie.dto.catalog.CatalogSnapshotView;
import com.dippie.exception.CatalogInputException;
import com.dippie.exception.InvalidRequestException;
import com.dippie.model.catalog.CatalogDocument;
import com.dippie.security.CurrentViewer;
import com.dippie.security.Viewer;
import com.dippie.service.admin.AuditTrailWriter;
import com.dippie.service.catalog.CatalogExportService;
import com.dippie.service.catalog.CatalogImportService;
import com.dippie.service.catalog.CatalogValidation;

import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// controller admin lo hết mấy API kiểm tra, nhập, xem lịch sử, đồng bộ snapshot và xuất danh mục
@RestController
@RequestMapping("/api/admin/catalog")
public class AdminCatalogController {

    private final CurrentViewer currentViewer;
    private final CatalogImportService importService;
    private final CatalogExportService exportService;
    private final AuditTrailWriter auditTrailWriter;
    private final ObjectReader documentReader;

    public AdminCatalogController(CurrentViewer currentViewer, CatalogImportService importService,
            CatalogExportService exportService, AuditTrailWriter auditTrailWriter, ObjectMapper mapper) {
        this.currentViewer = currentViewer;
        this.importService = importService;
        this.exportService = exportService;
        this.auditTrailWriter = auditTrailWriter;
        // copy riêng 1 ObjectMapper rồi khóa độ sâu JSON tối đa 64 tầng và bật FAIL_ON_TRAILING_TOKENS
        // Việt đừng dùng chung mapper mặc định ở đây nha, gặp payload lồng sâu quá là dễ tràn stack hoặc cạn RAM
        ObjectMapper boundedMapper = mapper.copy();
        boundedMapper.getFactory().setStreamReadConstraints(
                StreamReadConstraints.builder().maxNestingDepth(64).build()
        );
        documentReader = boundedMapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    // check gói JSON rồi trả ước lượng thay đổi, chưa ghi gì xuống DB
    @PostMapping("/validate")
    public ResponseEntity<CatalogValidation> validate(HttpServletRequest request) {
        currentViewer.requireAdmin();
        try {
            return ResponseEntity.ok(importService.validate(readDocument(request)));
        } catch (CatalogInputException exception) {
            return ResponseEntity.badRequest().body(CatalogValidation.invalid(exception));
        }
    }

    // nhận gói danh mục JSON từ admin rồi chạy luồng nhập chính thức
    @PostMapping("/import")
    public CatalogImportResult importPackage(HttpServletRequest request) {
        currentViewer.requireAdmin();
        return importService.importPackage(readDocument(request));
    }

    // lấy lịch sử mấy gói danh mục đã nhập, mới nhất lên đầu
    @GetMapping("/history")
    public List<CatalogPackageView> history() {
        currentViewer.requireAdmin();
        return importService.history();
    }

    // xem danh sách các snapshot đồng bộ chỉ mục hiện có
    @GetMapping("/snapshots")
    public List<CatalogSnapshotView> snapshots() {
        currentViewer.requireAdmin();
        return importService.snapshots();
    }

    // đồng bộ lại snapshot mới nhất sang bộ tìm kiếm rồi ghi audit log
    @PostMapping("/snapshots/{id}/retry")
    public CatalogSnapshotView retry(@PathVariable String id) {
        Viewer actor = currentViewer.requireAdmin();
        CatalogSnapshotView retried = importService.retrySnapshot(id);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("snapshotVersion", retried.snapshotVersion());
        details.put("status", retried.status());
        details.put("retryCount", retried.retryCount());
        auditTrailWriter.record(actor, "CATALOG_SNAPSHOT_RETRIED", "CATALOG_SNAPSHOT", retried.id(), details);
        return retried;
    }

    // xuất danh mục hiện tại ra JSON theo cờ lọc bản nháp, file riêng tư rồi ghi audit log
    @GetMapping("/export")
    public JsonNode export(@RequestParam(name = "includeDraft", required = false) String rawIncludeDraft,
            @RequestParam(name = "includePrivate", required = false) String rawIncludePrivate) {
        Viewer actor = currentViewer.requireAdmin();
        boolean includeDraft = parseExportFlag(rawIncludeDraft);
        boolean includePrivate = parseExportFlag(rawIncludePrivate);
        JsonNode exported = exportService.exportCatalog(includeDraft, includePrivate);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("includeDraft", includeDraft);
        details.put("includePrivate", includePrivate);
        auditTrailWriter.record(actor, "CATALOG_EXPORTED", "CATALOG_PACKAGE", "catalog-export", details);
        return exported;
    }

    private static boolean parseExportFlag(String rawValue) {
        if (rawValue == null) {
            return true;
        }
        return switch (rawValue.toLowerCase(Locale.ROOT)) {
            case "true", "1", "t", "yes", "y", "on" -> true;
            case "false", "0", "f", "no", "n", "off" -> false;
            default -> throw new InvalidRequestException("Giá trị tham số không hợp lệ.");
        };
    }

    private CatalogDocument readDocument(HttpServletRequest request) {
        try (InputStream body = request.getInputStream()) {
            JsonNode json = documentReader.readTree(body);
            if (json != null && !json.isMissingNode()) {
                return new CatalogDocument(json);
            }
        } catch (IOException exception) {
            throw invalidJson();
        }
        throw invalidJson();
    }

    private static CatalogInputException invalidJson() {
        return new CatalogInputException("package", "Dữ liệu gửi lên không đúng định dạng.");
    }
}
