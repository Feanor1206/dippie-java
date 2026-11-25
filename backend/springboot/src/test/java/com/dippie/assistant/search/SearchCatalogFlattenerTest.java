package com.dippie.assistant.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

// Kiểm tra bộ làm phẳng chỉ đưa bản ghi ready hoặc bản ghi cũ chưa có status vào chỉ mục, bỏ qua draft, pending và hidden.
class SearchCatalogFlattenerTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void indexesOnlyReadyOrUnspecifiedStatusRecordsAndExcludesPendingOrHidden() throws Exception {
        String catalogJson = """
                {
                  "series": [
                    {"id": "ser_ready", "title": "Bộ Ready", "record": {"status": "ready"}},
                    {"id": "ser_legacy", "title": "Bộ Di Sản"},
                    {"id": "ser_draft", "title": "Bộ Nháp", "record": {"status": "draft"}},
                    {"id": "ser_pending", "title": "Bộ Chờ", "record": {"status": "pending"}},
                    {"id": "ser_hidden", "title": "Bộ Ẩn", "record": {"status": "hidden"}}
                  ]
                }
                """;
        List<SearchDocument> documents = SearchCatalogFlattener.flatten(mapper.readTree(catalogJson));
        assertThat(documents).extracting(SearchDocument::id)
                .containsExactlyInAnyOrder("ser_ready", "ser_legacy");
    }
}
