package com.dippie.ai;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.net.ServerSocket;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class ChatProxyUnavailableTest {

    private static int closedPort;

    private final MockMvc mockMvc;

    ChatProxyUnavailableTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @BeforeAll
    static void allocateClosedPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
    }

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("dippie.ai.base-url", () -> "http://127.0.0.1:" + closedPort);
        registry.add("dippie.ai.connect-timeout", () -> "PT1S");
        registry.add("dippie.ai.request-timeout", () -> "PT1S");
    }

    @Test
    void returns503EnvelopeWhenAiServiceIsDown() throws Exception {
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"hello\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Dịch vụ AI tạm thời không khả dụng. Vui lòng thử lại sau."))
                .andExpect(jsonPath("$.data").doesNotExist());

        mockMvc.perform(post("/v1/discover")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"hello\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Dịch vụ AI tạm thời không khả dụng. Vui lòng thử lại sau."));

        mockMvc.perform(post("/v1/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"hello\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Dịch vụ AI tạm thời không khả dụng. Vui lòng thử lại sau."));
    }
}
