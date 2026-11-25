package com.dippie.assistant.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.dippie.assistant.search.SearchEvidence;
import com.dippie.assistant.search.SearchHit;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

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
                .isEqualTo("Danh mục hiện có 42 tập Xin chào Jadoo.");

        ChatIntent moodIntent = intentParser.parse("hôm nay tôi stress quá");
        SearchHit one = SearchHitFixture.builder().id("ep-1").build();
        SearchHit two = SearchHitFixture.builder().id("ep-2").build();
        assertThat(String.join("", generator.answer(moodIntent, List.of(one)).toList()))
                .isEqualTo("Bạn thử tập này nhé.");
        assertThat(String.join("", generator.answer(moodIntent, List.of(one, two)).toList()))
                .isEqualTo("Bạn thử mấy tập này nhé.");
    }
}
