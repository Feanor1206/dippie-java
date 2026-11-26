package com.dippie.controller.library;

import com.dippie.model.catalog.CatalogSeries;
import com.dippie.model.movie.Movie;
import com.dippie.model.user.Role;
import com.dippie.model.user.User;
import com.dippie.repository.catalog.CatalogSeriesRepository;
import com.dippie.repository.library.FavoriteItemRepository;
import com.dippie.repository.library.MovieRatingRepository;
import com.dippie.repository.library.WatchlistItemRepository;
import com.dippie.repository.movie.MovieRepository;
import com.dippie.repository.user.UserRepository;
import com.dippie.security.JwtService;
import com.dippie.service.catalog.CatalogReadModelCache;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Test tích hợp cho tủ phim cá nhân (xem sau, yêu thích, chấm điểm), gồm cả tình huống bấm thêm trùng lúc hai luồng chạy song song.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class LibraryControllerTest {

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final MovieRepository movieRepository;
    private final CatalogSeriesRepository seriesRepository;
    private final WatchlistItemRepository watchlistRepository;
    private final FavoriteItemRepository favoriteRepository;
    private final MovieRatingRepository ratingRepository;
    private final JwtService jwtService;
    private final CatalogReadModelCache cache;

    private String authHeader;

    LibraryControllerTest(
            MockMvc mockMvc,
            ObjectMapper objectMapper,
            UserRepository userRepository,
            MovieRepository movieRepository,
            CatalogSeriesRepository seriesRepository,
            WatchlistItemRepository watchlistRepository,
            FavoriteItemRepository favoriteRepository,
            MovieRatingRepository ratingRepository,
            JwtService jwtService,
            CatalogReadModelCache cache
    ) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
        this.movieRepository = movieRepository;
        this.seriesRepository = seriesRepository;
        this.watchlistRepository = watchlistRepository;
        this.favoriteRepository = favoriteRepository;
        this.ratingRepository = ratingRepository;
        this.jwtService = jwtService;
        this.cache = cache;
    }

    @BeforeEach
    void setUp() {
        watchlistRepository.deleteAll();
        favoriteRepository.deleteAll();
        ratingRepository.deleteAll();
        seriesRepository.deleteAll();
        movieRepository.deleteAll();
        userRepository.deleteAll();

        User user = userRepository.saveAndFlush(new User(
                "library_user",
                "library@example.com",
                "encoded",
                "Library User",
                Role.ROLE_USER,
                LocalDateTime.of(2026, 1, 1, 0, 0)
        ));
        authHeader = "Bearer " + jwtService.issueLoginToken(user.getUsername());

        movieRepository.saveAllAndFlush(List.of(
                new Movie("the-last-orbit", "The Last Orbit", 2024, 8.8, "110 min",
                        "Sci-Fi", "Orbit desc", "https://cdn/orbit.jpg", null, null),
                new Movie("spirited-away", "Spirited Away", 2001, 9.0, "125 min",
                        "Animation", "Chihiro", "https://cdn/spirited.jpg", null, null),
                new Movie("draft-series", "Draft Series", 2025, 9.5, "45 min",
                        "Drama", "Unpublished", null, null, null)
        ));
        seriesRepository.saveAndFlush(new CatalogSeries(
                "draft-series", "Draft Series", null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, "{\"status\":\"draft\"}", null, 1
        ));
        cache.invalidate();
    }

    @Test
    void rejectsRequestsWithoutValidLoginToken() throws Exception {
        String grantToken = jwtService.issuePlaybackGrant("ast-1", "library_user", Role.ROLE_USER);

        for (String endpoint : List.of("/api/users/watchlist", "/api/users/favorites", "/api/users/ratings")) {
            mockMvc.perform(get(endpoint))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("Bạn cần đăng nhập để tiếp tục."));

            mockMvc.perform(get(endpoint).header(HttpHeaders.AUTHORIZATION, "Bearer " + grantToken))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("Bạn cần đăng nhập để tiếp tục."));

            mockMvc.perform(post(endpoint)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"movieId\":\"the-last-orbit\",\"rating\":8}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("Bạn cần đăng nhập để tiếp tục."));

            mockMvc.perform(delete(endpoint + "/the-last-orbit"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("Bạn cần đăng nhập để tiếp tục."));
        }
    }

    @Test
    void supportsIdempotentWatchlistAndFavoritesCrudAndRejectsHiddenMovies() throws Exception {
        MvcResult firstAdd = mockMvc.perform(post("/api/users/watchlist")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"movieId\":\"the-last-orbit\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Đã thêm vào danh sách xem sau."))
                .andExpect(jsonPath("$.data.movieId").value("the-last-orbit"))
                .andExpect(jsonPath("$.data.movieTitle").value("The Last Orbit"))
                .andExpect(jsonPath("$.data.posterUrl").value("https://cdn/orbit.jpg"))
                .andReturn();

        MvcResult duplicateAdd = mockMvc.perform(post("/api/users/watchlist")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"movieId\":\"the-last-orbit\"}"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode firstData = objectMapper.readTree(firstAdd.getResponse().getContentAsString()).path("data");
        JsonNode duplicateData = objectMapper.readTree(duplicateAdd.getResponse().getContentAsString()).path("data");
        assertThat(duplicateData).isEqualTo(firstData);

        mockMvc.perform(get("/api/users/watchlist").header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));

        mockMvc.perform(delete("/api/users/watchlist/the-last-orbit")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Đã xóa khỏi danh sách xem sau."));

        mockMvc.perform(get("/api/users/watchlist").header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        mockMvc.perform(post("/api/users/favorites")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"movieId\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Bạn chưa chọn phim."));

        mockMvc.perform(post("/api/users/favorites")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"movieId\":\"draft-series\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Không tìm thấy phim: draft-series"));

        mockMvc.perform(post("/api/users/favorites")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"movieId\":\"spirited-away\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Đã thêm vào danh sách yêu thích."));

        mockMvc.perform(delete("/api/users/favorites/spirited-away")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Đã xóa khỏi danh sách yêu thích."));
    }

    @ParameterizedTest
    @ValueSource(strings = {"watchlist", "favorites"})
    void readsLiveMovieMetadataAfterMovieUpdate(String endpoint) throws Exception {
        String url = "/api/users/" + endpoint;
        MvcResult createdResult = mockMvc.perform(post(url)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"movieId\":\"the-last-orbit\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode created = objectMapper.readTree(createdResult.getResponse().getContentAsString()).path("data");

        movieRepository.saveAndFlush(new Movie(
                "the-last-orbit", "New title", 2024, 8.8, "110 min", "Sci-Fi", "Orbit desc", null, null, null
        ));

        MvcResult listed = mockMvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].movieTitle").value("New title"))
                .andExpect(jsonPath("$.data[0].posterUrl").isEmpty())
                .andReturn();
        JsonNode listedItem = objectMapper.readTree(listed.getResponse().getContentAsString()).path("data").get(0);
        assertThat(listedItem.path("id").longValue()).isEqualTo(created.path("id").longValue());
        assertThat(listedItem.path("addedAt").asText()).isEqualTo(created.path("addedAt").asText());

        MvcResult reAdded = mockMvc.perform(post(url)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"movieId\":\"the-last-orbit\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode reAddedItem = objectMapper.readTree(reAdded.getResponse().getContentAsString()).path("data");
        assertThat(reAddedItem).isEqualTo(listedItem);
    }

    @ParameterizedTest
    @ValueSource(strings = {"watchlist", "favorites", "ratings"})
    void handlesSimultaneousLibraryAdditionsWithSingleRow(String endpoint) throws Exception {
        String url = "/api/users/" + endpoint;
        String payload = objectMapper.writeValueAsString(Map.of("movieId", "the-last-orbit", "rating", 8));
        CyclicBarrier barrier = new CyclicBarrier(2);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Callable<Integer> task = () -> {
                barrier.await();
                return mockMvc.perform(post(url)
                                .header(HttpHeaders.AUTHORIZATION, authHeader)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload))
                        .andReturn()
                        .getResponse()
                        .getStatus();
            };
            List<Future<Integer>> futures = executor.invokeAll(List.of(task, task));
            assertThat(List.of(futures.get(0).get(), futures.get(1).get())).containsExactly(200, 200);
        }

        mockMvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void validatesRatingBoundsUpdatesExistingAndDeletes() throws Exception {
        for (String body : List.of(
                "{\"movieId\":\"the-last-orbit\",\"rating\":0}",
                "{\"movieId\":\"the-last-orbit\",\"rating\":11}",
                "{\"movieId\":\"the-last-orbit\"}",
                "{\"movieId\":\"\",\"rating\":8}"
        )) {
            mockMvc.perform(post("/api/users/ratings")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Điểm đánh giá cần từ 1 đến 10."));
        }

        mockMvc.perform(post("/api/users/ratings")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"movieId\":\"draft-series\",\"rating\":9}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Không tìm thấy phim: draft-series"));

        mockMvc.perform(post("/api/users/ratings")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"movieId\":\"the-last-orbit\",\"rating\":7}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Đã lưu đánh giá của bạn."))
                .andExpect(jsonPath("$.data.rating").value(7));

        mockMvc.perform(post("/api/users/ratings")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"movieId\":\"the-last-orbit\",\"rating\":10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rating").value(10));

        mockMvc.perform(get("/api/users/ratings").header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].rating").value(10));

        mockMvc.perform(delete("/api/users/ratings/the-last-orbit")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Đã xóa đánh giá của bạn."));

        mockMvc.perform(get("/api/users/ratings").header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }
}
