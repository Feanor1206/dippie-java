package com.dippie.assistant;

import com.dippie.assistant.search.CatalogSearch;
import com.dippie.common.web.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class AssistantStatusController {

    private final AssistantProperties properties;
    private final CatalogSearch search;

    AssistantStatusController(AssistantProperties properties, CatalogSearch search) {
        this.properties = properties;
        this.search = search;
    }

    @GetMapping("/api/assistant/status")
    ApiResponse<AssistantStatus> status() {
        boolean ready = search.isReady();
        return ApiResponse.ok(new AssistantStatus(ready,
                ready ? search.documentCount() : 0, properties.llm().provider()));
    }

    record AssistantStatus(boolean searchReady, int documents, String llmProvider) {
    }
}
