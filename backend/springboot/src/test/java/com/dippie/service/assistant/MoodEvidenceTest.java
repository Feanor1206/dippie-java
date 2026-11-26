package com.dippie.service.assistant;

import com.dippie.model.assistant.SearchEvidence;
import com.dippie.model.assistant.SearchHit;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// test chấm điểm vui buồn trong MoodEvidence, tính cả bỏ tiêu đề khỏi mô tả với đọc trường tâm trạng phụ
class MoodEvidenceTest {

    @Test
    void scoresCheerfulEvidenceMatchingPythonParity() {
        SearchHit hit = SearchHitFixture.builder()
                .title("Tập vui")
                .genres(List.of("Hài hước", "Gia đình"))
                .keywords(List.of("vui nhộn", "ấm áp"))
                .themes(List.of("tích cực"))
                .plotFacts(List.of("Mọi người cùng cười vui"))
                .description("Tập vui kể về chuyến đi hài hước và funny.")
                .evidence(List.of(
                        new SearchEvidence("sc-1", "Cảnh cười rất vui và cheerful.", 1000L, 5000L, "scene"),
                        new SearchEvidence("ep-1", "vui vui vui", null, null, "episode")
                ))
                .build();

        assertThat(MoodEvidence.score(hit, "cheerful")).isEqualTo(9);
    }

    @Test
    void scoresSadEvidenceMatchingPythonParity() {
        SearchHit hit = SearchHitFixture.builder()
                .title("Ngày buồn")
                .genres(List.of("Chính kịch"))
                .keywords(List.of("bi kịch", "sad"))
                .description("Ngày buồn mang cảm xúc melancholy và tragedy.")
                .evidence(List.of(new SearchEvidence("dlg-1", "Thật quá buồn.", 2000L, 4000L, "dialogue")))
                .build();

        assertThat(MoodEvidence.score(hit, "sad")).isEqualTo(5);
    }

    @Test
    void stripsTitleFromDescriptionAndReadsExtraMoodFields() {
        SearchHit titleOnly = SearchHitFixture.builder()
                .title("Hài hước")
                .description("Hài hước")
                .build();
        assertThat(MoodEvidence.score(titleOnly, "cheerful")).isZero();

        SearchHit withDiagnostics = SearchHitFixture.builder()
                .title("Tập thường")
                .diagnostics(Map.of("moods", List.of("cheerful", "funny"), "emotion", "laugh"))
                .build();
        assertThat(MoodEvidence.score(withDiagnostics, "cheerful")).isEqualTo(3);
    }
}
