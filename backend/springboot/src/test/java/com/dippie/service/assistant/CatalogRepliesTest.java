package com.dippie.service.assistant;

import com.dippie.model.assistant.SearchEvidence;
import com.dippie.model.assistant.SearchHit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

// Kiểm tra các mẫu câu trả lời của CatalogReplies: câu dẫn mốc giờ, lọc đoạn trích lặp tên tập và trả lời thông tin bộ phim.
class CatalogRepliesTest {

    static Stream<Arguments> sourceIntroParityCases() {
        return Stream.of(
                Arguments.of("Tập 22 [abcDEF12345].mp4", null, "find_moment", "Có vẻ bạn đang tìm Tập 22."),
                Arguments.of("Bình Minh Trên Sao Hỏa...", 70000L, "find_moment",
                        "Có vẻ bạn đang tìm Bình Minh Trên Sao Hỏa, khoảng 01:10."),
                Arguments.of("Học tập trải nghiệm thú vị.MKV", 0L, "recommend",
                        "Bạn có thể xem Học tập trải nghiệm thú vị, khoảng 00:00."),
                Arguments.of("Lò xo", -5000L, "recommend", "Bạn có thể xem Lò xo, khoảng 00:00."),
                Arguments.of("Tập 1.webm", 3665999L, "find_moment", "Có vẻ bạn đang tìm Tập 1, khoảng 61:05.")
        );
    }

    @ParameterizedTest
    @MethodSource("sourceIntroParityCases")
    void sourceIntroMatchesPython(String title, Long startMs, String mode, String expected) {
        assertThat(CatalogReplies.sourceIntro(title, startMs, mode)).isEqualTo(expected);
    }

    @Test
    void catalogReplyMatchesPythonParityCases() {
        assertThat(CatalogReplies.catalogReply(List.of(), "chitchat"))
                .isEqualTo("Mình là CineBot. Bạn cứ kể lại cảnh phim, nhân vật hoặc tập muốn tìm nhé.");
        assertThat(CatalogReplies.catalogReply(List.of(), "find_moment"))
                .isEqualTo("Mình chưa tìm thấy phim phù hợp trong kho phim.");

        SearchHit normal = SearchHitFixture.builder()
                .title("Tập 22")
                .startMs(520000L)
                .evidence(List.of(new SearchEvidence("sc-1", "Chi làm vỡ bình hoa.", 520000L, null, "scene")))
                .build();
        assertThat(CatalogReplies.catalogReply(List.of(normal), "find_moment"))
                .isEqualTo("Có vẻ bạn đang tìm Tập 22, khoảng 08:40. Chi làm vỡ bình hoa.");

        SearchHit fallbackEpisodeTitle = SearchHitFixture.builder()
                .title("")
                .episodeTitle("Ngôi Sao Biến Mất")
                .startMsSnake(12000L)
                .evidence(List.of(new SearchEvidence("ep-1", "Những hình giấy bay lên.", 12000L, null, "episode")))
                .build();
        assertThat(CatalogReplies.catalogReply(List.of(fallbackEpisodeTitle), "recommend"))
                .isEqualTo("Bạn có thể xem Ngôi Sao Biến Mất, khoảng 00:12. Những hình giấy bay lên.");

        SearchHit defaultTitle = SearchHitFixture.builder().title(null).build();
        assertThat(CatalogReplies.catalogReply(List.of(defaultTitle), "find_moment"))
                .isEqualTo("Có vẻ bạn đang tìm Tập phim.");

        SearchHit boilerplateVi = SearchHitFixture.builder()
                .title("Lò xo")
                .evidence(List.of(new SearchEvidence("ep-1", "Tập phim Lò xo của bộ hoạt hình Larva.", null, null, "episode")))
                .build();
        assertThat(CatalogReplies.catalogReply(List.of(boilerplateVi), "find_moment"))
                .isEqualTo("Có vẻ bạn đang tìm Lò xo.");

        SearchHit boilerplateEn = SearchHitFixture.builder()
                .title("Lò xo")
                .episodeNumber(46)
                .evidence(List.of(new SearchEvidence("ep-1", "Tập 46: Lò xo", null, null, "episode")))
                .build();
        assertThat(CatalogReplies.catalogReply(List.of(boilerplateEn), "find_moment"))
                .isEqualTo("Có vẻ bạn đang tìm Lò xo.");
    }

