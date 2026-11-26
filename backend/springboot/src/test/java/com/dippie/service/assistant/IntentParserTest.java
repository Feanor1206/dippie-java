package com.dippie.service.assistant;

import com.dippie.model.assistant.ChatHistoryMessage;
import com.dippie.model.assistant.ChatIntent;
import com.dippie.model.assistant.SearchConstraints;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

// test IntentParser tách thể loại cần hay tránh, mốc năm, số lượng, hỏi thông tin phim, chỉnh filter với JSON snake_case
class IntentParserTest {

    private final IntentParser parser = new IntentParser();
    private final ObjectMapper objectMapper = new ObjectMapper();

    static Stream<Arguments> pythonParityCases() {
        return Stream.of(
                Arguments.of("chitchat_hello", "Xin chào!", List.of(),
                        intent("NO_OP", "chitchat", "episode", "Xin chào!", Map.of(), 1, null, List.of(), List.of(), false, "cheerful")),
                Arguments.of("chitchat_who", "Bạn là ai?", List.of(),
                        intent("NO_OP", "chitchat", "episode", "Bạn là ai?", Map.of(), 1, null, List.of(), List.of(), false, "cheerful")),
                Arguments.of("chitchat_thanks", "Cảm ơn!!!", List.of(),
                        intent("NO_OP", "chitchat", "episode", "Cảm ơn!!!", Map.of(), 1, null, List.of(), List.of(), false, "cheerful")),
                Arguments.of("chitchat_hi", "  hi   ", List.of(),
                        intent("NO_OP", "chitchat", "episode", "  hi   ", Map.of(), 1, null, List.of(), List.of(), false, "cheerful")),
                Arguments.of("simple_moment", "cảnh Chi làm vỡ bình hoa", List.of(),
                        intent("DISCOVERY", "find_moment", "scene", "cảnh Chi làm vỡ bình hoa", Map.of(), 1, null, List.of(), List.of(), false, "cheerful")),
                Arguments.of("simple_episode", "Doraemon tập 209 trò chơi đấu súng không khí", List.of(),
                        intent("DISCOVERY", "find_moment", "episode", "Doraemon tập 209 trò chơi đấu súng không khí", Map.of(), 1, null, List.of(), List.of(), false, "cheerful")),
                Arguments.of("exact_s01e05", "xem s01e05 Jadoo", List.of(),
                        intent("DISCOVERY", "find_moment", "episode", "xem s01e05 Jadoo", Map.of(), 1, null, List.of(), List.of(), false, "cheerful")),
                Arguments.of("exact_season_hash", "Season #2 tập 3", List.of(),
                        intent("DISCOVERY", "find_moment", "episode", "Season #2 tập 3", Map.of(), 2, null, List.of(), List.of(), false, "cheerful")),
                Arguments.of("recommend_basic", "gợi ý phim hoạt hình gia đình", List.of(),
                        intent("DISCOVERY", "recommend", "episode", "gợi ý phim hoạt hình gia đình",
                                mapOf("include_genres", List.of("animation", "family")),
                                6, null, List.of("animation", "family"), List.of(), false, "cheerful")),
                Arguments.of("recommend_en", "recommend comedy animation", List.of(),
                        intent("DISCOVERY", "recommend", "episode", "recommend comedy animation",
                                mapOf("include_genres", List.of("animation", "comedy")),
                                6, null, List.of("animation", "comedy"), List.of(), false, "cheerful")),
                Arguments.of("genre_include_exclude", "tìm phim hoạt hình hài hước không kinh dị và tránh lãng mạn", List.of(),
                        intent("DISCOVERY", "recommend", "episode", "tìm phim hoạt hình hài hước không kinh dị và tránh lãng mạn",
                                mapOf("include_genres", List.of("animation", "comedy"), "exclude_genres", List.of("horror", "romance")),
                                1, null, List.of("animation", "comedy"), List.of("horror", "romance"), true, "cheerful")),
                Arguments.of("genre_exclude_en", "adventure cartoon without horror, exclude drama", List.of(),
                        intent("DISCOVERY", "find_moment", "episode", "adventure cartoon without horror, exclude drama",
                                mapOf("include_genres", List.of("animation", "adventure"), "exclude_genres", List.of("horror", "drama")),
                                1, null, List.of("animation", "adventure"), List.of("horror", "drama"), false, "cheerful")),
                Arguments.of("genre_sci_fi_action", "phim viễn tưởng hành động", List.of(),
                        intent("DISCOVERY", "find_moment", "episode", "phim viễn tưởng hành động",
                                mapOf("include_genres", List.of("sci-fi", "action")),
                                1, null, List.of("sci-fi", "action"), List.of(), false, "cheerful")),
                Arguments.of("year_before", "phim trước năm 2015", List.of(),
                        intent("DISCOVERY", "find_moment", "episode", "phim trước năm 2015",
                                mapOf("year_max", 2014),
                                1, null, List.of(), List.of(), false, "cheerful")),
                Arguments.of("year_after", "phim sau 2020", List.of(),
                        intent("DISCOVERY", "find_moment", "episode", "phim sau 2020",
                                mapOf("year_min", 2021),
                                1, null, List.of(), List.of(), false, "cheerful")),
                Arguments.of("year_range", "phim từ 2010 đến 2018", List.of(),
                        intent("DISCOVERY", "find_moment", "episode", "phim từ 2010 đến 2018",
                                mapOf("year_min", 2010, "year_max", 2018),
                                1, null, List.of(), List.of(), false, "cheerful")),
                Arguments.of("year_exact", "phim năm 2022", List.of(),
                        intent("DISCOVERY", "find_moment", "episode", "phim năm 2022",
                                mapOf("year_min", 2022, "year_max", 2022),
                                1, null, List.of(), List.of(), false, "cheerful")),
                Arguments.of("year_en_before_after", "animation after 2012 before 2021", List.of(),
                        intent("DISCOVERY", "find_moment", "episode", "animation after 2012 before 2021",
                                mapOf("include_genres", List.of("animation"), "year_min", 2013, "year_max", 2020),
                                1, null, List.of("animation"), List.of(), false, "cheerful")),
                Arguments.of("count_top", "top 5 phim hài", List.of(),
                        intent("DISCOVERY", "find_moment", "episode", "top 5 phim hài",
                                mapOf("include_genres", List.of("comedy")),
                                5, null, List.of("comedy"), List.of(), false, "cheerful")),
                Arguments.of("count_chi_mot", "chỉ một tập hoạt hình", List.of(),
                        intent("DISCOVERY", "find_moment", "episode", "chỉ một tập hoạt hình",
                                mapOf("include_genres", List.of("animation")),
                                1, null, List.of("animation"), List.of(), false, "cheerful")),
                Arguments.of("count_num_tap", "3 tập vui nhộn", List.of(),
                        intent("DISCOVERY", "recommend", "episode", "3 tập vui nhộn",
                                Map.of(), 3, null, List.of(), List.of(), true, "cheerful")),
                Arguments.of("count_clamp_high", "top 250 phim hoạt hình", List.of(),
                        intent("DISCOVERY", "find_moment", "episode", "top 250 phim hoạt hình",
                                mapOf("include_genres", List.of("animation")),
                                100, null, List.of("animation"), List.of(), false, "cheerful")),
                Arguments.of("count_clamp_zero", "top 0 phim", List.of(),
                        intent("DISCOVERY", "find_moment", "episode", "top 0 phim",
                                Map.of(), 1, null, List.of(), List.of(), false, "cheerful")),
                Arguments.of("count_not_episode_num", "xem tập 5 phim Jadoo", List.of(),
                        intent("DISCOVERY", "find_moment", "episode", "xem tập 5 phim Jadoo",
                                Map.of(), 1, null, List.of(), List.of(), false, "cheerful")),
                Arguments.of("count_not_season_num", "phần 2 phim Jadoo", List.of(),
                        intent("DISCOVERY", "find_moment", "episode", "phần 2 phim Jadoo",
                                Map.of(), 1, null, List.of(), List.of(), false, "cheerful")),
                Arguments.of("list_hint_tim_cac", "tìm các tập có Jadoo", List.of(),
                        intent("DISCOVERY", "find_moment", "episode", "tìm các tập có Jadoo",
                                Map.of(), 8, null, List.of(), List.of(), false, "cheerful")),
                Arguments.of("list_hint_liet_ke", "liệt kê những cảnh vui", List.of(),
                        intent("DISCOVERY", "find_moment", "scene", "liệt kê những cảnh vui",
                                Map.of(), 8, null, List.of(), List.of(), false, "cheerful")),
                Arguments.of("series_episodes", "Xin chào Jadoo có bao nhiêu tập?", List.of(),
                        intent("DISCOVERY", "find_moment", "series", "Xin chào Jadoo có bao nhiêu tập?",
                                mapOf("series_only", true), 1, "episodes", List.of(), List.of(), false, "cheerful")),
                Arguments.of("series_seasons", "Doraemon có mấy mùa?", List.of(),
                        intent("DISCOVERY", "find_moment", "series", "Doraemon có mấy mùa?",
                                mapOf("series_only", true), 1, "seasons", List.of(), List.of(), false, "cheerful")),
                Arguments.of("series_phan", "Larva có số lượng phần là gì?", List.of(),
                        intent("DISCOVERY", "find_moment", "series", "Larva có số lượng phần là gì?",
                                mapOf("series_only", true), 1, "seasons", List.of(), List.of(), false, "cheerful")),
                Arguments.of("series_characters", "Nhân vật chính của Xin chào Jadoo", List.of(),
                        intent("DISCOVERY", "find_moment", "series", "Nhân vật chính của Xin chào Jadoo",
                                mapOf("series_only", true), 1, "characters", List.of(), List.of(), false, "cheerful")),
                Arguments.of("series_characters_ai", "Nhân vật trong Doraemon gồm ai?", List.of(),
                        intent("DISCOVERY", "find_moment", "series", "Nhân vật trong Doraemon gồm ai?",
                                mapOf("series_only", true), 1, "characters", List.of(), List.of(), false, "cheerful")),
                Arguments.of("series_overview", "Xin chào Jadoo là phim gì?", List.of(),
                        intent("DISCOVERY", "find_moment", "series", "Xin chào Jadoo là phim gì?",
                                mapOf("series_only", true), 1, "summary", List.of(), List.of(), false, "cheerful")),
                Arguments.of("series_overview_intro", "giới thiệu về Larva", List.of(),
                        intent("DISCOVERY", "find_moment", "series", "giới thiệu về Larva",
                                mapOf("series_only", true), 1, "summary", List.of(), List.of(), false, "cheerful")),
                Arguments.of("series_blocked_by_moment", "cảnh có bao nhiêu tập", List.of(),
                        intent("DISCOVERY", "find_moment", "scene", "cảnh có bao nhiêu tập",
                                Map.of(), 1, null, List.of(), List.of(), false, "cheerful")),
                Arguments.of("series_blocked_by_recommend", "gợi ý phim có bao nhiêu tập", List.of(),
                        intent("DISCOVERY", "recommend", "episode", "gợi ý phim có bao nhiêu tập",
                                Map.of(), 6, null, List.of(), List.of(), false, "cheerful")),
                Arguments.of("mood_cheerful", "hôm nay tôi stress căng thẳng quá", List.of(),
                        intent("DISCOVERY", "recommend", "episode", "hôm nay tôi stress căng thẳng quá",
                                Map.of(), 1, null, List.of(), List.of(), true, "cheerful")),
                Arguments.of("mood_sad_target", "tìm phim buồn để xem", List.of(),
                        intent("DISCOVERY", "recommend", "episode", "tìm phim buồn để xem",
                                Map.of(), 1, null, List.of(), List.of(), true, "sad")),
                Arguments.of("mood_sad_tap", "tập buồn của Jadoo", List.of(),
                        intent("DISCOVERY", "recommend", "episode", "tập buồn của Jadoo",
                                Map.of(), 1, null, List.of(), List.of(), true, "sad")),
                Arguments.of("mood_blocked_by_title", "tên tập phim vui đó là gì", List.of(),
                        intent("DISCOVERY", "find_moment", "series", "tên tập phim vui đó là gì",
                                mapOf("series_only", true), 1, "summary", List.of(), List.of(), false, "cheerful")),
                Arguments.of("mood_blocked_by_moment", "cảnh hài hước trong lớp học", List.of(),
                        intent("DISCOVERY", "find_moment", "scene", "cảnh hài hước trong lớp học",
                                mapOf("include_genres", List.of("comedy")),
                                1, null, List.of("comedy"), List.of(), false, "cheerful")),
                Arguments.of("patch_single", "không kinh dị",
                        List.of(
                                new ChatHistoryMessage("user", "gợi ý phim hoạt hình"),
                                new ChatHistoryMessage("assistant", "Đây là gợi ý.")
                        ),
                        intent("CONVERSATIONAL_PATCH", "recommend", "episode", "gợi ý phim hoạt hình không kinh dị",
                                mapOf("include_genres", List.of("animation"), "exclude_genres", List.of("horror")),
                                6, null, List.of("animation"), List.of("horror"), false, "cheerful")),
                Arguments.of("patch_non_matching_prefix", "trước 2018",
                        List.of(
                                new ChatHistoryMessage("user", "tìm phim hoạt hình"),
                                new ChatHistoryMessage("assistant", "Ok"),
                                new ChatHistoryMessage("user", "thêm hài hước")
                        ),
                        intent("DISCOVERY", "find_moment", "episode", "trước 2018",
                                mapOf("year_max", 2017),
                                1, null, List.of(), List.of(), false, "cheerful")),
                Arguments.of("patch_chained_en_before", "before 2018",
                        List.of(
                                new ChatHistoryMessage("user", "tìm phim hoạt hình"),
                                new ChatHistoryMessage("assistant", "Ok"),
                                new ChatHistoryMessage("user", "thêm hài hước")
                        ),
                        intent("CONVERSATIONAL_PATCH", "recommend", "episode", "tìm phim hoạt hình thêm hài hước before 2018",
                                mapOf("include_genres", List.of("animation", "comedy"), "year_max", 2017),
                                1, null, List.of("animation", "comedy"), List.of(), true, "cheerful")),
                Arguments.of("patch_reset_by_non_patch", "bỏ kinh dị",
                        List.of(
                                new ChatHistoryMessage("user", "phim cũ"),
                                new ChatHistoryMessage("user", "gợi ý phim gia đình")
                        ),
                        intent("CONVERSATIONAL_PATCH", "recommend", "episode", "gợi ý phim gia đình bỏ kinh dị",
                                mapOf("include_genres", List.of("family"), "exclude_genres", List.of("horror")),
                                6, null, List.of("family"), List.of("horror"), false, "cheerful")),
                Arguments.of("patch_duplicate_query_ignored", "không kinh dị",
                        List.of(new ChatHistoryMessage("user", "không kinh dị")),
                        intent("DISCOVERY", "find_moment", "episode", "không kinh dị",
                                mapOf("exclude_genres", List.of("horror")),
                                1, null, List.of(), List.of("horror"), false, "cheerful")),
                Arguments.of("patch_canh_sau_do", "cảnh sau đó thì sao",
                        List.of(new ChatHistoryMessage("user", "cảnh Chi làm vỡ bình hoa")),
                        intent("CONVERSATIONAL_PATCH", "find_moment", "scene", "cảnh Chi làm vỡ bình hoa cảnh sau đó thì sao",
                                Map.of(), 1, null, List.of(), List.of(), false, "cheerful"))
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("pythonParityCases")
    void matchesPythonDeterministicIntent(
            String name,
            String query,
            List<ChatHistoryMessage> history,
            ChatIntent expected
    ) {
        ChatIntent actual = parser.parse(query, history);
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void serializesToExactPythonSnakeCaseKeysAndConvertsConstraints() throws Exception {
        ChatIntent parsed = parser.parse("gợi ý phim hoạt hình từ 2010 đến 2020 không kinh dị");
        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(parsed));

        assertThat(json.get("intent_type").asText()).isEqualTo("DISCOVERY");
        assertThat(json.get("mode").asText()).isEqualTo("recommend");
        assertThat(json.get("scope").asText()).isEqualTo("episode");
        assertThat(json.get("semantic_query").asText()).isEqualTo("gợi ý phim hoạt hình từ 2010 đến 2020 không kinh dị");
        assertThat(json.get("requested_k").asInt()).isEqualTo(6);
        assertThat(json.get("series_info").isNull()).isTrue();
        assertThat(json.get("include_genres").get(0).asText()).isEqualTo("animation");
        assertThat(json.get("exclude_genres").get(0).asText()).isEqualTo("horror");
        assertThat(json.get("mood_recommendation").asBoolean()).isFalse();
        assertThat(json.get("mood_target").asText()).isEqualTo("cheerful");
        assertThat(json.get("constraints").get("year_min").asInt()).isEqualTo(2010);
        assertThat(json.get("constraints").get("year_max").asInt()).isEqualTo(2020);
        assertThat(json.size()).isEqualTo(11);

        SearchConstraints constraints = parsed.toSearchConstraints();
        assertThat(constraints.includeGenres()).containsExactly("animation");
        assertThat(constraints.excludeGenres()).containsExactly("horror");
        assertThat(constraints.yearMin()).isEqualTo(2010);
        assertThat(constraints.yearMax()).isEqualTo(2020);
        assertThat(constraints.seriesOnly()).isFalse();
    }

    private static ChatIntent intent(
            String intentType,
            String mode,
            String scope,
            String semanticQuery,
            Map<String, Object> constraints,
            int requestedK,
            String seriesInfo,
            List<String> includeGenres,
            List<String> excludeGenres,
            boolean moodRecommendation,
            String moodTarget
    ) {
        return new ChatIntent(
                intentType,
                mode,
                scope,
                semanticQuery,
                constraints,
                requestedK,
                seriesInfo,
                includeGenres,
                excludeGenres,
                moodRecommendation,
                moodTarget
        );
    }

    private static Map<String, Object> mapOf(Object... entries) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            map.put((String) entries[i], entries[i + 1]);
        }
        return map;
    }
}
