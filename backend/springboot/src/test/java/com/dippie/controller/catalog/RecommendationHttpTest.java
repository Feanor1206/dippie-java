package com.dippie.controller.catalog;

import com.dippie.model.user.Role;
import com.dippie.model.user.User;
import com.dippie.repository.user.UserRepository;
import com.dippie.security.JwtService;
import com.dippie.service.assistant.LocalIndexPublisher;
import com.dippie.service.catalog.CatalogIndexPublisher;
import com.dippie.service.catalog.CatalogReadModelCache;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Clock;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// test API gợi ý phim cho khách, user đã bấm thích với lọc đúng tập có file phát công khai
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
// tách context riêng cho DB với chỉ mục trống lúc mở màn, khỏi dính dữ liệu test khác
@TestPropertySource(properties = "dippie.test.context=RecommendationHttpTest")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class RecommendationHttpTest {

    private final MockMvc mvc;
    private final UserRepository users;
    private final JwtService jwt;
    private final PasswordEncoder passwords;
    private final Clock clock;
    private final JdbcTemplate database;
    private final CatalogIndexPublisher indexPublisher;
    private final EntityManagerFactory entityManagerFactory;
    private final CatalogReadModelCache cache;

    RecommendationHttpTest(MockMvc mvc, UserRepository users, JwtService jwt, PasswordEncoder passwords,
            Clock clock, JdbcTemplate database, CatalogIndexPublisher indexPublisher,
            EntityManagerFactory entityManagerFactory, CatalogReadModelCache cache) {
        this.mvc = mvc;
        this.users = users;
        this.jwt = jwt;
        this.passwords = passwords;
        this.clock = clock;
        this.database = database;
        this.indexPublisher = indexPublisher;
        this.entityManagerFactory = entityManagerFactory;
        this.cache = cache;
    }

    @Test
    void recommendsImportedEpisodesAnonymouslyAndPersonalizesForFavorites() throws Exception {
        assertThat(indexPublisher).isInstanceOf(LocalIndexPublisher.class);
        String admin = token("recommend-admin", Role.ROLE_ADMIN);
        submitCatalog(admin, CatalogHttpTest.jadooFixture()).andExpect(status().isOk())
                .andExpect(jsonPath("$.indexStatus").value("READY"));

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        boolean previouslyEnabled = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        long before = statistics.getPrepareStatementCount();
        try {
            verifyAnonymousRecommendations();
            assertThat(statistics.getPrepareStatementCount() - before).isEqualTo(1);
        } finally {
            statistics.setStatisticsEnabled(previouslyEnabled);
        }

        submitCatalog(admin, """
                {"package":{"id":"recommend-rival"},
                 "series":[{"id":"rival-series","title":"Rival"}],
                 "episodes":[{"id":"rival-episode","series_id":"rival-series","title":"Rival Episode","number":2}]}
                """).andExpect(status().isOk());
        database.update("update movies set rating = ? where id = ?", 10, "rival-episode");
        cache.invalidate();

        mvc.perform(post("/api/recommend").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendedMovies[0].id").value("rival-episode"))
                .andExpect(jsonPath("$.recommendedMovies[0].description").value("Tập 2: Rival Episode"));

        String member = token("recommend-viewer", Role.ROLE_USER);
        mvc.perform(post("/api/users/favorites").header(HttpHeaders.AUTHORIZATION, member)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"movieId":"ep_jadoo_s01_0001"}
                                """))
                .andExpect(status().isOk());
        mvc.perform(post("/api/recommend").header(HttpHeaders.AUTHORIZATION, member)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"limit":100}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reasoning").value("Phim gần gu với những gì bạn đã thích hoặc đã xem."))
                .andExpect(jsonPath("$.recommendedMovies[0].series_id").value("ser_jadoo"))
                .andExpect(jsonPath("$.recommendedMovies[0].score").value(greaterThanOrEqualTo(0.8)))
                .andExpect(jsonPath("$.recommendedMovies[*].id", not(hasItem("ep_jadoo_s01_0001"))))
                .andExpect(jsonPath("$.recommendedMovies[*].id", hasItem("rival-episode")));
    }

    private void verifyAnonymousRecommendations() throws Exception {
        mvc.perform(post("/api/recommend").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"genres\":[\"Animation\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendedMovies.length()").value(4))
                .andExpect(jsonPath("$.reasoning").value("Phim hợp với thể loại bạn đã chọn."))
                .andExpect(jsonPath("$.recommendedMovies[0].id").value("ep_jadoo_s01_0001"))
                .andExpect(jsonPath("$.recommendedMovies[0].episode_id").value("ep_jadoo_s01_0001"))
                .andExpect(jsonPath("$.recommendedMovies[0].episodeId").value("ep_jadoo_s01_0001"))
                .andExpect(jsonPath("$.recommendedMovies[0].series_id").value("ser_jadoo"))
                .andExpect(jsonPath("$.recommendedMovies[0].item_type").value("episode"))
                .andExpect(jsonPath("$.recommendedMovies[0].primary_media_asset_id").value(nullValue()))
                .andExpect(jsonPath("$.recommendedMovies[0].calibratedScore").value(nullValue()))
                .andExpect(jsonPath("$.recommendedMovies[0].evidence[0].itemType").value("episode"));

    }

    @Test
    void excludesHiddenEpisodesAndRequiresPublicFullVideoForDeclaredCoverage() throws Exception {
        String admin = token("recommend-coverage", Role.ROLE_ADMIN);
        submitCatalog(admin, """
                {"package":{"id":"recommend-coverage"},
                 "series":[{"id":"coverage-series"},{"id":"hidden-series","record":{"status":"draft"}}],
                 "seasons":[{"id":"hidden-season","series_id":"coverage-series","record":{"status":"draft"}}],
                 "episodes":[
                   {"id":"coverage-undeclared","series_id":"coverage-series"},
                   {"id":"coverage-playable","series_id":"coverage-series"},
                   {"id":"coverage-private","series_id":"coverage-series"},
                   {"id":"coverage-partial","series_id":"coverage-series"},
                   {"id":"coverage-disabled","series_id":"coverage-series"},
                   {"id":"coverage-draft","series_id":"coverage-series"},
                   {"id":"coverage-dub","series_id":"coverage-series"},
                   {"id":"coverage-hidden-season","series_id":"coverage-series","season_id":"hidden-season"},
                   {"id":"coverage-hidden-series","series_id":"hidden-series"}],
                 "media_assets":[
                   {"id":"playable","storage":{"relative_path":"a.mp4"},
                    "coverage":[{"episode_id":"coverage-playable","scope":"full_episode"}]},
                   {"id":"private","access_policy":"private","storage":{"relative_path":"b.mp4"},
                    "coverage":[{"episode_id":"coverage-private","scope":"full_episode"}]},
                   {"id":"partial","storage":{"relative_path":"c.mp4"},
                    "coverage":[{"episode_id":"coverage-partial","scope":"partial_episode"}]},
                   {"id":"disabled","enabled":false,"storage":{"relative_path":"d.mp4"},
                    "coverage":[{"episode_id":"coverage-disabled","scope":"full_episode"}]},
                   {"id":"draft","record":{"status":"draft"},"storage":{"relative_path":"e.mp4"},
                    "coverage":[{"episode_id":"coverage-draft","scope":"full_episode"}]},
                   {"id":"dub","role":"dub","storage":{"relative_path":"f.mp4"},
                    "coverage":[{"episode_id":"coverage-dub","scope":"full_episode"}]}]}
                """).andExpect(status().isOk());

        var response = mvc.perform(post("/api/recommend").header(HttpHeaders.AUTHORIZATION, admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"limit\":100}")).andExpect(status().isOk());
        response.andExpect(jsonPath("$.recommendedMovies[?(@.series_id == 'coverage-series')].id",
                contains("coverage-playable", "coverage-undeclared")));
        response.andExpect(jsonPath("$.recommendedMovies[*].id", not(hasItem("coverage-hidden-series"))));
    }

    @Test
    void rejectsInvalidLimitWithVietnameseEnvelope() throws Exception {
        mvc.perform(post("/api/recommend").contentType(MediaType.APPLICATION_JSON).content("{\"limit\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Số lượng gợi ý phải từ 1 đến 100."));
        mvc.perform(post("/api/recommend").contentType(MediaType.APPLICATION_JSON).content("{\"limit\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    private String token(String username, Role role) {
        users.save(new User(username, username + "@example.com", passwords.encode("test-password"),
                username, role, LocalDateTime.now(clock)));
        return "Bearer " + jwt.issueLoginToken(username);
    }

    private ResultActions submitCatalog(String token, String body) throws Exception {
        return mvc.perform(post("/api/admin/catalog/import").header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
