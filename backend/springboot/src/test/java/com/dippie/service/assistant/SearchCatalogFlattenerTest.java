package com.dippie.service.assistant;

import com.dippie.model.assistant.SearchDocument;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// test bộ làm phẳng chỉ nhét bản ghi ready hay bản cũ chưa có status vào index, gạt draft, pending với hidden ra
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
