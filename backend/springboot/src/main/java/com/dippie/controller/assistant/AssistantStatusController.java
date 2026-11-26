package com.dippie.controller.assistant;

import com.dippie.common.web.ApiResponse;
import com.dippie.config.assistant.AssistantProperties;
import com.dippie.dto.assistant.AssistantStatus;
import com.dippie.security.CurrentViewer;
import com.dippie.security.Viewer;
import com.dippie.service.assistant.CatalogSearch;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// /api/assistant/status cho frontend check xem chỉ mục tìm kiếm đã sẵn sàng chưa
@RestController
public class AssistantStatusController {

    private final AssistantProperties properties;
    private final CatalogSearch search;
    private final CurrentViewer currentViewer;

    public AssistantStatusController(AssistantProperties properties, CatalogSearch search, CurrentViewer currentViewer) {
        this.properties = properties;
        this.search = search;
        this.currentViewer = currentViewer;
    }

    // chỉ admin mới thấy tổng số tài liệu với tên provider LLM, user thường chỉ nhận searchReady cho khỏi lộ cấu hình
    @GetMapping("/api/assistant/status")
    public ApiResponse<AssistantStatus> status() {
        boolean ready = search.isReady();
        boolean isAdmin = currentViewer.find().filter(Viewer::isAdmin).isPresent();
        return ApiResponse.ok(new AssistantStatus(
                ready,
                isAdmin && ready ? search.documentCount() : null,
                isAdmin ? properties.llm().provider() : null
        ));
    }

}
