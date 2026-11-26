package com.dippie.service.user;

import com.dippie.model.catalog.CatalogDocument;
import com.dippie.security.JwtService;
import com.dippie.service.catalog.CatalogImportService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Chạy thử DemoHistorySeeder sau khi import danh mục phim và kiểm tra cơ chế không ghi đè dữ liệu người xem đã sửa.
@SpringBootTest(properties = "dippie.seed-demo-data=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
// Lớp này cần CSDL và chỉ mục tìm kiếm trống lúc bắt đầu, nên dùng context riêng để không dính dữ liệu của test chạy trước.
@TestPropertySource(properties = "dippie.test.context=DemoHistorySeederTest")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class DemoHistorySeederTest {

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final CatalogImportService catalogImportService;
    private final JwtService jwtService;
    private final ApplicationContext applicationContext;

    DemoHistorySeederTest(
            MockMvc mockMvc,
            ObjectMapper objectMapper,
            CatalogImportService catalogImportService,
            JwtService jwtService,
            ApplicationContext applicationContext
    ) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.catalogImportService = catalogImportService;
        this.jwtService = jwtService;
        this.applicationContext = applicationContext;
    }

    // Import dần từng bộ phim để kích hoạt sự kiện nạp mẫu, sau đó chỉnh sửa tiến độ/điểm rồi chạy lại seeder để chắc chắn không bị ghi đè.
    @Test
    void seedsAfterCatalogImportsAndKeepsExistingViewerChangesOnRerun() throws Exception {
        String userBearer = "Bearer " + jwtService.issueLoginToken("user");
        assertThat(library(userBearer, "watch-progress")).isEmpty();
        assertThat(library(userBearer, "watchlist")).isEmpty();

        importSeries("peppa_pig");
        assertThat(library(userBearer, "watch-progress").valueStream().map(row -> row.path("episodeId").asText()))
                .containsExactly("ep_peppa_pig_s01_0001");
        assertThat(library(userBearer, "watchlist")).isEmpty();

        for (String series : List.of("jadoo", "ori", "doraemon", "larva", "trang_quynh")) {
            importSeries(series);
        }

        var progress = library(userBearer, "watch-progress").valueStream().toList();
        assertThat(progress).extracting(row -> row.path("seriesId").asText())
                .containsExactly("ser_peppa_pig", "ser_doraemon", "ser_larva");
        assertThat(progress).extracting(row -> row.path("assetId").asText())
                .containsExactly("youtube:WdvGYRaRHUs", "youtube:0FHNLZszGiU", "youtube:Z3D9Wr2AYeA");
        assertThat(progress).extracting(row -> row.path("positionMs").asLong()).containsExactly(92_000L, 360_000L, 138_000L);
        assertThat(progress).extracting(row -> row.path("completed").asBoolean()).containsExactly(false, false, true);
        assertThat(library(userBearer, "watchlist")).hasSize(3);
        assertThat(library(userBearer, "favorites")).hasSize(2);
        assertThat(library(userBearer, "ratings")).hasSize(3);
        for (String collection : List.of("watch-progress", "watchlist", "favorites", "ratings")) {
            assertThat(library("Bearer " + jwtService.issueLoginToken("admin"), collection)).isEmpty();
        }

        mockMvc.perform(put("/api/users/watch-progress/ep_peppa_pig_s01_0001").header(HttpHeaders.AUTHORIZATION, userBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assetId\":\"youtube:WdvGYRaRHUs\",\"positionMs\":100000,\"expectedVersion\":0}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/users/ratings").header(HttpHeaders.AUTHORIZATION, userBearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"movieId\":\"ep_peppa_pig_s01_0001\",\"rating\":10}"))
                .andExpect(status().isOk());

        applicationContext.getBean("demoHistorySeeder", ApplicationRunner.class).run(new DefaultApplicationArguments());
        assertThat(library(userBearer, "watch-progress").valueStream().map(row -> row.path("positionMs").asLong()))
                .containsExactlyInAnyOrder(100_000L, 360_000L, 138_000L);
        assertThat(library(userBearer, "ratings").valueStream().map(row -> row.path("rating").asInt()))
                .containsExactlyInAnyOrder(10, 9, 7);
        assertThat(library(userBearer, "watchlist")).hasSize(3);
        assertThat(library(userBearer, "favorites")).hasSize(2);
    }

    // Kể cả khi bật dippie.seed-demo-data=true, hễ có profile mysql thì cả hai seeder demo không được tạo bean.
    @Test
    void mysqlProfileDisallowsSeedingEvenWhenItIsEnabled() {
        new ApplicationContextRunner().withUserConfiguration(DemoAccountSeeder.class, DemoHistorySeeder.class)
                .withInitializer(application -> application.getEnvironment().setActiveProfiles("mysql", "test"))
                .withPropertyValues("dippie.seed-demo-data=true")
                .run(application -> assertThat(application).doesNotHaveBean(DemoAccountSeeder.class)
                        .doesNotHaveBean(DemoHistorySeeder.class));
    }

    // Chạy profile test mặc định mà không bật cờ dippie.seed-demo-data thì seeder phải tắt.
    @Test
    void testProfileRequiresExplicitOptInForSeeding() {
        new ApplicationContextRunner().withUserConfiguration(DemoAccountSeeder.class, DemoHistorySeeder.class)
                .withInitializer(application -> application.getEnvironment().setActiveProfiles("test"))
                .run(application -> assertThat(application).doesNotHaveBean(DemoAccountSeeder.class)
                        .doesNotHaveBean(DemoHistorySeeder.class));
    }

    private void importSeries(String series) throws Exception {
        var catalogFile = Path.of("../../data/catalog-by-series/catalog." + series + ".json");
        assertThat(catalogImportService.importPackage(new CatalogDocument(objectMapper.readTree(Files.readAllBytes(catalogFile))))
                .indexStatus()).isEqualTo("READY");
    }

    private JsonNode library(String authorizationHeader, String collection) throws Exception {
        var response = mockMvc.perform(get("/api/users/" + collection).header(HttpHeaders.AUTHORIZATION, authorizationHeader))
                .andExpect(status().isOk()).andReturn().getResponse();
        return objectMapper.readTree(response.getContentAsByteArray()).path("data");
    }
}
