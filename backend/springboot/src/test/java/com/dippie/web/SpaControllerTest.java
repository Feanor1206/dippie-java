package com.dippie.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class SpaControllerTest {

    @TempDir
    static Path tempDist;

    private final MockMvc mockMvc;

    SpaControllerTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("dippie.web.dist", () -> tempDist.toAbsolutePath().toString());
    }

    @BeforeEach
    void prepareDistFiles() throws IOException {
        Files.createDirectories(tempDist.resolve("assets"));
        Files.createDirectories(tempDist.resolve("illustrations"));
        Files.writeString(tempDist.resolve("index.html"), "<!doctype html><title>Dippie</title>", StandardCharsets.UTF_8);
        Files.writeString(tempDist.resolve("assets/app-abc123.js"), "export const v = 1;", StandardCharsets.UTF_8);
        Files.writeString(tempDist.resolve("illustrations/cover.svg"), "<svg/>", StandardCharsets.UTF_8);
    }

    @Test
    void servesIndexHtmlWithNoCacheForAllSpaRoutesAndHeadRequests() throws Exception {
        for (String route : new String[]{"/", "/search", "/profile", "/account", "/admin/import", "/admin/users", "/admin/stats",
                "/admin/audit", "/movies/m1", "/series", "/series/s1"}) {
            mockMvc.perform(get(route))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-cache"))
                    .andExpect(content().contentTypeCompatibleWith("text/html"))
                    .andExpect(content().string("<!doctype html><title>Dippie</title>"));

            mockMvc.perform(head(route))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-cache"));
        }
    }

    @Test
    void servesStaticAssetsWithLongCacheAndIllustrations() throws Exception {
        mockMvc.perform(get("/assets/app-abc123.js"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("max-age=31536000")))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("immutable")))
                .andExpect(content().string("export const v = 1;"));

        mockMvc.perform(get("/illustrations/cover.svg"))
                .andExpect(status().isOk())
                .andExpect(content().string("<svg/>"));

        mockMvc.perform(get("/assets/missing.js"))
                .andExpect(status().isNotFound());
    }

    @Test
    void returns503EnvelopeWhenIndexHtmlIsMissing() throws Exception {
        Files.deleteIfExists(tempDist.resolve("index.html"));

        mockMvc.perform(get("/"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Chưa build giao diện. Chạy npm run build trong thư mục frontend."))
                .andExpect(jsonPath("$.data").doesNotExist());

        mockMvc.perform(get("/movies/m1"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Chưa build giao diện. Chạy npm run build trong thư mục frontend."));
    }
}
