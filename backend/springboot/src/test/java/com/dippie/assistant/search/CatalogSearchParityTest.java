package com.dippie.assistant.search;

import com.dippie.catalog.domain.CatalogImportedEvent;
import com.dippie.catalog.importing.CatalogDocument;
import com.dippie.catalog.importing.CatalogImportService;
import com.dippie.security.JwtService;
import com.dippie.security.Role;
import com.dippie.user.User;
import com.dippie.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Bài test tích hợp kiểm tra API trạng thái trợ lý, nạp gói dữ liệu mẫu và đối chiếu kết quả xếp hạng trên /v1/search với /v1/discover.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class CatalogSearchParityTest {

    private final MockMvc mvc;
    private final ObjectMapper mapper;
    private final CatalogImportService imports;
    private final ApplicationEventPublisher events;
    private final UserRepository userRepository;
    private final JwtService jwtService;

    CatalogSearchParityTest(MockMvc mvc, ObjectMapper mapper, CatalogImportService imports,
            ApplicationEventPublisher events, UserRepository userRepository, JwtService jwtService) {
        this.mvc = mvc;
        this.mapper = mapper;
        this.imports = imports;
        this.events = events;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
    }

    @Test
    void matchesCatalogRankingsAfterImportAndReportsSnapshotReadiness() throws Exception {
        userRepository.deleteAll();
        User admin = userRepository.saveAndFlush(new User(
                "parity_admin", "parity.admin@dippie.com", "pw", "Admin",
                Role.ROLE_ADMIN, LocalDateTime.of(2026, 1, 1, 0, 0)
        ));
        String adminAuthHeader = "Bearer " + jwtService.issueLoginToken(admin.getUsername(), admin.getTokenVersion());

        // Khách chưa đăng nhập chỉ xem được cờ searchReady, không thấy llmProvider hay tổng số tài liệu nội bộ.
        mvc.perform(get("/api/assistant/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").doesNotExist())
                .andExpect(jsonPath("$.data.llmProvider").isEmpty())
                .andExpect(jsonPath("$.data.searchReady").value(true))
                .andExpect(jsonPath("$.data.documents").isEmpty());

        mvc.perform(get("/api/assistant/status").header(HttpHeaders.AUTHORIZATION, adminAuthHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.llmProvider").value("template"))
                .andExpect(jsonPath("$.data.searchReady").value(true))
                .andExpect(jsonPath("$.data.documents").value(0));

        events.publishEvent(new CatalogImportedEvent("unpersisted", 3, 100, "unpersisted", null,
                LocalDateTime.of(2026, 1, 1, 0, 0)));
        for (String name : List.of("jadoo", "larva")) {
            Path file = Path.of("../../data/catalog-by-series/catalog." + name + ".json");
            var result = imports.importPackage(new CatalogDocument(mapper.readTree(Files.readAllBytes(file))));
            assertThat(result.indexStatus()).isEqualTo("READY");
        }

        JsonNode fixture;
        try (var input = getClass().getResourceAsStream("/search-parity/jadoo-larva.json")) {
            fixture = mapper.readTree(input);
        }
        mvc.perform(get("/api/assistant/status").header(HttpHeaders.AUTHORIZATION, adminAuthHeader))
                .andExpect(jsonPath("$.data.searchReady").value(true))
                .andExpect(jsonPath("$.data.documents").value(fixture.path("documents").intValue()));

        for (JsonNode testCase : fixture.path("queries")) {
            String query = testCase.path("query").textValue();
            var request = mapper.createObjectNode().put("query", query);
            request.set("constraints", testCase.path("constraints"));
            String body = mvc.perform(post("/v1/search").contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsBytes(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.structuredIntent.semantic_query").value(query))
                    .andExpect(jsonPath("$.pipelineStages.retrieval").value("java-lexical"))
                    .andExpect(jsonPath("$.pipelineStages.indexReady").value(true))
                    .andReturn().getResponse().getContentAsString();

            var expected = testCase.path("results");
            var actual = mapper.readTree(body).path("results");
            assertThat(actual.size()).as(query).isEqualTo(expected.size());
            for (int i = 0; i < expected.size(); i++) {
                for (var fields = expected.get(i).fields(); fields.hasNext();) {
                    var field = fields.next();
                    assertThat(actual.get(i).path(field.getKey())).as("%s hit %s %s", query, i, field.getKey())
                            .isEqualTo(field.getValue());
                }
            }
        }

        mvc.perform(post("/v1/search").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"query":"Larva","limit":1,"constraints":{"series_only":true}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].item_type").value("series"))
                .andExpect(jsonPath("$.results[0].episode_count").value(104))
                .andExpect(jsonPath("$.results[0].season_count").value(1));

        mvc.perform(post("/v1/discover").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"query":"Larva tap 3","sessionId":null,"userId":"viewer","limit":1}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].title").value("Nhảy múa trong mưa"))
                .andExpect(jsonPath("$.followUps[0]").isString())
                .andExpect(jsonPath("$.explanation").value("Đã tìm trong kho phim hiện có."));
    }

    @Test
    void rejectsBlankAndInvalidDiscoverRequests() throws Exception {
        mvc.perform(post("/v1/search").contentType(MediaType.APPLICATION_JSON).content("{\"query\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Nội dung tìm kiếm đang bị bỏ trống."));
        mvc.perform(post("/v1/discover").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"Larva\",\"constraints\":{\"year_min\":\"2011\"}}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail[0].loc[2]").value("year_min"))
                .andExpect(jsonPath("$.detail[0].msg").value("Giá trị này phải là số nguyên."));
        mvc.perform(post("/v1/discover").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"Larva\",\"limit\":null,\"extra\":true}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail[0].type").value("int_type"))
                .andExpect(jsonPath("$.detail[1].type").value("extra_forbidden"));
    }
}
