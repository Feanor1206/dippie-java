package com.dippie.service.catalog;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

// Giao diện cổng Adapter (vai trò Target) để tầng catalog hỏi file media ngoài đĩa còn tồn tại không mà khỏi phụ thuộc trực tiếp vào gói media.
public interface MediaFileAvailability {

    // Lọc từ danh sách mã yêu cầu ra tập các asset đang có file vật lý sẵn sàng.
    Set<String> availableAssetIds(Collection<String> assetIds);

    // Trả về map đánh dấu trạng thái sẵn sàng của toàn bộ asset video.
    Map<String, Boolean> availableVideoAssets();
}
