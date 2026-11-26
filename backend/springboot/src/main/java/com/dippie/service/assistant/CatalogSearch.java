package com.dippie.service.assistant;

import com.dippie.model.assistant.SearchConstraints;
import com.dippie.model.assistant.SearchHit;

import java.util.List;
import java.util.Optional;

// Giao diện chung cho tầng tìm kiếm danh mục, dùng lại ở cả CineBot lẫn luồng gợi ý khám phá.
// Các hàm tra cứu mở rộng trả về rỗng sẵn để bản mock trong test chỉ cần cài isReady và search.
public interface CatalogSearch {

    boolean isReady();

    default int documentCount() {
        return 0;
    }

    // Tìm danh sách kết quả khớp theo từ khóa, giới hạn số lượng và bộ lọc ràng buộc.
    List<SearchHit> search(String query, int limit, SearchConstraints constraints);

    default Optional<SearchHit> item(String id) {
        return Optional.empty();
    }

    // Lấy phân đoạn đứng ngay sau tài liệu hiện tại trong cùng tập phim (dùng cho câu hỏi kiểu "cảnh sau đó thì sao").
    default Optional<SearchHit> nextSegment(String documentId) {
        return Optional.empty();
    }

    // Gợi ý các bộ phim khác gần thể loại và chủ đề với bộ phim người dùng nhắc tới.
    default List<SearchHit> similarSeries(String reference, int limit) {
        return List.of();
    }

    default List<String> availableSeriesTitles() {
        return List.of();
    }
}
