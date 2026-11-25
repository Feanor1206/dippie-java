package com.dippie.assistant;

import com.dippie.assistant.search.CatalogSearch;
import com.dippie.common.web.ApiResponse;
import com.dippie.security.CurrentViewer;
import com.dippie.security.Viewer;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// Endpoint /api/assistant/status cho frontend kiểm tra chỉ mục tìm kiếm đã sẵn sàng chưa.
@RestController
class AssistantStatusController {

    private final AssistantProperties properties;
    private final CatalogSearch search;
    private final CurrentViewer currentViewer;

    AssistantStatusController(AssistantProperties properties, CatalogSearch search, CurrentViewer currentViewer) {
        this.properties = properties;
        this.search = search;
        this.currentViewer = currentViewer;
    }

    // Chỉ admin mới thấy tổng số tài liệu và tên provider LLM, còn user thường chỉ nhận searchReady để tránh lộ cấu hình nội bộ.
    @GetMapping("/api/assistant/status")
    ApiResponse<AssistantStatus> status() {
        boolean ready = search.isReady();
        boolean isAdmin = currentViewer.find().filter(Viewer::isAdmin).isPresent();
        return ApiResponse.ok(new AssistantStatus(
                ready,
                isAdmin && ready ? search.documentCount() : null,
                isAdmin ? properties.llm().provider() : null
        ));
    }

    // Payload trạng thái trả về cho client ở /api/assistant/status.
    record AssistantStatus(boolean searchReady, Integer documents, String llmProvider) {
    }
}
