package com.dippie.service.catalog;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

// cổng Adapter (vai Target) để tầng catalog hỏi file media ngoài đĩa còn tồn tại không mà khỏi dính trực tiếp vào gói media
public interface MediaFileAvailability {

    // lọc từ danh sách mã đưa vào ra mấy asset đang có file thật sẵn ngoài đĩa
    Set<String> availableAssetIds(Collection<String> assetIds);

    // trả map đánh dấu xem từng asset video có sẵn file chưa
    Map<String, Boolean> availableVideoAssets();
}
