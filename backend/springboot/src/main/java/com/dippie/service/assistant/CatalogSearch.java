package com.dippie.service.assistant;

import com.dippie.model.assistant.SearchConstraints;
import com.dippie.model.assistant.SearchHit;

import java.util.List;
import java.util.Optional;

// interface chung cho tầng tìm kiếm danh mục, dùng lại cho cả CineBot lẫn luồng khám phá
// mấy hàm phụ có default trả rỗng sẵn để mock trong test chỉ cần cài isReady với search cho gọn
public interface CatalogSearch {

    boolean isReady();

    default int documentCount() {
        return 0;
    }

    // tìm kết quả khớp từ khóa theo giới hạn số lượng và bộ lọc
    List<SearchHit> search(String query, int limit, SearchConstraints constraints);

    default Optional<SearchHit> item(String id) {
        return Optional.empty();
    }

    // lấy phân đoạn đứng sau ngay trong cùng tập, dùng lúc hỏi kiểu "cảnh sau đó thì sao"
    default Optional<SearchHit> nextSegment(String documentId) {
        return Optional.empty();
    }

    // gợi ý mấy bộ khác gần thể loại với chủ đề của bộ vừa nhắc
    default List<SearchHit> similarSeries(String reference, int limit) {
        return List.of();
    }

    default List<String> availableSeriesTitles() {
        return List.of();
    }
}
