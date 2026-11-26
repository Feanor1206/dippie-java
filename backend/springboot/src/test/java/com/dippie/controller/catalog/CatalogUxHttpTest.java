package com.dippie.controller.catalog;

import com.dippie.common.text.TextFolding;
import com.dippie.model.catalog.CatalogDocument;
import com.dippie.service.assistant.ChatStreamGuard;
import com.dippie.service.catalog.CatalogImportService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Bộ test tích hợp toàn bộ trải nghiệm tìm kiếm chịu lỗi chính tả, gợi ý phim, điểm đánh giá trực tiếp và hội thoại CineBot trên dữ liệu 6 bộ phim thật.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
// Lớp này cần CSDL và chỉ mục tìm kiếm trống lúc bắt đầu, nên dùng context riêng để không dính dữ liệu của test chạy trước.
@TestPropertySource(properties = "dippie.test.context=CatalogUxHttpTest")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CatalogUxHttpTest {

    private final MockMvc mvc;
    private final ObjectMapper mapper;
    private final CatalogImportService imports;
    private final ChatStreamGuard chatStreamGuard;

    CatalogUxHttpTest(MockMvc mvc, ObjectMapper mapper, CatalogImportService imports, ChatStreamGuard chatStreamGuard) {
        this.mvc = mvc;
        this.mapper = mapper;
        this.imports = imports;
        this.chatStreamGuard = chatStreamGuard;
    }

    @BeforeAll
    void importPublishedSeries() throws Exception {
        for (String series : List.of("trang_quynh", "ori", "peppa_pig", "doraemon", "jadoo", "larva")) {
            var path = Path.of("../../data/catalog-by-series/catalog." + series + ".json");
            assertThat(imports.importPackage(new CatalogDocument(mapper.readTree(Files.readAllBytes(path))))
                    .indexStatus()).isEqualTo("READY");
        }
    }

    @ParameterizedTest
    @CsvSource({
            "Trang Quynh, ser_trang_quynh, ep_trang_quynh_0001, 35",
            "Cong chua Ori, ser_ori, ep_ori_s01_0001, 180",
            "Peppa Pig, ser_peppa_pig, ep_peppa_pig_s01_0001, 51",
            "Peppa, ser_peppa_pig, ep_peppa_pig_s01_0001, 51",
            "Jadoo, ser_jadoo, ep_jadoo_s01_0001, 99",
            "Ori, ser_ori, ep_ori_s01_0001, 180",
            "Doremon, ser_doraemon, ep_doraemon_s05_0209, 337"
    })
    void ranksSeriesFirstAndFindsItsEpisodes(String query, String seriesId, String episodeId, int episodes) throws Exception {
        mvc.perform(get("/api/movies/browse").param("animationOnly", "true").param("query", query))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(seriesId))
                .andExpect(jsonPath("$.data.totalElements").value(greaterThan(episodes)));
        mvc.perform(get("/api/catalog/search").param("q", query)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(seriesId))
                .andExpect(jsonPath("$[?(@.type == 'episode')].id", hasItem(episodeId)));
    }

    @ParameterizedTest
    @CsvSource({"Xuka", "Shizuka"})
    void findsEpisodesByCharacterAlias(String query) throws Exception {
        mvc.perform(get("/api/movies/browse").param("query", query))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.item_type == 'episode')].series_id", hasItem("ser_doraemon")));
        mvc.perform(get("/api/catalog/search").param("q", query)).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.type == 'episode')].seriesId", hasItem("ser_doraemon")));
    }

    @ParameterizedTest
    @CsvSource({"pepa,ser_peppa_pig", "pepa pig,ser_peppa_pig", "peppa pgi,ser_peppa_pig", "pgi,ser_peppa_pig",
            "oir,ser_ori", "doraemom,ser_doraemon", "doraemxx,ser_doraemon", "larvaa,ser_larva",
            "trang quinh,ser_trang_quynh"})
    void browseAndCatalogSearchCorrectSeriesTitleAndAliasTypos(String query, String seriesId) throws Exception {
        mvc.perform(get("/api/movies/browse").param("query", query))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(seriesId));
        mvc.perform(get("/api/catalog/search").param("q", query))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(seriesId))
                .andExpect(jsonPath("$[?(@.type == 'episode')].seriesId", hasItem(seriesId)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"khung log", "khug long"})
    void browseCatalogSearchAndCineBotCorrectVietnameseTopicTypos(String query) throws Exception {
        mvc.perform(get("/api/movies/browse").param("query", query))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.series_id == 'ser_doraemon')].id",
                        hasItem("ep_doraemon_s05_0219")));
        mvc.perform(get("/api/catalog/search").param("q", query))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.seriesId == 'ser_doraemon')].id",
                        hasItem("ep_doraemon_s05_0219")));
        var cineBot = ask(query);
        assertThat(cineBot.path("recommendedMovies")).isNotEmpty();
        assertThat(TextFolding.fold(cineBot.path("text").asText())).contains("khung long");
    }

    @Test
    void searchDoesNotConfuseCommonVietnameseWordsWithThreeLetterSeriesTranspositions() throws Exception {
        var answer = ask("Trâu ốm rồi");
        assertThat(answer.path("recommendedMovies")).isNotEmpty();
        assertThat(answer.path("recommendedMovies").get(0).path("series_id").asText()).isEqualTo("ser_trang_quynh");
        mvc.perform(get("/api/catalog/search").param("q", "Trâu ốm rồi"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].seriesId").value("ser_trang_quynh"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"naruto", "conan", "frozen", "pokemon", "siêu nhân gao", "one piece",
            "tom and jerry", "cách nấu phở bò ngon nhất"})
    void topicTypoToleranceDoesNotMatchUnrelatedQueries(String query) throws Exception {
        mvc.perform(get("/api/movies/browse").param("query", query))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
        mvc.perform(get("/api/catalog/search").param("q", query))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"nobitta", "suneoo", "shizka"})
    void browseCatalogSearchAndCineBotCorrectCharacterNameTypos(String query) throws Exception {
        mvc.perform(get("/api/movies/browse").param("query", query))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.item_type == 'episode')].series_id", hasItem("ser_doraemon")));
        mvc.perform(get("/api/catalog/search").param("q", query))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.type == 'episode')].seriesId", hasItem("ser_doraemon")));
        var result = ask(query).path("recommendedMovies").get(0);
        assertThat(result.path("series_id").asText().equals("ser_doraemon")
                || result.path("id").asText().equals("ser_doraemon")).isTrue();
    }

    @Test
    void cineBotResolvesNumberedEpisodesAndSimilarityUsingTypoTolerantSeriesNames() throws Exception {
        var episode = ask("pepa pig tap 1").path("recommendedMovies").get(0);
        assertThat(episode.path("id").asText()).isEqualTo("ep_peppa_pig_s01_0001");
        var transposed = ask("peppa pgi tap 1").path("recommendedMovies").get(0);
        assertThat(transposed.path("id").asText()).isEqualTo("ep_peppa_pig_s01_0001");
        var similar = ask("phim giống pepa").path("recommendedMovies").valueStream().toList();
        assertThat(similar).hasSizeBetween(4, 6).extracting(item -> item.path("id").asText())
                .doesNotContain("ser_peppa_pig");
    }

    @Test
    void searchDoesNotFuzzWordsShorterThanFourCharacters() throws Exception {
        mvc.perform(get("/api/movies/browse").param("query", "ory"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.series_id == 'ser_ori')].id").isEmpty());
        mvc.perform(get("/api/catalog/search").param("q", "ory"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.seriesId == 'ser_ori')].id").isEmpty());
        assertThat(ask("phim giống ory").path("recommendedMovies")).isEmpty();
    }

    @Test
    void coldStartPicksOneEpisodePerSeriesBeforeRepeatingAnySeries() throws Exception {
        var items = recommendations("{\"limit\":6}", "");
        assertThat(items).extracting(item -> item.path("series_id").asText()).containsExactlyInAnyOrder(
                "ser_trang_quynh", "ser_ori", "ser_peppa_pig", "ser_doraemon", "ser_jadoo", "ser_larva");
        assertThat(items.stream().filter(item -> item.path("series_id").asText().equals("ser_peppa_pig")))
                .allMatch(item -> item.path("season_id").asText().equals("sea_peppa_pig_s01"));
        assertThat(items.stream().filter(item -> item.path("series_id").asText().equals("ser_doraemon")))
                .allMatch(item -> item.path("year").isNull());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ser_peppa_pig", "ep_peppa_pig_s01_0001"})
    void favorsRelatedEpisodesWithoutRepeatingOneSeriesTooOften(String likedId) throws Exception {
        var items = recommendations(mapper.createObjectNode().put("limit", 6)
                .set("likedMovieIds", mapper.createArrayNode().add(likedId)).toString(), "");
        assertThat(items).hasSize(6).extracting(item -> item.path("id").asText()).doesNotContain(likedId);
        assertThat(items.stream().filter(item -> item.path("series_id").asText().equals("ser_peppa_pig")))
                .hasSizeBetween(1, 2);
        var singleSeries = recommendations("""
                {"limit":6,"seriesId":"ser_peppa_pig","likedMovieIds":["ser_peppa_pig"]}
                """, "");
        assertThat(singleSeries).hasSize(6)
                .allMatch(item -> item.path("series_id").asText().equals("ser_peppa_pig"));
    }

    @Test
    void downranksOtherEpisodesOfALowRatedSeries() throws Exception {
        String request = "{\"limit\":6,\"seriesId\":\"ser_peppa_pig\"}";
        var before = recommendations(request, "").getFirst().path("score").asDouble();
        String bearer = register("low-rating");
        rate(bearer, "ser_peppa_pig", 4);
        try {
            var items = recommendations(request, bearer);
            assertThat(items).hasSize(6).allMatch(item -> item.path("score").asDouble() < before);
        } finally {
            removeRating(bearer, "ser_peppa_pig");
        }
    }

    @ParameterizedTest
    @CsvSource({"funny,60", "family,60", "bedtime,10", "adventure,60", "friendship,60", "short,10"})
    void moodPresetsReturnEpisodesWithinTheirRuntimeBounds(String preset, int minutes) throws Exception {
        var items = recommendations(mapper.createObjectNode().put("moodPreset", preset).put("limit", 6).toString(), "");
        assertThat(items).isNotEmpty().allMatch(item -> item.path("duration_ms").asLong() <= minutes * 60_000L)
                .extracting(item -> item.path("id").asText()).doesNotContain("ep_jadoo_s01_0014");
    }

    @Test
    void hidesTheCorruptJadooRuntimeInEpisodeAndMovieDetails() throws Exception {
        String id = "ep_jadoo_s01_0014";
        assertThat(movie(id).path("runtime").asText()).isEqualTo("Tập phim");
        assertThat(responseBody(mvc.perform(get("/api/catalog/episodes/" + id)).andExpect(status().isOk()))
                .path("episode").path("durationMs").isNull()).isTrue();
        var progress = responseBody(mvc.perform(put("/api/users/watch-progress/" + id)
                        .header(HttpHeaders.AUTHORIZATION, register("unknown-runtime"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assetId\":\"youtube:MgDMj7ekn5A\",\"positionMs\":5000,\"expectedVersion\":-1}"))
                .andExpect(status().isOk())).path("data");
        assertThat(progress.path("durationMs").isNull()).isTrue();
        assertThat(progress.path("completed").asBoolean()).isFalse();
    }

    @Test
    void bedtimeRanksGentleEpisodesAheadOfTheShortShelf() throws Exception {
        var bedtime = recommendations("{\"limit\":6,\"moodPreset\":\"bedtime\"}", "");
        var shorter = recommendations("{\"limit\":6,\"moodPreset\":\"short\"}", "");
        assertThat(bedtime).hasSize(6).allMatch(CatalogUxHttpTest::hasShortRuntime);
        assertThat(shorter).hasSize(6).allMatch(CatalogUxHttpTest::hasShortRuntime);
        assertThat(bedtime.getFirst().path("series_id").asText()).isEqualTo("ser_peppa_pig");
        assertThat(bedtime).extracting(item -> item.path("id").asText())
                .doesNotContain("ep_larva_s01_0001", "ep_larva_s01_0002");
        assertThat(bedtime).isNotEqualTo(shorter);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"limit\":6,\"moodPreset\":\"bedtime\"}",
            "{\"limit\":6,\"moodPreset\":\"bedtime\",\"likedMovieIds\":[\"ser_peppa_pig\"]}",
            "{\"limit\":6,\"mood\":\"thư giãn trước khi ngủ cho bé 5 tuổi\"}"})
    void bedtimeShelvesCapEverySeriesAtTwoOfSix(String requestBody) throws Exception {
        var items = recommendations(requestBody, "");
        assertThat(items).hasSize(6).allMatch(CatalogUxHttpTest::hasShortRuntime);
        var counts = items.stream().collect(Collectors.groupingBy(item -> item.path("series_id").asText(),
                Collectors.counting()));
        assertThat(counts.values()).containsExactlyInAnyOrder(2L, 2L, 2L);
        assertThat(items).extracting(item -> item.path("id").asText())
                .doesNotContain("ep_larva_s01_0001", "ep_larva_s01_0002", "ep_larva_s01_0010");
    }

    @Test
    void largerBedtimeShelvesUseTheSameProportionalCap() throws Exception {
        var items = recommendations("{\"limit\":20,\"moodPreset\":\"bedtime\"}", "");
        var counts = items.stream().collect(Collectors.groupingBy(item -> item.path("series_id").asText(),
                Collectors.counting()));
        assertThat(counts.values()).hasSizeGreaterThanOrEqualTo(3).allMatch(count -> count <= 7);
    }

    @Test
    void recommendationCardsReturnOnlyCanonicalGenreIdsAndStillAcceptGenreLabels() throws Exception {
        for (var item : recommendations("{\"limit\":2}", "")) {
            assertThat(item.path("genres").valueStream().map(JsonNode::asText).toList()).isNotEmpty()
                    .doesNotHaveDuplicates().isSubsetOf("animation", "comedy", "family", "school", "slice_of_life");
        }
        var family = recommendations("{\"limit\":2,\"genres\":[\"Gia đình\"]}", "");
        assertThat(family).hasSize(2).allMatch(item -> item.path("genres").valueStream()
                .anyMatch(genre -> "family".equals(genre.asText())));
    }

    @Test
    void interpretsFreeTextBedtimeAndSadMoodsWithoutInsomniaOrCompilations() throws Exception {
        var bedtime = recommendations("{\"limit\":6,\"mood\":\"thư giãn trước khi ngủ cho bé 5 tuổi\"}", "");
        assertThat(bedtime).hasSize(6).allMatch(CatalogUxHttpTest::hasShortRuntime)
                .extracting(item -> item.path("title").asText()).noneMatch(title -> title.contains("Mất Ngủ"));
        assertThat(bedtime.getFirst().path("series_id").asText()).isEqualTo("ser_peppa_pig");
        var sad = recommendations("{\"limit\":6,\"mood\":\"buồn\"}", "");
        assertThat(sad).isNotEmpty().allMatch(item -> item.path("duration_ms").asLong() <= 3_600_000L)
                .extracting(item -> item.path("id").asText())
                .doesNotContain("ep_jadoo_s04_0016", "ep_jadoo_s03_0016", "ep_jadoo_s01_0014");
    }

    private static boolean hasShortRuntime(JsonNode item) {
        long duration = item.path("duration_ms").asLong();
        return duration >= 60_000 && duration <= 600_000;
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"seriesId\":\"ser_ori\",\"limit\":100}",
            "{\"seriesId\":\"ser_ori\",\"limit\":100,\"moodPreset\":\"family\"}",
            "{\"seriesId\":\"ser_ori\",\"limit\":100,\"mood\":\"Ori\"}"
    })
    void excludesOriEpisodesThatOnlyOpenOnYoutubeFromRecommendations(String requestBody) throws Exception {
        assertThat(recommendations(requestBody, "")).isNotEmpty().extracting(item -> item.path("id").asText())
                .noneMatch(id -> id.startsWith("ep_ori_s05_"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"liệt kê các tập Công chúa Ori mùa 5", "Công chúa Ori mùa 5 có những tập nào",
            "Công chúa Ori mùa 5 gồm những tập nào", "tất cả các tập Công chúa Ori mùa 5"})
    void cineBotListsOriSeasonFiveAndExplainsItsYoutubePlayback(String query) throws Exception {
        var answer = ask(query);
        assertThat(answer.path("recommendedMovies")).hasSize(26).allMatch(item ->
                item.path("season_id").asText().equals("sea_ori_s05") && item.path("externalOnly").asBoolean());
        assertThat(answer.path("text").asText()).contains("Nhiệm Vụ Bất Khả Thi", "chỉ mở", "YouTube");
        assertThat(ask("gợi ý 6 tập Công chúa Ori mùa 5").path("recommendedMovies")).isEmpty();
    }

    @Test
    void cineBotListsMixedOriSeasonFourAndNotesOnlyTheExternalEpisodes() throws Exception {
        var answer = ask("liệt kê các tập Công chúa Ori mùa 4");
        var items = answer.path("recommendedMovies").valueStream().toList();
        assertThat(items).hasSize(24);
        assertThat(items.stream().filter(item -> item.path("externalOnly").asBoolean()).count()).isEqualTo(16);
        assertThat(answer.path("text").asText()).contains("Các tập chỉ mở được trên YouTube gồm");
    }

    @Test
    void cineBotMarksTheOriSeasonFiveEpisodeLookedUpByNumber() throws Exception {
        var answer = ask("Công chúa Ori mùa 5 tập 1");
        var item = answer.path("recommendedMovies").get(0);
        assertThat(item.path("id").asText()).isEqualTo("ep_ori_s05_0001");
        assertThat(item.path("externalOnly").asBoolean()).isTrue();
        assertThat(answer.path("text").asText()).contains("Nhiệm Vụ Bất Khả Thi", "chỉ mở", "YouTube");
    }

    @Test
    void keepsExternalOnlyOriEpisodesSearchableButRejectsTheirWatchProgress() throws Exception {
        String id = "ep_ori_s05_0001";
        String title = movie(id).path("title").asText();
        assertThat(responseBody(mvc.perform(get("/api/catalog/episodes/" + id)).andExpect(status().isOk()))
                .path("youtube").path("canEmbed").asBoolean()).isFalse();
        mvc.perform(get("/api/catalog/search").param("q", title)).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.type == 'episode')].id", hasItem(id)));
        mvc.perform(get("/api/movies/browse").param("query", title)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].id", hasItem(id)));
        assertThat(ask(title).path("recommendedMovies").valueStream().map(item -> item.path("id").asText()))
                .contains(id);
        assertThat(ask("gợi ý 100 tập Công chúa Ori").path("recommendedMovies").valueStream()
                .map(item -> item.path("id").asText())).isNotEmpty().noneMatch(item -> item.startsWith("ep_ori_s05_"));

        mvc.perform(put("/api/users/watch-progress/" + id).header(HttpHeaders.AUTHORIZATION, register("ori-progress"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assetId\":\"youtube:SSubOeKMP2c\",\"positionMs\":60000,\"expectedVersion\":-1}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsUnknownMoodPresetsAndFallsBackForFreeTextWithoutHits() throws Exception {
        mvc.perform(post("/api/recommend").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"moodPreset\":\"unknown\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Tâm trạng bạn chọn không có trong danh sách."));
        assertThat(recommendations("{\"limit\":6,\"mood\":\"zzzzz-no-catalog-result\"}", "")).hasSize(6);
    }

    @Test
    void exposesLiveRatingAggregatesInEveryCardEndpoint() throws Exception {
        String seriesId = "ser_peppa_pig";
        String episodeId = "ep_peppa_pig_s01_0003";
        String first = register("rating-first");
        String second = register("rating-second");
        assertRating(movie(episodeId), null, 0);
        rate(first, seriesId, 9);
        rate(second, seriesId, 8);
        rate(first, episodeId, 6);
        rate(second, episodeId, 9);

        assertRating(movie(seriesId), 8.5, 2);
        assertRating(seriesCard(seriesId), 8.5, 2);
        assertRating(responseBody(mvc.perform(get("/api/catalog/series/" + seriesId))
                .andExpect(status().isOk())).path("series"), 8.5, 2);
        assertEpisodeRatings(episodeId, 7.5, 2);

        rate(first, episodeId, 4);
        assertEpisodeRatings(episodeId, 6.5, 2);
        removeRating(first, episodeId);
        assertEpisodeRatings(episodeId, 9.0, 1);
        removeRating(second, episodeId);
        assertEpisodeRatings(episodeId, null, 0);
        removeRating(first, seriesId);
        removeRating(second, seriesId);
        assertRating(seriesCard(seriesId), null, 0);
    }

    private void assertEpisodeRatings(String episodeId, Double average, int count) throws Exception {
        assertRating(movie(episodeId), average, count);
        assertRating(responseBody(mvc.perform(get("/api/catalog/episodes/" + episodeId))
                .andExpect(status().isOk())).path("episode"), average, count);
        assertRating(findItem(responseBody(mvc.perform(get("/api/catalog/series/ser_peppa_pig"))
                .andExpect(status().isOk())).path("episodes"), episodeId), average, count);
        assertRating(findItem(responseBody(mvc.perform(get("/api/catalog/search").param("q", "Best Friend"))
                .andExpect(status().isOk())), episodeId), average, count);
        assertRating(findItem(responseBody(mvc.perform(get("/api/movies/browse").param("query", "Best Friend"))
                .andExpect(status().isOk())).path("data").path("items"), episodeId), average, count);
        assertRating(recommendations("{\"seriesId\":\"ser_peppa_pig\",\"limit\":100}", "").stream()
                .filter(item -> item.path("id").asText().equals(episodeId)).findFirst().orElseThrow(), average, count);
        assertRating(findItem(chat(mapper.createObjectNode().put("message", "tóm tắt tập 3 Peppa"))
                .path("recommendedMovies"), episodeId), average, count);
    }

    @Test
    void browseOrdersByLiveCommunityAverageWithUnratedItemsLast() throws Exception {
        String first = register("browse-sort-first");
        String second = register("browse-sort-second");
        String series = "ser_peppa_pig";
        String episode = "ep_peppa_pig_s01_0001";
        rate(first, series, 5);
        rate(first, episode, 10);
        rate(second, episode, 7);
        try {
            assertBrowseRatingOrder("desc", null, episode, series);
            assertBrowseRatingOrder("asc", null, series, episode);
            assertBrowseRatingOrder("desc", "Peppa", episode, series);
            rate(first, series, 9);
            assertBrowseRatingOrder("desc", null, series, episode);
            removeRating(first, series);
            assertBrowseRatingOrder("asc", null, episode);
        } finally {
            removeRating(first, episode);
            removeRating(second, episode);
            removeRating(first, series);
        }
    }

    private void assertBrowseRatingOrder(String direction, String query, String... ratedIds) throws Exception {
        var request = get("/api/movies/browse").param("animationOnly", "true").param("sort", "rating")
                .param("direction", direction).param("size", Integer.toString(ratedIds.length + 1));
        if (query != null) {
            request.param("query", query);
        }
        var items = responseBody(mvc.perform(request).andExpect(status().isOk())).path("data").path("items")
                .valueStream().toList();
        assertThat(items.subList(0, ratedIds.length)).extracting(item -> item.path("id").asText())
                .containsExactly(ratedIds);
        assertRating(items.getLast(), null, 0);
    }

    @ParameterizedTest
    @CsvSource({"asc,2004", "desc,2023"})
    void browsePutsUnknownYearsLast(String direction, int firstYear) throws Exception {
        var page = responseBody(mvc.perform(get("/api/movies/browse").param("sort", "year")
                .param("direction", direction).param("size", "3")).andExpect(status().isOk())).path("data");
        assertThat(page.path("items").get(0).path("year").asInt()).isEqualTo(firstYear);
        var last = responseBody(mvc.perform(get("/api/movies/browse").param("sort", "year")
                .param("direction", direction).param("size", "3")
                .param("page", Integer.toString(page.path("totalPages").asInt() - 1)))
                .andExpect(status().isOk())).path("data").path("items");
        assertThat(last).isNotEmpty().allMatch(item -> item.path("year").isNull());
    }

    @ParameterizedTest
    @ValueSource(strings = {"yearMin", "yearMax"})
    void yearBoundsExcludeUnknownCatalogYears(String parameter) throws Exception {
        assertThat(movie("ep_doraemon_s05_0209").path("year").isNull()).isTrue();
        mvc.perform(get("/api/movies/browse").param("query", "Doraemon").param(parameter, "2011"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
        mvc.perform(get("/api/movies/browse").param("query", "Peppa").param(parameter, "2004"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(greaterThan(0)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"thời tiết Hà Nội hôm nay thế nào", "có phim Naruto hoặc One Piece không",
            "giá vàng hôm nay bao nhiêu", "có phim Siêu nhân Gao không", "cách nấu phở bò ngon nhất",
            "nấu phở bò như thế nào", "thủ đô của Pháp là gì", "bây giờ là mấy giờ",
            "2 cộng 2 bằng mấy", "2 cộng 2 bằng mấy vậy?", "hai cộng hai bằng mấy", "7 nhân 8 bằng bao nhiêu"})
    void cineBotReportsNoMatchAndNamesTheAvailableSeries(String query) throws Exception {
        var answer = ask(query);
        assertThat(answer.path("recommendedMovies")).isEmpty();
        assertThat(answer.path("text").asText()).contains("chưa tìm thấy", "Doraemon", "Larva", "Heo Peppa",
                "Xin Chào Jadoo", "Công chúa Ori", "Trạng Quỳnh");
    }

    @Test
    void cineBotIntroducesItselfAfterACombinedGreeting() throws Exception {
        var answer = ask("xin chào bạn là ai");
        assertThat(answer.path("recommendedMovies")).isEmpty();
        assertThat(answer.path("text").asText()).startsWith("Mình là CineBot.");
    }

    @ParameterizedTest
    @CsvSource({
            "phim giống Larva,ser_larva",
            "phim tương tự Larva,ser_larva",
            "phim kiểu như Larva,ser_larva",
            "phim giống Peppa,ser_peppa_pig",
            "phim giống Jadoo,ser_jadoo",
            "phim giống Trạng Quỳnh,ser_trang_quynh",
            "phim giống Ori,ser_ori",
            "phim giống Doremon,ser_doraemon"
    })
    void cineBotSuggestsSimilarSeriesWithoutRepeatingTheReference(String query, String reference) throws Exception {
        var answer = ask(query);
        var items = answer.path("recommendedMovies").valueStream().toList();
        assertThat(items).hasSizeBetween(4, 6)
                .allMatch(item -> item.path("item_type").asText().equals("series"));
        assertThat(items).extracting(item -> item.path("id").asText()).doesNotContain(reference);
        assertThat(answer.path("followUps").valueStream().map(JsonNode::asText)).doesNotContain("Không, tập khác");
        assertThat(answer.path("text").asText()).contains("bộ phim", items.get(0).path("title").asText(),
                items.get(1).path("title").asText());
    }

    @ParameterizedTest
    @CsvSource({"Peppa,51,Heo Peppa", "Jadoo,99,Xin Chào Jadoo", "Trạng Quỳnh,35,Trạng Quỳnh",
            "Ori,180,Công chúa Ori", "Doremon,337,Doraemon"})
    void cineBotResolvesShortNamesForSeriesQuestions(String alias, int episodes, String title) throws Exception {
        assertThat(ask(alias + " có bao nhiêu tập").path("text").asText())
                .contains(episodes + " tập", title);
    }

    @ParameterizedTest
    @CsvSource({"cảm ơn bạn nhiều,Không có gì", "cảm ơn nhé,Không có gì", "cảm ơn ạ,Không có gì",
            "tạm biệt,Hẹn gặp lại", "tạm biệt nhé,Hẹn gặp lại", "tạm biệt ạ,Hẹn gặp lại",
            "hẹn gặp lại nhé,Hẹn gặp lại", "bye nha,Hẹn gặp lại", "see you later,Hẹn gặp lại",
            "tạm biệt bạn nhé,Hẹn gặp lại", "tạm biệt cảm ơn bạn,Hẹn gặp lại",
            "chào bạn,Mình là CineBot", "chào bạn nhé,Mình là CineBot"})
    void cineBotTreatsGreetingsAndGoodbyesAsConversation(String query, String prefix) throws Exception {
        var answer = ask(query);
        assertThat(answer.path("recommendedMovies")).isEmpty();
        assertThat(answer.path("text").asText()).startsWith(prefix);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Không, tập khác", "tập khác"})
    void cineBotAlternativesExcludeEveryItemFromThePreviousAnswer(String followUp) throws Exception {
        String question = "có phim nào về tình bạn cho bé 5 tuổi không";
        var previous = ask(question);
        var ids = previous.path("recommendedMovies").valueStream().map(item -> item.path("id").asText()).toList();
        assertThat(ids).hasSize(6);
        var answer = followUp(question, previous, followUp).path("recommendedMovies").valueStream().toList();
        assertThat(answer).hasSize(6).extracting(item -> item.path("id").asText()).doesNotContainAnyElementsOf(ids);
    }

    @Test
    void cineBotAlternativesExcludeEarlierAndLatestAnswersAcrossRepeatedRejections() throws Exception {
        String question = "có phim nào về tình bạn cho bé 5 tuổi không";
        String rejection = "Không, tập khác";
        var first = ask(question);
        assertThat(first.path("followUps").valueStream().map(JsonNode::asText)).contains(rejection);
        var history = mapper.createArrayNode();
        addTurn(history, question, first);
        var second = ask(rejection, history);
        assertThat(second.path("followUps").valueStream().map(JsonNode::asText)).contains(rejection);
        addTurn(history, rejection, second);
        var third = ask(rejection, history);

        var allIds = List.of(first, second, third).stream().flatMap(answer -> answer.path("recommendedMovies").valueStream())
                .map(item -> item.path("id").asText()).toList();
        assertThat(allIds).hasSize(18).doesNotHaveDuplicates().doesNotContain("ep_jadoo_s04_0009");
    }

    @Test
    void cineBotReportsExhaustedAlternativesAndDoesNotOfferTheChip() throws Exception {
        String question = "gợi ý 100 tập Heo Peppa";
        String rejection = "Không, tập khác";
        var previous = ask(question);
        assertThat(previous.path("recommendedMovies")).hasSize(51);
        assertThat(previous.path("followUps").valueStream().map(JsonNode::asText)).doesNotContain(rejection);
        var history = mapper.createArrayNode();
        addTurn(history, question, previous);
        var exhausted = ask(rejection, history);
        assertThat(exhausted.path("recommendedMovies")).isEmpty();
        assertThat(exhausted.path("text").asText()).contains("Không còn tập phù hợp nào khác");
        assertThat(exhausted.path("followUps").valueStream().map(JsonNode::asText)).doesNotContain(rejection);
        addTurn(history, rejection, exhausted);
        assertThat(ask(rejection, history).path("recommendedMovies")).isEmpty();
    }

    @Test
    void cineBotTreatsSadEpisodeQuestionsAsDiscovery() throws Exception {
        var answer = ask("có tập phim buồn nào không");
        assertThat(answer.path("structuredIntent").path("requested_k").asInt()).isEqualTo(6);
        assertThat(answer.path("recommendedMovies").valueStream().toList()).hasSizeGreaterThan(1)
                .allMatch(item -> item.path("item_type").asText().equals("episode"));
    }

    @Test
    void cineBotShorterFollowUpHonorsRuntimeExclusionAndAnswersEpisodeDetails() throws Exception {
        String question = "có phim nào về gia đình vui nhộn không";
        String shorter = "còn tập nào ngắn hơn không, không phải Doraemon";
        var next = followUp(question, ask(question), shorter);
        var items = next.path("recommendedMovies").valueStream().toList();
        assertThat(items).isNotEmpty().allMatch(CatalogUxHttpTest::hasShortRuntime)
                .noneMatch(item -> item.path("series_id").asText().equals("ser_doraemon"));

        var details = followUp(shorter, next, "tập đó thuộc bộ nào và có nhân vật nào");
        var episode = items.getFirst();
        assertThat(details.path("recommendedMovies").get(0).path("id")).isEqualTo(episode.path("id"));
        assertThat(details.path("text").asText()).contains("thuộc bộ", episode.path("series_title").asText(), "Nhân vật");
        var characters = episode.path("characters").valueStream().map(JsonNode::asText).toList();
        if (characters.isEmpty()) {
            assertThat(details.path("text").asText()).contains("chưa được ghi");
        } else {
            assertThat(details.path("text").asText()).contains(characters.toArray(String[]::new));
        }
    }

    private JsonNode followUp(String question, JsonNode previous, String query) throws Exception {
        var messages = mapper.createArrayNode();
        addTurn(messages, question, previous);
        return ask(query, messages);
    }

    private void addTurn(ArrayNode messages, String question, JsonNode previous) {
        messages.addObject().put("role", "user").put("content", question);
        var ids = messages.addObject().put("role", "assistant").put("content", previous.path("text").asText())
                .putArray("itemIds");
        previous.path("recommendedMovies").valueStream().forEach(item -> ids.add(item.path("id").asText()));
    }

    private JsonNode ask(String query, ArrayNode messages) throws Exception {
        return chat(mapper.createObjectNode().put("message", query).set("messages", messages));
    }

    @ParameterizedTest
    @CsvSource({
            "ngắn dưới 5 phút cho bé xem trước khi ngủ, 299999",
            "có phim nào về tình bạn cho bé 5 tuổi không, 3600000",
            "gợi ý tập ngắn cho trẻ mầm non, 600000",
            "gợi ý phim hài nhẹ nhàng ngắn dưới 5 phút cho bé xem trước khi ngủ, 299999"
    })
    void cineBotOffersShortEpisodeChoicesForYoungChildren(String query, long maxDurationMs) throws Exception {
        var answer = ask(query);
        var items = answer.path("recommendedMovies").valueStream().toList();
        assertThat(items).hasSizeBetween(4, 6).allMatch(item ->
                item.path("item_type").asText().equals("episode") && item.path("duration_ms").asLong() >= 60_000
                        && item.path("duration_ms").asLong() <= maxDurationMs);
        assertThat(items.getFirst().path("duration_ms").asLong()).isLessThanOrEqualTo(600_000);
        assertThat(items).extracting(item -> item.path("title").asText())
                .noneMatch(title -> title.contains("Đêm đáng sợ") || title.contains("Thunderstorm")
                        || title.contains("Mister Skinnylegs"));
    }

    @Test
    void cineBotKeepsKnownAliasesAndCatalogQuestionsWorking() throws Exception {
        var alias = ask("Doremon").path("recommendedMovies").get(0);
        assertThat(alias.path("id").asText().equals("ser_doraemon")
                || alias.path("series_id").asText().equals("ser_doraemon")).isTrue();
        var dinosaur = ask("khung long");
        assertThat(dinosaur.path("recommendedMovies")).isNotEmpty();
        assertThat(TextFolding.fold(dinosaur.path("text").asText())).contains("khung long");
        assertThat(ask("tóm tắt tập 3 Peppa").path("text").asText()).contains("Tóm tắt", "Best Friend");
        assertThat(ask("Larva có bao nhiêu tập").path("text").asText()).contains("104 tập", "Larva");
        assertThat(ask("Jadoo sợ tăng cân").path("recommendedMovies").get(0).path("title").asText())
                .contains("sợ tăng cân");
        assertThat(ask("nhảy múa trong mưa").path("recommendedMovies").get(0).path("id").asText())
                .isEqualTo("ep_larva_s01_0003");
        var availability = ask("có phim Doraemon không").path("recommendedMovies").get(0);
        assertThat(availability.path("id").asText().equals("ser_doraemon")
                || availability.path("series_id").asText().equals("ser_doraemon")).isTrue();

        var characters = ask("nhân vật chính trong Peppa Pig là ai");
        assertThat(characters.path("text").asText()).contains("Chloé Pig", "Daddy Pig", "George Pig")
                .doesNotContain("Chloé,", "Daddy,", "George,");
        assertThat(characters.path("recommendedMovies").get(0).path("genres").valueStream().map(JsonNode::asText))
                .containsExactlyInAnyOrder("animation", "comedy", "family");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Công chúa Ori mùa 5 tập 1", "Peppa Pig tập 1"})
    void cineBotCardsReturnUniqueCanonicalGenreIds(String query) throws Exception {
        var genres = ask(query).path("recommendedMovies").get(0).path("genres");
        assertThat(genres.valueStream().map(JsonNode::asText).toList()).isNotEmpty().doesNotHaveDuplicates()
                .isSubsetOf("animation", "comedy", "family", "school", "slice_of_life");
    }

    @ParameterizedTest
    @ValueSource(strings = {"cảnh sau đó thì sao", "tiếp theo thì sao"})
    void cineBotMovesToTheNextSegmentOnTheSameEpisodeTimeline(String followUp) throws Exception {
        String query = "tập Doraemon nào Nobita đi vũ trụ";
        var first = ask(query);
        var card = first.path("recommendedMovies").get(0);
        assertThat(card.path("series_id").asText()).isEqualTo("ser_doraemon");
        assertThat(card.path("year").isNull()).isTrue();
        var evidence = card.path("evidence").get(0);
        assertThat(evidence.path("timeline").asText()).isEqualTo("episode");
        assertThat(evidence.path("startMs").isNumber()).isTrue();

        var history = mapper.createArrayNode();
        history.addObject().put("role", "user").put("content", query);
        var previous = history.addObject().put("role", "assistant").put("content", first.path("text").asText());
        previous.putArray("itemIds").add(card.path("id").asText());
        previous.putArray("documentIds").add("older-document").add(evidence.path("documentId").asText());
        var body = mapper.createObjectNode().put("message", followUp);
        body.set("messages", history);

        var next = chat(body).path("recommendedMovies").get(0);
        assertThat(next.path("id").asText()).isEqualTo(card.path("id").asText());
        var nextEvidence = next.path("evidence").get(0);
        assertThat(nextEvidence.path("documentId").asText()).isNotEqualTo(evidence.path("documentId").asText());
        assertThat(nextEvidence.path("startMs").asLong()).isGreaterThan(evidence.path("startMs").asLong());
    }

    @ParameterizedTest
    @CsvSource({"tóm tắt tập đầu tiên bạn vừa gợi ý,0", "tóm tắt nó,0", "tập thứ hai,1", "phim thứ 3,2",
            "tập đó,0", "phim đó,0"})
    void cineBotResolvesItemReferencesAgainstTheLatestAnswer(String query, int index) throws Exception {
        var history = mapper.createArrayNode();
        history.addObject().put("role", "assistant").put("content", "Gợi ý trước.")
                .putArray("itemIds").add("ser_larva");
        var recent = history.addObject().put("role", "assistant").put("content", "Đây là ba tập.");
        var ids = recent.putArray("itemIds").add("ep_peppa_pig_s01_0003").add("ep_peppa_pig_s01_0004")
                .add("ep_peppa_pig_s01_0005");
        var body = mapper.createObjectNode().put("message", query);
        body.set("messages", history);
        var answer = chat(body);
        assertThat(answer.path("recommendedMovies").get(0).path("id").asText()).isEqualTo(ids.get(index).asText());
        if (query.startsWith("tóm tắt")) {
            assertThat(answer.path("text").asText()).contains("Tóm tắt", "Best Friend");
        }
    }

    @Test
    void cineBotNamesMultipleItemsWhenAskedForAFewEpisodes() throws Exception {
        var answer = ask("cho mình vài tập");
        var items = answer.path("recommendedMovies").valueStream().toList();
        assertThat(items).hasSizeBetween(4, 6);
        assertThat(items.stream().map(item -> item.path("series_id").asText()).distinct().count())
                .isGreaterThanOrEqualTo(4);
        assertThat(answer.path("text").asText()).contains(items.get(0).path("title").asText().replaceFirst("[.]+$", ""),
                items.get(1).path("title").asText().replaceFirst("[.]+$", ""));
    }

    @Test
    void cineBotNamesEachOfThreeEpisodeSuggestions() throws Exception {
        var answer = ask("cho mình 3 tập về tình bạn");
        var items = answer.path("recommendedMovies").valueStream().toList();
        assertThat(items).hasSize(3);
        assertThat(answer.path("text").asText()).startsWith("Bạn có thể xem tập");
        assertThat(answer.path("text").asText()).contains(items.stream()
                .map(item -> item.path("title").asText().replaceFirst("[.]+$", "")).toArray(String[]::new));
    }

    @Test
    void cineBotValidatesMessageLengthInVietnamese() throws Exception {
        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.createObjectNode().put("message", "x".repeat(4_005)).toString()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Câu hỏi không được vượt quá 4000 ký tự."));
    }

    private JsonNode ask(String query) throws Exception {
        return chat(mapper.createObjectNode().put("message", query));
    }

    @Test
    void usesTheVietnameseSeriesRuntimeInMovieAndChatCards() throws Exception {
        assertThat(movie("ser_larva").path("runtime").asText()).isEqualTo("Phim bộ");
        var card = ask("Larva có bao nhiêu tập").path("recommendedMovies").get(0);
        assertThat(card.path("runtime").asText()).isEqualTo("Phim bộ");
    }

    private void removeRating(String bearer, String id) throws Exception {
        mvc.perform(delete("/api/users/ratings/" + id).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk());
    }

    private JsonNode movie(String id) throws Exception {
        return responseBody(mvc.perform(get("/api/movies/" + id)).andExpect(status().isOk())).path("data");
    }

    private JsonNode seriesCard(String id) throws Exception {
        return findItem(responseBody(mvc.perform(get("/api/catalog/series/cards")).andExpect(status().isOk())), id);
    }

    private static JsonNode findItem(JsonNode items, String id) {
        return StreamSupport.stream(items.spliterator(), false).filter(item -> item.path("id").asText().equals(id))
                .findFirst().orElseThrow();
    }

    private static void assertRating(JsonNode item, Double average, int count) {
        assertThat(item.path("ratingCount").isInt()).isTrue();
        assertThat(item.path("ratingCount").asInt()).isEqualTo(count);
        assertThat(item.has("averageRating")).isTrue();
        if (average == null) {
            assertThat(item.path("averageRating").isNull()).isTrue();
        } else {
            assertThat(item.path("averageRating").asDouble()).isEqualTo(average);
        }
    }

    private JsonNode chat(JsonNode body) throws Exception {
        chatStreamGuard.resetRateLimit();
        var started = mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM).content(body.toString()))
                .andExpect(request().asyncStarted()).andReturn();
        String stream = mvc.perform(asyncDispatch(started)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String done = Arrays.stream(stream.split("\\R\\R")).filter(frame -> frame.startsWith("event: done\n"))
                .findFirst().orElseThrow(() -> new AssertionError(stream));
        return mapper.readTree(done.substring(done.indexOf("data: ") + "data: ".length()));
    }

    private String register(String username) throws Exception {
        var body = mapper.createObjectNode().put("username", username).put("email", username + "@example.com")
                .put("password", "password123").put("fullName", "Người xem").put("acceptedTerms", true);
        var response = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(body.toString()))
                .andExpect(status().isCreated());
        return "Bearer " + responseBody(response).path("data").path("token").asText();
    }

    private void rate(String bearer, String id, int rating) throws Exception {
        mvc.perform(post("/api/users/ratings").header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.createObjectNode().put("movieId", id).put("rating", rating).toString()))
                .andExpect(status().isOk());
    }

    private List<JsonNode> recommendations(String request, String token) throws Exception {
        var response = mvc.perform(post("/api/recommend").contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, token).content(request))
                .andExpect(status().isOk());
        return StreamSupport.stream(responseBody(response).path("recommendedMovies").spliterator(), false).toList();
    }

    private JsonNode responseBody(ResultActions response) throws Exception {
        return mapper.readTree(response.andReturn().getResponse().getContentAsByteArray());
    }
}
