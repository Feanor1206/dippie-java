package com.dippie.service.assistant;

import com.dippie.model.assistant.ChatIntent;
import com.dippie.model.assistant.SearchEvidence;
import com.dippie.model.assistant.SearchHit;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

// Xác minh TemplateAnswerGenerator tách token kèm khoảng trắng và chọn đúng mẫu cho câu hỏi bộ phim, tâm trạng.
class TemplateAnswerGeneratorTest {

    private final IntentParser intentParser = new IntentParser();
    private final TemplateAnswerGenerator generator = new TemplateAnswerGenerator();

    @Test
    void streamsTokensMatchingWordAndTrailingWhitespacePattern() {
        ChatIntent intent = intentParser.parse("cảnh Chi làm vỡ bình hoa");
        SearchHit hit = SearchHitFixture.builder()
                .title("Tập 22")
                .startMs(520000L)
                .evidence(List.of(new SearchEvidence("sc-1", "Chi làm vỡ bình hoa.", 520000L, null, "scene")))
                .build();

        AtomicReference<String> stage = new AtomicReference<>();
        List<String> tokens = generator.answer(intent, List.of(hit), stage::set).toList();

        assertThat(stage.get()).isEqualTo("template");
        assertThat(tokens).isNotEmpty();
        assertThat(tokens).allSatisfy(token -> assertThat(token).matches("\\S+\\s*"));
        assertThat(String.join("", tokens))
                .isEqualTo("Có vẻ bạn đang tìm Tập 22, khoảng 08:40. Chi làm vỡ bình hoa.");
    }

    @Test
    void usesSeriesAndMoodRepliesAppropriately() {
        ChatIntent seriesIntent = intentParser.parse("Xin chào Jadoo có bao nhiêu tập?");
        SearchHit seriesHit = SearchHitFixture.builder()
                .title("Xin chào Jadoo")
                .itemType("series")
                .episodeCount(42)
                .build();
        assertThat(String.join("", generator.answer(seriesIntent, List.of(seriesHit)).toList()))
                .isEqualTo("Kho phim hiện có 42 tập Xin chào Jadoo.");

        ChatIntent moodIntent = intentParser.parse("hôm nay tôi stress quá");
        SearchHit one = SearchHitFixture.builder().id("ep-1").title("Cuộc gặp đầu tiên").build();
        SearchHit two = SearchHitFixture.builder().id("ep-2").title("Chuyến đi xa").build();
        assertThat(String.join("", generator.answer(moodIntent, List.of(one)).toList()))
                .isEqualTo("Bạn có thể xem tập \"Cuộc gặp đầu tiên\".");
        assertThat(String.join("", generator.answer(moodIntent, List.of(one, two)).toList()))
                .isEqualTo("Bạn có thể xem tập \"Cuộc gặp đầu tiên\", tập \"Chuyến đi xa\".");
    }
}
