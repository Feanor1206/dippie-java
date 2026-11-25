package com.dippie.catalog.importing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

// Chạy một lần lúc ứng dụng khởi động để rà soát các snapshot danh mục còn treo, thay cho việc sửa trạng thái trong HTTP GET.
@Component
class CatalogSnapshotStartupReconciler implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(CatalogSnapshotStartupReconciler.class);

    private final CatalogImportService catalogImportService;

    CatalogSnapshotStartupReconciler(CatalogImportService catalogImportService) {
        this.catalogImportService = catalogImportService;
    }

    // Nếu bộ tìm kiếm lúc khởi động chưa sẵn sàng thì ghi log cảnh báo rồi bỏ qua, không để lỗi này chặn app khởi chạy.
    @Override
    public void run(ApplicationArguments args) {
        try {
            catalogImportService.reconcilePending();
        } catch (RuntimeException exception) {
            LOG.warn("Không thể rà soát lại các snapshot danh mục lúc khởi động ({})", exception.getClass().getSimpleName());
        }
    }
}
