package com.dippie.service.catalog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

// chạy 1 lần lúc app khởi động để rà mấy snapshot danh mục đang treo, thay vì sửa trạng thái trong HTTP GET
@Component
public class CatalogSnapshotStartupReconciler implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(CatalogSnapshotStartupReconciler.class);

    private final CatalogImportService catalogImportService;

    public CatalogSnapshotStartupReconciler(CatalogImportService catalogImportService) {
        this.catalogImportService = catalogImportService;
    }

    // lúc khởi động mà bộ tìm kiếm chưa sẵn sàng thì log cảnh báo rồi bỏ qua, đừng để lỗi này chặn app chạy lên
    @Override
    public void run(ApplicationArguments args) {
        try {
            catalogImportService.reconcilePending();
        } catch (RuntimeException exception) {
            LOG.warn("Không thể rà soát lại các snapshot danh mục lúc khởi động ({})", exception.getClass().getSimpleName());
        }
    }
}
