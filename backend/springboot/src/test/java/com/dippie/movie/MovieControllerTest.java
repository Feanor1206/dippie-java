package com.dippie.movie;

import com.dippie.catalog.domain.CatalogEpisode;
import com.dippie.catalog.domain.CatalogEpisodeRepository;
import com.dippie.catalog.domain.CatalogMediaAsset;
import com.dippie.catalog.domain.CatalogMediaAssetRepository;
import com.dippie.catalog.domain.CatalogSeason;
import com.dippie.catalog.domain.CatalogSeasonRepository;
import com.dippie.catalog.domain.CatalogSeries;
import com.dippie.catalog.domain.CatalogSeriesRepository;
import com.dippie.catalog.query.CatalogReadModelCache;
import com.dippie.media.MediaAsset;
import com.dippie.media.MediaAssetRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class MovieControllerTest {

    private static final String READY = "{\"status\":\"ready\"}";
    private static final String DRAFT = "{\"status\":\"draft\"}";

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final MovieRepository movieRepository;
    private final MediaAssetRepository mediaAssetRepository;
    private final CatalogSeriesRepository seriesRepository;
    private final CatalogSeasonRepository seasonRepository;
    private final CatalogEpisodeRepository episodeRepository;
    private final CatalogMediaAssetRepository catalogMediaAssetRepository;
    private final EntityManagerFactory entityManagerFactory;
    private final MovieGenreRepository movieGenres;
    private final MovieGenreTermRepository genreTerms;
    private final CatalogReadModelCache cache;

    MovieControllerTest(
            MockMvc mockMvc,
            ObjectMapper objectMapper,
            MovieRepository movieRepository,
            MediaAssetRepository mediaAssetRepository,
            CatalogSeriesRepository seriesRepository,
            CatalogSeasonRepository seasonRepository,
            CatalogEpisodeRepository episodeRepository,
            CatalogMediaAssetRepository catalogMediaAssetRepository,
            EntityManagerFactory entityManagerFactory,
            MovieGenreRepository movieGenres,
            MovieGenreTermRepository genreTerms,
            CatalogReadModelCache cache
    ) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.movieRepository = movieRepository;
        this.mediaAssetRepository = mediaAssetRepository;
        this.seriesRepository = seriesRepository;
        this.seasonRepository = seasonRepository;
        this.episodeRepository = episodeRepository;
        this.catalogMediaAssetRepository = catalogMediaAssetRepository;
        this.entityManagerFactory = entityManagerFactory;
        this.movieGenres = movieGenres;
        this.genreTerms = genreTerms;
        this.cache = cache;
    }

    @BeforeEach
    void setUp() {
        mediaAssetRepository.deleteAll();
        episodeRepository.deleteAll();
        seasonRepository.deleteAll();
        seriesRepository.deleteAll();
        catalogMediaAssetRepository.deleteAll();
        movieRepository.deleteAll();
        cache.invalidate();
    }

    @Test
    void browseFiltersLinkedAndLegacyGenresWithBoundedSqlAsTheCatalogGrows() throws Exception {
        movieRepository.saveAll(List.of(
                movie("linked", "100% Film", 2024, 9.0, "Comedy", null),
                movie("legacy", "Second", 2024, 8.0, "\tFamily , Science Fiction ", "100% magic"),
                movie("mismatch", "100% Wrong genre", 2024, 10.0, "Family,Science Fiction", null),
                movie("wildcard", "100X Wrong text", 2024, 10.0, "Family,Science Fiction", null)
        ));
        genreTerms.saveAll(List.of(new MovieGenreTerm("Family"), new MovieGenreTerm("Science Fiction"),
                new MovieGenreTerm("Comedy")));
        movieGenres.saveAll(List.of(new MovieGenre("linked", "Family"), new MovieGenre("linked", "Science Fiction"),
                new MovieGenre("mismatch", "Comedy")));

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        boolean previouslyEnabled = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        try {
            browseFilteredPage(2);
            long before = statistics.getPrepareStatementCount();
            browseFilteredPage(2);
            long small = statistics.getPrepareStatementCount() - before;

            movieRepository.saveAll(IntStream.range(0, 160)
                    .mapToObj(i -> movie("legacy-" + i, "100% film " + i, 2024, 7.0, "Family, Science Fiction", null))
                    .toList());
            before = statistics.getPrepareStatementCount();
            browseFilteredPage(162);
            assertThat(statistics.getPrepareStatementCount() - before).isEqualTo(small);
            assertThat(small).isBetween(2L, 4L);
        } finally {
            statistics.setStatisticsEnabled(previouslyEnabled);
        }
        mockMvc.perform(get("/api/movies").param("genre", "science fiction").param("q", "100%"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(162))
                .andExpect(jsonPath("$.data[0].id").value("linked"));
    }

    private void browseFilteredPage(int total) throws Exception {
        mockMvc.perform(get("/api/movies/browse").param("q", "100%").param("genres", "family,science fiction")
                        .param("yearMin", "2024").param("yearMax", "2024").param("page", "1").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(total))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value("legacy"));
    }

    @Test
    void listsEnrichesAndHidesUnpublishedCatalogMovies() throws Exception {
        movieRepository.saveAll(List.of(
                movie("plain-1", "The Last Orbit", 2024, 9.1, "Sci-Fi, Adventure", "Orbiting space"),
                movie("ser-pub", "Published Series", 2023, 8.8, "Animation, Family", "Series synopsis"),
                movie("ep-pub", "Published Episode", 2023, 8.5, "Animation", "Episode synopsis"),
                movie("ser-draft", "Draft Series", 2025, 9.9, "Animation", "Hidden series"),
                movie("ep-draft", "Draft Episode", 2025, 9.8, "Animation", "Hidden episode"),
                movie("ep-draft-season", "Draft Season Episode", 2025, 9.7, "Animation", "Hidden season ep")
        ));
        seriesRepository.saveAll(List.of(
                series("ser-pub", "Published Series", READY),
                series("ser-draft", "Draft Series", DRAFT)
        ));
        seasonRepository.saveAll(List.of(
                season("sea-pub", "ser-pub", READY),
                season("sea-draft", "ser-pub", DRAFT)
        ));
        episodeRepository.saveAll(List.of(
                episode("ep-pub", "ser-pub", "sea-pub", 3, READY),
                episode("ep-draft", "ser-pub", "sea-pub", 4, DRAFT),
                episode("ep-draft-season", "ser-pub", "sea-draft", 5, READY)
        ));

        MvcResult listResult = mockMvc.perform(get("/api/movies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        JsonNode items = objectMapper.readTree(listResult.getResponse().getContentAsString()).path("data");
        assertThat(items).hasSize(3);

        JsonNode plain = items.get(0);
        assertThat(plain.path("id").asText()).isEqualTo("plain-1");
        assertThat(plain.has("item_type")).isFalse();
        assertThat(plain.has("series_id")).isFalse();
        assertThat(plain.has("episode_id")).isFalse();
        assertThat(plain.has("episode_number")).isFalse();

        JsonNode seriesNode = items.get(1);
        assertThat(seriesNode.path("id").asText()).isEqualTo("ser-pub");
        assertThat(seriesNode.path("item_type").asText()).isEqualTo("series");
        assertThat(seriesNode.path("series_id").asText()).isEqualTo("ser-pub");
        assertThat(seriesNode.has("episode_id")).isFalse();
        assertThat(seriesNode.has("episode_number")).isFalse();

        JsonNode episodeNode = items.get(2);
        assertThat(episodeNode.path("id").asText()).isEqualTo("ep-pub");
        assertThat(episodeNode.path("item_type").asText()).isEqualTo("episode");
        assertThat(episodeNode.path("series_id").asText()).isEqualTo("ser-pub");
        assertThat(episodeNode.path("episode_id").asText()).isEqualTo("ep-pub");
        assertThat(episodeNode.path("episode_number").intValue()).isEqualTo(3);

        mockMvc.perform(get("/api/movies/ep-draft"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Không tìm thấy phim."));

        mockMvc.perform(get("/api/movies/ep-draft-season"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Không tìm thấy phim."));

        mockMvc.perform(get("/api/movies/ser-draft"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Không tìm thấy phim."));

        mockMvc.perform(get("/api/movies/ep-pub"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.item_type").value("episode"))
                .andExpect(jsonPath("$.data.episode_number").value(3));

        mockMvc.perform(get("/api/movies").param("search", "Orbit").param("genre", "sci-fi"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value("plain-1"));
    }

    @Test
    void browseKeepsBinaryTieBreakingAndSupportsHugePages() throws Exception {
        movieRepository.saveAll(List.of(
                movie("a", "a", 2020, 8.0, "Drama", null),
                movie("B", "B", 2020, 8.0, "Drama", null),
                movie("é", "é", 2020, 8.0, "Drama", null),
                movie("Z", "Z", 2020, 8.0, "Drama", null)
        ));

        mockMvc.perform(get("/api/movies/browse")
                        .param("sort", "rating")
                        .param("direction", "desc")
                        .param("page", "1")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].id").value("a"))
                .andExpect(jsonPath("$.data.items[1].id").value("é"))
                .andExpect(jsonPath("$.data.totalElements").value(4))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andExpect(jsonPath("$.data.hasPrevious").value(true));

        mockMvc.perform(get("/api/movies/browse")
                        .param("sort", "year")
                        .param("page", "999999999999999999999")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0))
                .andExpect(jsonPath("$.data.totalElements").value(4))
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andExpect(jsonPath("$.data.hasPrevious").value(true));
    }

    @Test
    void browseFiltersAnimationVietnameseSearchTitleSortAndHiddenCatalogItems() throws Exception {
        movieRepository.saveAll(List.of(
                movie("a", "é", 2020, 8.0, " animation , Family ", "Một câu chuyện về bờ biển"),
                movie("B", "Z", 2020, 8.0, "Animation, Comedy", null),
                movie("é", "a", 2020, 8.0, "Animation, Family", "Một tuần ở BỜ BIỂN"),
                movie("Z", "Film", 2020, 10.0, "Animàtion, Family", null),
                movie("other", "Film 2", 2020, 10.0, "AnimationExtra, Family", null),
                movie("private-series", "Nháp bộ", 2020, 10.0, "Animation, Family", null),
                movie("private-season-ep", "Nháp mùa", 2020, 10.0, "Animation, Family", null),
                movie("private-ep", "Nháp tập", 2020, 10.0, "Animation, Family", null)
        ));
        seriesRepository.saveAll(List.of(
                series("public-series", "Bộ đã xuất bản", READY),
                series("private-series", "Bộ chưa xuất bản", "{}")
        ));
        seasonRepository.save(season("private-season", "public-series", "{}"));
        episodeRepository.saveAll(List.of(
                episode("private-season-ep", "public-series", "private-season", 1, READY),
                episode("private-ep", "public-series", null, 2, "{}")
        ));

        mockMvc.perform(get("/api/movies/browse")
                        .param("animationOnly", "true")
                        .param("page", "1")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.items[0].id").value("a"));

        mockMvc.perform(get("/api/movies/browse")
                        .param("animationOnly", "true")
                        .param("genres", "family, animation")
                        .param("sort", "title")
                        .param("direction", "asc")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.items[0].id").value("é"));

        mockMvc.perform(get("/api/movies/browse")
                        .param("animationOnly", "true")
                        .param("q", "bờ biển")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.items[0].id").value("a"));

        mockMvc.perform(get("/api/movies/browse")
                        .param("animationOnly", "true")
                        .param("q", "bo bien")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0))
                .andExpect(jsonPath("$.data.items.length()").value(0));
    }

    @Test
    void browseReturnsValidationErrorsWithExactMessages() throws Exception {
        mockMvc.perform(get("/api/movies/browse").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Chỉ số trang (page) không được âm: -1"));

        mockMvc.perform(get("/api/movies/browse").param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Kích thước trang (size) phải nằm trong khoảng 1 đến 100: 0"));

        mockMvc.perform(get("/api/movies/browse").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Kích thước trang (size) phải nằm trong khoảng 1 đến 100: 101"));

        mockMvc.perform(get("/api/movies/browse").param("yearMin", "2024").param("yearMax", "2020"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Năm tối thiểu (yearMin: 2024) không được lớn hơn năm tối đa (yearMax: 2020)"));

        mockMvc.perform(get("/api/movies/browse").param("sort", "views"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Trường sắp xếp không hợp lệ: 'views'"));

        mockMvc.perform(get("/api/movies/browse").param("direction", "sideways"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Chiều sắp xếp không hợp lệ: 'sideways'"));
    }

    @Test
    void mediaDescriptorSelectsAllowedAssetsAndBuildsLegacyFallbackTrailer() throws Exception {
        Movie orbit = new Movie(
                "the-last-orbit",
                "The Last Orbit",
                2024,
                8.8,
                "110 min",
                "Sci-Fi",
                "Space film",
                null,
                null,
                "https://cdn.example.com/orbit-trailer.mp4"
        );
        movieRepository.save(orbit);
        mediaAssetRepository.saveAll(List.of(
                new MediaAsset("ast-orbit-feat", "the-last-orbit", "feature", "local", null,
                        "orbit.mp4", "v1", "video/mp4", 2048L, 120.0, true, true),
                new MediaAsset("ast-orbit-private", "the-last-orbit", "trailer", "local", null,
                        "orbit-trailer.mp4", "v1", "video/mp4", 512L, 30.0, true, true)
        ));
        catalogMediaAssetRepository.save(new CatalogMediaAsset(
                "ast-orbit-private", "video", "trailer", null, null, null,
                "local", "main", "orbit-trailer.mp4", null, null, 512L, null, 30_000L,
                null, null, null, "private", true, READY, null, 1
        ));

        mockMvc.perform(get("/api/movies/the-last-orbit/media-descriptor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.movieId").value("the-last-orbit"))
                .andExpect(jsonPath("$.data.hasFeature").value(true))
                .andExpect(jsonPath("$.data.hasTrailer").value(true))
                .andExpect(jsonPath("$.data.feature.assetId").value("ast-orbit-feat"))
                .andExpect(jsonPath("$.data.feature.streamUrl").value("/api/media/stream/ast-orbit-feat"))
                .andExpect(jsonPath("$.data.trailer.assetId").value("fallback-trailer-the-last-orbit"))
                .andExpect(jsonPath("$.data.trailer.streamUrl").value("https://cdn.example.com/orbit-trailer.mp4"))
                .andExpect(jsonPath("$.data.trailer.version").value("legacy"))
                .andExpect(jsonPath("$.data.trailer.byteLength").value(0));

        mockMvc.perform(get("/api/movies/missing-movie/media-descriptor"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Không tìm thấy phim với mã: missing-movie"));
    }

    private static Movie movie(String id, String title, int year, double rating, String genres, String description) {
        return new Movie(id, title, year, rating, "90 min", genres, description, null, null, null);
    }

    private static CatalogSeries series(String id, String title, String recordJson) {
        return new CatalogSeries(id, title, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, recordJson, null, 1);
    }

    private static CatalogSeason season(String id, String seriesId, String recordJson) {
        return new CatalogSeason(id, seriesId, 1, "Season 1", null, "regular", null,
                null, null, null, recordJson, null, 1);
    }

    private static CatalogEpisode episode(String id, String seriesId, String seasonId, Integer number, String recordJson) {
        return new CatalogEpisode(id, seriesId, seasonId, number, "Episode " + number, null, null, null,
                "regular", null, 60_000L, null, null, null, null, null, null, null, null, null, null, null, null,
                recordJson, null, 1);
    }
}