    @Test
    void seriesReplyMatchesPythonParityCases() {
        assertThat(CatalogReplies.seriesReply(List.of(), "episodes"))
                .isEqualTo("Mình chưa tìm thấy bộ phim này trong kho phim.");

        SearchHit episodes = SearchHitFixture.builder()
                .title("Xin chào Jadoo")
                .episodeCount(120)
                .build();
        assertThat(CatalogReplies.seriesReply(List.of(episodes), "episodes"))
                .isEqualTo("Kho phim hiện có 120 tập Xin chào Jadoo.");

        SearchHit episodesDefault = SearchHitFixture.builder().title("Xin chào Jadoo").build();
        assertThat(CatalogReplies.seriesReply(List.of(episodesDefault), "episodes"))
                .isEqualTo("Kho phim hiện có 0 tập Xin chào Jadoo.");

        SearchHit seasons = SearchHitFixture.builder()
                .title("Doraemon")
                .seasonCount(5)
                .build();
        assertThat(CatalogReplies.seriesReply(List.of(seasons), "seasons"))
                .isEqualTo("Kho phim hiện có 5 mùa Doraemon.");

        SearchHit characters = SearchHitFixture.builder()
                .title("Jadoo")
                .characters(List.of("Jadoo", "Mimi"))
                .build();
        assertThat(CatalogReplies.seriesReply(List.of(characters), "characters"))
                .isEqualTo("Nhân vật trong Jadoo gồm Jadoo, Mimi.");

        SearchHit charactersMissing = SearchHitFixture.builder()
                .title("Jadoo")
                .characters(List.of())
                .build();
        assertThat(CatalogReplies.seriesReply(List.of(charactersMissing), "characters"))
                .isEqualTo("Kho phim chưa có thông tin này về Jadoo.");

        SearchHit summaryFromDiagnostics = SearchHitFixture.builder()
                .title("Larva")
                .diagnostics(Map.of("synopsis", "Hai chú sâu tinh nghịch."))
                .build();
        assertThat(CatalogReplies.seriesReply(List.of(summaryFromDiagnostics), "summary"))
                .isEqualTo("Hai chú sâu tinh nghịch.");

        SearchHit summaryFromDescription = SearchHitFixture.builder()
                .title("Larva")
                .description("Hai chú sâu tinh nghịch.")
                .build();
        assertThat(CatalogReplies.seriesReply(List.of(summaryFromDescription), "summary"))
                .isEqualTo("Hai chú sâu tinh nghịch.");

        SearchHit summaryMissing = SearchHitFixture.builder()
                .title("Larva")
                .description("Phim bộ: Larva")
                .build();
        assertThat(CatalogReplies.seriesReply(List.of(summaryMissing), "summary"))
                .isEqualTo("Kho phim chưa có thông tin này về Larva.");
        assertThat(CatalogReplies.seriesReply(List.of(summaryMissing), "other"))
                .isEqualTo("Kho phim chưa có thông tin này về Larva.");
    }

    @Test
    void evidenceTextsLimitsToThreeAndTruncatesAt1200Chars() {
        String oversized = "a".repeat(1500);
        SearchHit hit = SearchHitFixture.builder()
                .evidence(List.of(
                        new SearchEvidence("d1", oversized, null, null, "episode"),
                        new SearchEvidence("d2", "", null, null, "episode"),
                        new SearchEvidence("d3", "quote 3", null, null, "scene"),
                        new SearchEvidence("d4", "quote 4", null, null, "scene")
                ))
                .build();

        List<String> quotes = CatalogReplies.evidenceTexts(hit);
        assertThat(quotes).hasSize(2);
        assertThat(quotes.get(0)).hasSize(1200);
        assertThat(quotes.get(1)).isEqualTo("quote 3");
    }

    @Test
    void followUpHelperMatchesPythonBuildFollowUps() {
        assertThat(FollowUpSuggestions.build(List.of()))
                .containsExactly("Gợi ý tập phim nổi bật", "Tìm phim hài gia đình");

        SearchHit singleWithCharAndStart = SearchHitFixture.builder()
                .characters(List.of("Jadoo"))
                .startMs(1000L)
                .build();
        assertThat(FollowUpSuggestions.build(List.of(singleWithCharAndStart)))
                .containsExactly("Tìm thêm cảnh có Jadoo", "Cảnh sau đó thì sao");

        SearchHit first = SearchHitFixture.builder()
                .characters(List.of("Jadoo"))
                .startMsSnake(1000L)
                .build();
        SearchHit second = SearchHitFixture.builder().id("hit-2").build();
        assertThat(FollowUpSuggestions.build(List.of(first, second)))
                .containsExactly("Tìm thêm cảnh có Jadoo", "Không, tập khác", "Cảnh sau đó thì sao");

        SearchHit plain = SearchHitFixture.builder().build();
        assertThat(FollowUpSuggestions.build(List.of(plain))).isEmpty();
    }
}
