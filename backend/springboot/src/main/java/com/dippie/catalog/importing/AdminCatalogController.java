package com.dippie.catalog.importing;

import com.dippie.catalog.domain.CatalogPackageView;
import com.dippie.catalog.domain.CatalogSnapshotView;
import com.dippie.common.error.InvalidRequestException;
import com.dippie.security.CurrentViewer;
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
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/admin/catalog")
public class AdminCatalogController {

    private final CurrentViewer currentViewer;
    private final CatalogImportService imports;
    private final CatalogExportService export;
    private final ObjectReader documentReader;

    public AdminCatalogController(CurrentViewer currentViewer, CatalogImportService imports, CatalogExportService export,
            ObjectMapper mapper) {
        this.currentViewer = currentViewer;
        this.imports = imports;
        this.export = export;
        documentReader = mapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    @PostMapping("/validate")
    public ResponseEntity<CatalogValidation> validate(HttpServletRequest request) {
        currentViewer.requireAdmin();
        try {
            return ResponseEntity.ok(imports.validate(readDocument(request)));
        } catch (CatalogInputException exception) {
            return ResponseEntity.badRequest().body(CatalogValidation.invalid(exception));
        }
    }

    @PostMapping("/import")
    public CatalogImportResult importPackage(HttpServletRequest request) {
        currentViewer.requireAdmin();
        return imports.importPackage(readDocument(request));
    }

    @GetMapping("/history")
    public List<CatalogPackageView> history() {
        currentViewer.requireAdmin();
        return imports.history();
    }

    @GetMapping("/snapshots")
    public List<CatalogSnapshotView> snapshots() {
        currentViewer.requireAdmin();
        return imports.snapshots();
    }

    @PostMapping("/snapshots/{id}/retry")
    public CatalogSnapshotView retry(@PathVariable String id) {
        currentViewer.requireAdmin();
        return imports.retrySnapshot(id);
    }

    @GetMapping("/export")
    public JsonNode export(@RequestParam(required = false) String includeDraft,
            @RequestParam(required = false) String includePrivate) {
        currentViewer.requireAdmin();
        return export.exportEligibleCatalog(exportFlag(includeDraft), exportFlag(includePrivate));
    }

    private static boolean exportFlag(String value) {
        if (value == null) {
            return true;
        }
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "true", "1", "t", "yes", "y", "on" -> true;
            case "false", "0", "f", "no", "n", "off" -> false;
            default -> throw new InvalidRequestException("Tham số yêu cầu không hợp lệ.");
        };
    }

    private CatalogDocument readDocument(HttpServletRequest request) {
        try {
            JsonNode json = documentReader.readTree(request.getInputStream());
            if (json != null && !json.isMissingNode()) {
                return new CatalogDocument(json);
            }
        } catch (IOException exception) {
            throw invalidJson();
        }
        throw invalidJson();
    }

    private static CatalogInputException invalidJson() {
        return new CatalogInputException("package", "Dữ liệu JSON không hợp lệ.");
    }
}
