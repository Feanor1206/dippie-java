package com.dippie.service.catalog;

import com.dippie.model.catalog.CatalogImportedEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

// chạy kiểu Caching Proxy giữ CatalogReadModel trong AtomicReference, kiêm luôn Observer nghe CatalogImportedEvent để tự nạp bản mới
// vì tham chiếu bất biến nên lúc luồng import đang dựng bản mới thì user vẫn đọc bản cũ ngon lành, khỏi chờ khóa
@Component
public class CatalogReadModelCache {

    private static final Logger log = LoggerFactory.getLogger(CatalogReadModelCache.class);

    private final AtomicReference<CatalogReadModel> model = new AtomicReference<>();
    private final CatalogReadModelLoader loader;

    public CatalogReadModelCache(CatalogReadModelLoader loader) {
        this.loader = loader;
    }

    // lấy bản chụp đang có trong RAM, cache trống mới nạp lần đầu
    public CatalogReadModel current() {
        CatalogReadModel current = model.get();
        return current == null ? initialize() : current;
    }

    // xóa bản chụp hiện tại để lần đọc sau tự kéo mới từ DB
    public synchronized void invalidate() {
        model.set(null);
    }

    // có gói danh mục mới thì dựng xong bản mới hẳn rồi mới thay thế
    // bước nạp mà lỗi thì gọi invalidate luôn cho lần sau thử tải lại, khỏi kẹt mãi ở data cũ
    @EventListener
    public synchronized void refresh(CatalogImportedEvent event) {
        try {
            model.set(loader.load());
        } catch (RuntimeException exception) {
            invalidate();
            log.error("Không thể làm mới bộ nhớ đệm danh mục cho bản chụp {}", event.snapshotId(), exception);
        }
    }

    private synchronized CatalogReadModel initialize() {
        CatalogReadModel current = model.get();
        if (current == null) {
            current = loader.load();
            model.set(current);
        }
        return current;
    }
}
