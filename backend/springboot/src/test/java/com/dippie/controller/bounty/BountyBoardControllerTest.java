package com.dippie.controller.bounty;

import com.dippie.repository.bounty.BountySubmissionRepository;
import com.dippie.repository.bounty.BountyTaskProfileRepository;
import com.dippie.repository.bounty.BountyTaskRepository;
import com.dippie.repository.profile.ChildProfileRepository;
import com.dippie.repository.profile.ScreenTimeLedgerRepository;
import com.dippie.repository.user.UserRepository;
import com.dippie.security.LoginRateLimiter;
import com.dippie.service.profile.ParentPinRateLimiter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// test bảng nhiệm vụ đời thực: CRUD có PIN, phân quyền từng bé, giới hạn 1 lượt chờ duyệt, duyệt từ cả hồ sơ chính lẫn kid mode và cộng BOUNTY
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(BountyBoardControllerTest.ClockOverrideConfig.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class BountyBoardControllerTest {

    @TestConfiguration
    static class ClockOverrideConfig {

        @Bean
        @Primary
        MutableClock bountyMutableClock() {
            return new MutableClock(Instant.parse("2026-11-26T10:00:00Z"));
        }
    }

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final MutableClock clock;
    private final UserRepository userRepository;
    private final ChildProfileRepository childProfileRepository;
    private final ScreenTimeLedgerRepository ledgerRepository;
    private final BountyTaskRepository taskRepository;
    private final BountyTaskProfileRepository taskProfileRepository;
    private final BountySubmissionRepository submissionRepository;
    private final LoginRateLimiter loginRateLimiter;
    private final ParentPinRateLimiter parentPinRateLimiter;

    BountyBoardControllerTest(
            MockMvc mockMvc,
            ObjectMapper objectMapper,
            MutableClock clock,
            UserRepository userRepository,
            ChildProfileRepository childProfileRepository,
            ScreenTimeLedgerRepository ledgerRepository,
            BountyTaskRepository taskRepository,
            BountyTaskProfileRepository taskProfileRepository,
            BountySubmissionRepository submissionRepository,
            LoginRateLimiter loginRateLimiter,
            ParentPinRateLimiter parentPinRateLimiter
    ) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.userRepository = userRepository;
        this.childProfileRepository = childProfileRepository;
        this.ledgerRepository = ledgerRepository;
        this.taskRepository = taskRepository;
        this.taskProfileRepository = taskProfileRepository;
        this.submissionRepository = submissionRepository;
        this.loginRateLimiter = loginRateLimiter;
        this.parentPinRateLimiter = parentPinRateLimiter;
    }

    @BeforeEach
    void setUp() {
        clock.set(Instant.parse("2026-11-26T10:00:00Z"));
        loginRateLimiter.reset();
        parentPinRateLimiter.reset();
        submissionRepository.deleteAll();
        taskProfileRepository.deleteAll();
        taskRepository.deleteAll();
        ledgerRepository.deleteAll();
        childProfileRepository.deleteAll();
        userRepository.deleteAll();
    }

    // test trọn vòng lặp tạo nhiệm vụ -> bé nộp -> chặn nộp trùng -> ba mẹ duyệt cộng BOUNTY -> phân biệt lặp hàng ngày với làm một lần
    @Test
    void enforcesTaskScopingSinglePendingSubmissionPinApprovalAndRepeatableRules() throws Exception {
        String mainToken = registerUser("parentb", "parentb@example.com", "secret123", "Bố Nam");
        enableParentMode(mainToken, "1357");
        long child1Id = createChildProfile(mainToken, "1357", "Bé Bo", "bear", 0);
        long child2Id = createChildProfile(mainToken, "1357", "Bé Bông", "bunny", 60);

        String child1Token = switchToChild(mainToken, child1Id);
        String child2Token = switchToChild(mainToken, child2Id);

        // kiểm tra validate: phần thưởng ngoài 1..120 bị 400, kid mode không được gọi API quản lý (403), hồ sơ chính gọi /kid bị 400
        mockMvc.perform(post("/api/users/bounties/tasks")
                        .header(HttpHeaders.AUTHORIZATION, bearer(mainToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "pin", "1357",
                                "title", "Quét nhà",
                                "rewardMinutes", 121,
                                "profileIds", List.of(child1Id)
                        ))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/users/bounties").header(HttpHeaders.AUTHORIZATION, bearer(child1Token)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/users/bounties/kid").header(HttpHeaders.AUTHORIZATION, bearer(mainToken)))
                .andExpect(status().isBadRequest());

        // tạo 1 nhiệm vụ lặp mỗi ngày chỉ cho bé 1 và 1 nhiệm vụ làm 1 lần cho cả hai bé
        long dailyTaskId = createTask(mainToken, "1357", "Dọn đồ chơi gọn gàng", 15, "toys", true, true, List.of(child1Id));
        long oneOffTaskId = createTask(mainToken, "1357", "Đọc hết 1 cuốn truyện tranh", 20, "book", true, false, List.of(child1Id, child2Id));

        // bé 2 chỉ thấy nhiệm vụ one-off và không thể nộp nhiệm vụ riêng của bé 1
        mockMvc.perform(get("/api/users/bounties/kid").header(HttpHeaders.AUTHORIZATION, bearer(child2Token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tasks.length()").value(1))
                .andExpect(jsonPath("$.data.tasks[0].id").value(oneOffTaskId));

        mockMvc.perform(post("/api/users/bounties/tasks/" + dailyTaskId + "/submit")
                        .header(HttpHeaders.AUTHORIZATION, bearer(child2Token)))
                .andExpect(status().isNotFound());

        // bé 1 đang bị khóa ở 0 phút -> nộp nhiệm vụ daily -> trạng thái PENDING
        MvcResult submitRes = mockMvc.perform(post("/api/users/bounties/tasks/" + dailyTaskId + "/submit")
                        .header(HttpHeaders.AUTHORIZATION, bearer(child1Token)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn();
        long submissionId = objectMapper.readTree(submitRes.getResponse().getContentAsString()).path("data").path("id").asLong();

        // không được nộp trùng khi đang có lượt PENDING
        mockMvc.perform(post("/api/users/bounties/tasks/" + dailyTaskId + "/submit")
                        .header(HttpHeaders.AUTHORIZATION, bearer(child1Token)))
                .andExpect(status().isConflict());

        // ba mẹ từ chối thì không cộng phút và bé được nộp lại
        mockMvc.perform(post("/api/users/bounties/submissions/" + submissionId + "/reject")
                        .header(HttpHeaders.AUTHORIZATION, bearer(mainToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("pin", "1357", "note", "Cất thêm hộp bút nhé"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.submission.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.screenTime.bountySeconds").value(0))
                .andExpect(jsonPath("$.data.screenTime.locked").value(true));

        MvcResult resubmitRes = mockMvc.perform(post("/api/users/bounties/tasks/" + dailyTaskId + "/submit")
                        .header(HttpHeaders.AUTHORIZATION, bearer(child1Token)))
                .andExpect(status().isCreated())
                .andReturn();
        long submission2Id = objectMapper.readTree(resubmitRes.getResponse().getContentAsString()).path("data").path("id").asLong();

        // ba mẹ duyệt ngay trên máy trong kid mode bằng đúng mã PIN -> cộng 15 phút BOUNTY và mở khóa bé 1 ngay lập tức
        mockMvc.perform(post("/api/users/bounties/submissions/" + submission2Id + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, bearer(child1Token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("pin", "1357"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.submission.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.screenTime.bountySeconds").value(900))
                .andExpect(jsonPath("$.data.screenTime.remainingSeconds").value(900))
                .andExpect(jsonPath("$.data.screenTime.locked").value(false));

        // nhiệm vụ lặp hàng ngày: hôm nay đã duyệt nên chặn 409, qua 0h VN hôm sau lại nộp được
        mockMvc.perform(post("/api/users/bounties/tasks/" + dailyTaskId + "/submit")
                        .header(HttpHeaders.AUTHORIZATION, bearer(child1Token)))
                .andExpect(status().isConflict());

        // nộp và duyệt nhiệm vụ làm một lần (one-off) từ hồ sơ chính
        MvcResult oneOffSubmit = mockMvc.perform(post("/api/users/bounties/tasks/" + oneOffTaskId + "/submit")
                        .header(HttpHeaders.AUTHORIZATION, bearer(child1Token)))
                .andExpect(status().isCreated())
                .andReturn();
        long oneOffSubId = objectMapper.readTree(oneOffSubmit.getResponse().getContentAsString()).path("data").path("id").asLong();

        mockMvc.perform(post("/api/users/bounties/submissions/" + oneOffSubId + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, bearer(mainToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("pin", "1357"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.screenTime.bountySeconds").value(2100))
                .andExpect(jsonPath("$.data.screenTime.remainingSeconds").value(2100));

        // tua qua ngày hôm sau theo giờ Việt Nam (2026-11-27T01:00+07:00 = 2026-11-26T18:00:00Z)
        clock.set(Instant.parse("2026-11-26T18:00:00Z"));
        mockMvc.perform(post("/api/users/bounties/tasks/" + dailyTaskId + "/submit")
                        .header(HttpHeaders.AUTHORIZATION, bearer(child1Token)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/users/bounties/tasks/" + oneOffTaskId + "/submit")
                        .header(HttpHeaders.AUTHORIZATION, bearer(child1Token)))
                .andExpect(status().isConflict());

        // phụ huynh sửa và xóa nhiệm vụ cần đúng PIN
        mockMvc.perform(put("/api/users/bounties/tasks/" + dailyTaskId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(mainToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "pin", "1357",
                                "title", "Dọn bàn học",
                                "rewardMinutes", 25,
                                "icon", "book",
                                "active", false,
                                "repeatableDaily", true,
                                "profileIds", List.of(child1Id)
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Dọn bàn học"))
                .andExpect(jsonPath("$.data.active").value(false));

        mockMvc.perform(delete("/api/users/bounties/tasks/" + oneOffTaskId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(mainToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("pin", "1357"))))
                .andExpect(status().isNoContent());

        assertThat(taskRepository.findById(oneOffTaskId)).isEmpty();
    }

    // test nhập sai PIN khi duyệt trong kid mode bị khóa rate limit 429 sau 5 lần sai
    @Test
    void rateLimitsWrongParentPinWhenApprovingFromKidMode() throws Exception {
        String mainToken = registerUser("parentpin", "parentpin@example.com", "secret123", "Mẹ Hà");
        enableParentMode(mainToken, "2468");
        long childId = createChildProfile(mainToken, "2468", "Bé Tí", "fox", 60);
        String childToken = switchToChild(mainToken, childId);

        long taskId = createTask(mainToken, "2468", "Đánh răng đúng giờ", 10, "teeth", true, true, List.of(childId));
        MvcResult submitRes = mockMvc.perform(post("/api/users/bounties/tasks/" + taskId + "/submit")
                        .header(HttpHeaders.AUTHORIZATION, bearer(childToken)))
                .andExpect(status().isCreated())
                .andReturn();
        long subId = objectMapper.readTree(submitRes.getResponse().getContentAsString()).path("data").path("id").asLong();

        for (int attempt = 0; attempt < 5; attempt++) {
            mockMvc.perform(post("/api/users/bounties/submissions/" + subId + "/approve")
                            .header(HttpHeaders.AUTHORIZATION, bearer(childToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("pin", "0000"))))
                    .andExpect(status().isBadRequest());
            clock.advance(Duration.ofSeconds(1));
        }

        mockMvc.perform(post("/api/users/bounties/submissions/" + subId + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, bearer(childToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("pin", "2468"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
    }

    private long createTask(
            String token,
            String pin,
            String title,
            int rewardMinutes,
            String icon,
            boolean active,
            boolean repeatableDaily,
            List<Long> profileIds
    ) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/users/bounties/tasks")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "pin", pin,
                                "title", title,
                                "rewardMinutes", rewardMinutes,
                                "icon", icon,
                                "active", active,
                                "repeatableDaily", repeatableDaily,
                                "profileIds", profileIds
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode data = objectMapper.readTree(res.getResponse().getContentAsString()).path("data");
        return data.path("id").asLong();
    }

    private String registerUser(String username, String email, String password, String fullName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "username", username,
                                "email", email,
                                "password", password,
                                "fullName", fullName,
                                "acceptedTerms", true
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("token").asText();
    }

    private void enableParentMode(String token, String pin) throws Exception {
        mockMvc.perform(post("/api/users/parent-mode/enable")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("pin", pin))))
                .andExpect(status().isOk());
    }

    private long createChildProfile(String token, String pin, String name, String avatarKey, int dailyMinutes) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/users/profiles")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("pin", pin, "name", name, "avatarKey", avatarKey, "dailyMinutes", dailyMinutes))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    private String switchToChild(String token, long profileId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/users/profiles/" + profileId + "/switch")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("token").asText();
    }

    private String json(Object payload) throws Exception {
        return objectMapper.writeValueAsString(payload);
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    static final class MutableClock extends Clock {

        private Instant current;

        MutableClock(Instant initial) {
            this.current = initial;
        }

        void set(Instant instant) {
            this.current = instant;
        }

        void advance(Duration duration) {
            this.current = this.current.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}
