package com.dippie.catalog.query;

import com.dippie.catalog.domain.CatalogImportedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

// Hoạt động kiểu Caching Proxy giữ bản chụp CatalogReadModel trong AtomicReference và làm Observer nghe CatalogImportedEvent để tự nạp bản mới.
// Nhờ giữ tham chiếu bất biến, trong lúc luồng import đang dựng bản mới thì request của người dùng vẫn đọc bản cũ bình thường không bị chờ khóa.
@Component
public class CatalogReadModelCache {

    private static final Logger log = LoggerFactory.getLogger(CatalogReadModelCache.class);

    private final AtomicReference<CatalogReadModel> model = new AtomicReference<>();
    private final CatalogReadModelLoader loader;

    CatalogReadModelCache(CatalogReadModelLoader loader) {
        this.loader = loader;
    }

    // Lấy bản chụp hiện có trong bộ nhớ, chỉ nạp lần đầu khi bộ đệm đang rỗng.
    public CatalogReadModel current() {
        CatalogReadModel current = model.get();
        return current == null ? initialize() : current;
    }

    // Xóa bản chụp hiện tại để lần đọc kế tiếp tự nạp mới từ CSDL.
    public synchronized void invalidate() {
        model.set(null);
    }

    // Khi có gói danh mục mới được nạp, dựng sẵn bản mới rồi mới thay thế.
    // Nếu bước nạp lỗi, mình chủ động gọi invalidate để lần gọi sau thử tải lại thay vì kẹt mãi ở dữ liệu cũ.
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
