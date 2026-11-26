package com.dippie.controller.quiz;

import com.dippie.model.quiz.QuizChallengeContext;
import com.dippie.model.quiz.QuizGrade;
import com.dippie.model.quiz.QuizOptionKey;
import com.dippie.model.quiz.QuizQuestion;
import com.dippie.model.quiz.QuizSet;
import com.dippie.model.quiz.QuizSubject;
import com.dippie.repository.profile.ChildProfileRepository;
import com.dippie.repository.profile.ScreenTimeLedgerRepository;
import com.dippie.repository.quiz.ChildQuizSetRepository;
import com.dippie.repository.quiz.QuizAttemptRepository;
import com.dippie.repository.quiz.QuizQuestionRepository;
import com.dippie.repository.quiz.QuizSetRepository;
import com.dippie.repository.user.UserRepository;
import com.dippie.security.LoginRateLimiter;
import com.dippie.service.profile.ParentPinRateLimiter;
import com.dippie.service.quiz.BuiltInQuizSeeder;
import com.dippie.service.quiz.QuizCsvParser;
import com.dippie.service.quiz.QuizRateLimiter;
import com.dippie.service.quiz.QuizSelectionService;
import com.dippie.service.quiz.QuizTokenCodec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// test đầy đủ module quiz: kho sẵn có, quyền riêng tư bộ câu hỏi, giới hạn 20 bộ/500 câu/1 MB, CSV, không lặp 30 câu, trộn đáp án, báo cáo tuần và đổi lớp xác nhận PIN
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class QuizBankControllerTest {

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final ChildProfileRepository childProfileRepository;
    private final ScreenTimeLedgerRepository ledgerRepository;
    private final QuizSetRepository quizSetRepository;
    private final QuizQuestionRepository quizQuestionRepository;
    private final ChildQuizSetRepository childQuizSetRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final BuiltInQuizSeeder builtInQuizSeeder;
    private final QuizTokenCodec quizTokenCodec;
    private final QuizRateLimiter quizRateLimiter;
    private final QuizSelectionService quizSelectionService;
    private final LoginRateLimiter loginRateLimiter;
    private final ParentPinRateLimiter parentPinRateLimiter;

    QuizBankControllerTest(
            MockMvc mockMvc,
            ObjectMapper objectMapper,
            UserRepository userRepository,
            ChildProfileRepository childProfileRepository,
            ScreenTimeLedgerRepository ledgerRepository,
            QuizSetRepository quizSetRepository,
            QuizQuestionRepository quizQuestionRepository,
            ChildQuizSetRepository childQuizSetRepository,
            QuizAttemptRepository quizAttemptRepository,
            BuiltInQuizSeeder builtInQuizSeeder,
            QuizTokenCodec quizTokenCodec,
            QuizRateLimiter quizRateLimiter,
            QuizSelectionService quizSelectionService,
            LoginRateLimiter loginRateLimiter,
            ParentPinRateLimiter parentPinRateLimiter
    ) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
        this.childProfileRepository = childProfileRepository;
        this.ledgerRepository = ledgerRepository;
        this.quizSetRepository = quizSetRepository;
        this.quizQuestionRepository = quizQuestionRepository;
        this.childQuizSetRepository = childQuizSetRepository;
        this.quizAttemptRepository = quizAttemptRepository;
        this.builtInQuizSeeder = builtInQuizSeeder;
        this.quizTokenCodec = quizTokenCodec;
        this.quizRateLimiter = quizRateLimiter;
        this.quizSelectionService = quizSelectionService;
        this.loginRateLimiter = loginRateLimiter;
        this.parentPinRateLimiter = parentPinRateLimiter;
    }

    @BeforeEach
    void setUp() {
        loginRateLimiter.reset();
        parentPinRateLimiter.reset();
        quizRateLimiter.reset();
        quizTokenCodec.resetUsedTokens();
        quizSelectionService.resetRecentIssued();
        quizAttemptRepository.deleteAll();
        childQuizSetRepository.deleteAll();
        // chỉ xóa các bộ tự tạo của phụ huynh, giữ nguyên 3 bộ sẵn có rồi bảo đảm seed đủ
        List<QuizSet> customSets = quizSetRepository.findAll().stream()
                .filter(set -> !set.isBuiltIn())
                .toList();
        for (QuizSet custom : customSets) {
            quizQuestionRepository.deleteAll(quizQuestionRepository.findBySetIdOrderByIdDesc(custom.getId()));
        }
        quizSetRepository.deleteAll(customSets);
        ledgerRepository.deleteAll();
        childProfileRepository.deleteAll();
        userRepository.deleteAll();
        builtInQuizSeeder.ensureSeeded();
    }

    @Test
    void enforcesOwnershipPrivacyBuiltInReadOnlyAndCrudPlusCsvImportFlow() throws Exception {
        String parentA = registerUser("quizmoma", "quizmoma@example.com", "secret123", "Mẹ An");
        String parentB = registerUser("quizmomb", "quizmomb@example.com", "secret123", "Mẹ Bình");
        enableParentMode(parentA, "1234");
        enableParentMode(parentB, "5678");

        // kho có sẵn gồm 3 bộ (Toán động + 30 Tiếng Anh + 30 Đố vui & Khoa học)
        MvcResult overviewRes = mockMvc.perform(get("/api/users/quiz/sets")
                        .header(HttpHeaders.AUTHORIZATION, bearer(parentA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sets.length()").value(3))
                .andExpect(jsonPath("$.data.maxSetsPerAccount").value(20))
                .andExpect(jsonPath("$.data.maxQuestionsPerSet").value(500))
                .andReturn();
        JsonNode setsNode = objectMapper.readTree(overviewRes.getResponse().getContentAsString()).path("data").path("sets");
        long builtInMathId = -1;
        long builtInEnglishId = -1;
        long builtInScienceId = -1;
        for (JsonNode s : setsNode) {
            if (s.path("dynamicGenerator").asBoolean()) {
                builtInMathId = s.path("id").asLong();
            } else if ("TIENG_ANH".equals(s.path("defaultSubject").asText())) {
                builtInEnglishId = s.path("id").asLong();
                assertThat(s.path("questionCount").asInt()).isEqualTo(30);
            } else {
                builtInScienceId = s.path("id").asLong();
                assertThat(s.path("questionCount").asInt()).isEqualTo(30);
            }
        }
        assertThat(builtInMathId).isPositive();
        assertThat(builtInEnglishId).isPositive();
        assertThat(builtInScienceId).isPositive();

        // không sửa hay xóa được bộ có sẵn
        mockMvc.perform(delete("/api/users/quiz/sets/" + builtInEnglishId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(parentA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("pin", "1234"))))
                .andExpect(status().isBadRequest());

        // mẹ A tạo bộ riêng, thêm/sửa/tắt/xóa câu hỏi bằng tay
        long setId = createCustomSet(parentA, "1234", "Ôn tập tuần 5", "TIENG_VIET", "LOP_3");

        MvcResult qCreate = mockMvc.perform(post("/api/users/quiz/sets/" + setId + "/questions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(parentA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "pin", "1234",
                                "questionText", "Từ nào viết đúng chính tả?",
                                "optionA", "Sạch sẽ",
                                "optionB", "Xạch xẽ",
                                "optionC", "Sạch xẽ",
                                "optionD", "Xạch sẽ",
                                "correctOption", "A",
                                "explanation", "Sạch sẽ dùng chữ s",
                                "subject", "TIENG_VIET",
                                "grade", "LOP_3"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.correctOption").value("A"))
                .andReturn();
        long questionId = objectMapper.readTree(qCreate.getResponse().getContentAsString()).path("data").path("id").asLong();

        // sửa và tạm tắt câu hỏi
        mockMvc.perform(put("/api/users/quiz/sets/" + setId + "/questions/" + questionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(parentA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("pin", "1234", "active", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false));

        // mẹ B hoàn toàn không xem, sửa, xóa, nhập CSV hay gán bộ riêng của mẹ A được (404)
        mockMvc.perform(get("/api/users/quiz/sets/" + setId + "/questions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(parentB)))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/users/quiz/sets/" + setId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(parentB))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("pin", "5678", "name", "Chiếm bộ"))))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/users/quiz/sets/" + setId + "/csv/preview")
                        .header(HttpHeaders.AUTHORIZATION, bearer(parentB))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("csvContent", QuizCsvParser.TEMPLATE_CSV))))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/users/quiz/sets/" + setId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(parentB))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("pin", "5678"))))
                .andExpect(status().isNotFound());

        // tải tệp mẫu CSV có BOM UTF-8 và 3 dòng ví dụ
        MvcResult templateRes = mockMvc.perform(get("/api/users/quiz/csv-template")
                        .header(HttpHeaders.AUTHORIZATION, bearer(parentA)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andReturn();
        String templateBody = templateRes.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(templateBody).startsWith(QuizCsvParser.UTF8_BOM);
        assertThat(templateBody).contains("cau_hoi,a,b,c,d,dap_an,giai_thich,mon,lop");

        // xem trước CSV có cả dòng đúng và dòng lỗi, rồi nhập chỉ những dòng hợp lệ
        String mixedCsv = QuizCsvParser.UTF8_BOM
                + "cau_hoi,a,b,c,d,dap_an,giai_thich,mon,lop\n"
                + "\"Thủ đô Việt Nam là gì?\",Hà Nội,Đà Nẵng,Huế,Cần Thơ,A,\"Hà Nội là thủ đô\",Khác,Lớp 3\n"
                + "Dòng bị lỗi đáp án,1,2,3,4,Z,,Toán,Lớp 3\n"
                + "\"2 + 3 = ?\",4,5,6,7,B,\"2 cộng 3 bằng 5\",Toán,Lớp 1\n";

        mockMvc.perform(post("/api/users/quiz/sets/" + setId + "/csv/preview")
                        .header(HttpHeaders.AUTHORIZATION, bearer(parentA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("csvContent", mixedCsv))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalRows").value(3))
                .andExpect(jsonPath("$.data.validCount").value(2))
                .andExpect(jsonPath("$.data.invalidCount").value(1))
                .andExpect(jsonPath("$.data.rows[1].valid").value(false));

        mockMvc.perform(post("/api/users/quiz/sets/" + setId + "/csv/import")
                        .header(HttpHeaders.AUTHORIZATION, bearer(parentA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("pin", "1234", "csvContent", mixedCsv))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.importedCount").value(2))
                .andExpect(jsonPath("$.data.skippedInvalidCount").value(1))
                .andExpect(jsonPath("$.data.totalQuestionsInSet").value(3));

        // xóa câu đầu và tắt rồi xóa cả bộ riêng
        mockMvc.perform(delete("/api/users/quiz/sets/" + setId + "/questions/" + questionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(parentA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("pin", "1234"))))
                .andExpect(status().isNoContent());

        mockMvc.perform(put("/api/users/quiz/sets/" + setId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(parentA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("pin", "1234", "active", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false));

        mockMvc.perform(delete("/api/users/quiz/sets/" + setId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(parentA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("pin", "1234"))))
                .andExpect(status().isNoContent());
    }

    @Test
    void enforcesAccountSetLimitAndQuestionPerSetLimit() throws Exception {
        String parentToken = registerUser("limitmom", "limitmom@example.com", "secret123", "Mẹ Giới Hạn");
        enableParentMode(parentToken, "1111");

        long firstSetId = -1;
        for (int i = 1; i <= 20; i++) {
            long id = createCustomSet(parentToken, "1111", "Bộ câu hỏi " + i, "KHAC", "LOP_2");
            if (i == 1) {
                firstSetId = id;
            }
        }
        // bộ thứ 21 bị từ chối 400
        mockMvc.perform(post("/api/users/quiz/sets")
                        .header(HttpHeaders.AUTHORIZATION, bearer(parentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("pin", "1111", "name", "Bộ vượt hạn mức"))))
                .andExpect(status().isBadRequest());

        // lấp đầy 500 câu vào bộ 1 rồi thêm câu thứ 501 phải bị chặn 400
        LocalDateTime now = LocalDateTime.now();
        List<QuizQuestion> batch = new ArrayList<>();
        for (int i = 1; i <= 500; i++) {
            batch.add(new QuizQuestion(
                    firstSetId, null, "Câu " + i, "A" + i, "B" + i, "C" + i, "D" + i,
                    QuizOptionKey.A, null, QuizSubject.KHAC, QuizGrade.LOP_2, true, now
            ));
        }
        quizQuestionRepository.saveAllAndFlush(batch);

        mockMvc.perform(post("/api/users/quiz/sets/" + firstSetId + "/questions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(parentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "pin", "1111",
                                "questionText", "Câu 501",
                                "optionA", "1",
                                "optionB", "2",
                                "optionC", "3",
                                "optionD", "4",
                                "correctOption", "A",
                                "subject", "KHAC",
                                "grade", "LOP_2"
                        ))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void avoidsRepeatingLastThirtyQuestionsAndShufflesOptionsServerSide() throws Exception {
        String parentToken = registerUser("shuffleparent", "shuffleparent@example.com", "secret123", "Bố Nam");
        enableParentMode(parentToken, "2222");

        // tạo 1 bộ riêng đúng 35 câu ở Lớp 3 rồi gán riêng bộ này ngay lúc tạo hồ sơ bé
        long bigSetId = createCustomSet(parentToken, "2222", "Ôn tập 35 câu", "KHOA_HOC", "LOP_3");
        LocalDateTime now = LocalDateTime.now();
        List<QuizQuestion> pool35 = new ArrayList<>();
        for (int i = 1; i <= 35; i++) {
            pool35.add(new QuizQuestion(
                    bigSetId, null, "Câu ôn tập số " + i + "?",
                    "Đúng " + i, "Sai một " + i, "Sai hai " + i, "Sai ba " + i,
                    QuizOptionKey.A, "Giải thích câu " + i,
                    QuizSubject.KHOA_HOC, QuizGrade.LOP_3, true, now
            ));
        }
        quizQuestionRepository.saveAllAndFlush(pool35);

        MvcResult createChildRes = mockMvc.perform(post("/api/users/profiles")
                        .header(HttpHeaders.AUTHORIZATION, bearer(parentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "pin", "2222",
                                "name", "Bé Minh",
                                "avatarKey", "fox",
                                "quizGrade", "LOP_3",
                                "quizSetIds", List.of(bigSetId)
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.quizGrade").value("LOP_3"))
                .andReturn();
        long childId = objectMapper.readTree(createChildRes.getResponse().getContentAsString()).path("data").path("id").asLong();

        // xem câu mẫu trả về 2-3 câu
        mockMvc.perform(post("/api/users/quiz/samples")
                        .header(HttpHeaders.AUTHORIZATION, bearer(parentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("grade", "LOP_3", "setIds", List.of(bigSetId)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3));

        // nâng trần bonus để bé trả lời liên tục 30 câu không bị 409
        mockMvc.perform(put("/api/users/profiles/" + childId + "/screen-time")
                        .header(HttpHeaders.AUTHORIZATION, bearer(parentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("pin", "2222", "dailyMinutes", 60, "rewardMinutes", 1, "maxBonusMinutes", 120))))
                .andExpect(status().isOk());

        String childToken = switchToChild(parentToken, childId);

        // trả lời 30 câu liên tiếp -> 30 câu hỏi hoàn toàn khác nhau, không lặp bất kỳ câu nào
        Set<Long> askedQuestionIds = new HashSet<>();
        Set<QuizOptionKey> observedCorrectPositions = new HashSet<>();
        for (int i = 0; i < 30; i++) {
            MvcResult chRes = mockMvc.perform(get("/api/users/screen-time/challenge")
                            .header(HttpHeaders.AUTHORIZATION, bearer(childToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.options.length()").value(4))
                    .andExpect(jsonPath("$.data.correctOption").doesNotExist())
                    .andReturn();
            JsonNode ch = objectMapper.readTree(chRes.getResponse().getContentAsString()).path("data");
            String token = ch.path("token").asText();
            QuizTokenCodec.DecodedToken decoded = quizTokenCodec.decode(token, QuizChallengeContext.SCREEN_TIME).orElseThrow();
            assertThat(askedQuestionIds.add(decoded.questionId())).isTrue();
            observedCorrectPositions.add(decoded.correctOption());

            // xác nhận văn bản đáp án ở chữ cái correctOption đúng khớp với đáp án gốc
            String optionTextAtKey = "";
            for (JsonNode opt : ch.path("options")) {
                if (decoded.correctOption().name().equals(opt.path("key").asText())) {
                    optionTextAtKey = opt.path("text").asText();
                }
            }
            assertThat(optionTextAtKey).isEqualTo(decoded.correctText());

            mockMvc.perform(post("/api/users/screen-time/challenge/answer")
                            .header(HttpHeaders.AUTHORIZATION, bearer(childToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("token", token, "option", decoded.correctOption().name()))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.correct").value(true));
        }

        assertThat(askedQuestionIds).hasSize(30);
        // toàn bộ câu gốc có đáp án ở vị trí A, sau khi server trộn phải phân bố ra nhiều chữ cái khác nhau
        assertThat(observedCorrectPositions.size()).isGreaterThanOrEqualTo(3);

        // sau 30/30 câu đúng (100% > 90% qua >= 20 câu): báo cáo tuần gợi ý lên Lớp 4 nhưng KHÔNG tự đổi lớp của bé
        mockMvc.perform(get("/api/users/quiz/profiles")
                        .header(HttpHeaders.AUTHORIZATION, bearer(parentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].grade").value("LOP_3"))
                .andExpect(jsonPath("$.data[0].weeklySummary.totalAnswered").value(30))
                .andExpect(jsonPath("$.data[0].weeklySummary.correctCount").value(30))
                .andExpect(jsonPath("$.data[0].weeklySummary.accuracyPercent").value(100))
                .andExpect(jsonPath("$.data[0].weeklySummary.bySet[0].setName").value("Ôn tập 35 câu"))
                .andExpect(jsonPath("$.data[0].weeklySummary.suggestion.direction").value("UP"))
                .andExpect(jsonPath("$.data[0].weeklySummary.suggestion.suggestedGrade").value("LOP_4"));

        // kid mode không được tự đổi lớp (403)
        mockMvc.perform(post("/api/users/quiz/profiles/" + childId + "/grade")
                        .header(HttpHeaders.AUTHORIZATION, bearer(childToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("pin", "2222", "grade", "LOP_4"))))
                .andExpect(status().isForbidden());

        // phụ huynh nhập sai PIN bị từ chối 400
        mockMvc.perform(post("/api/users/quiz/profiles/" + childId + "/grade")
                        .header(HttpHeaders.AUTHORIZATION, bearer(parentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("pin", "9999", "grade", "LOP_4"))))
                .andExpect(status().isBadRequest());

        // phụ huynh xác nhận đúng PIN -> đổi sang Lớp 4 thành công
        mockMvc.perform(post("/api/users/quiz/profiles/" + childId + "/grade")
                        .header(HttpHeaders.AUTHORIZATION, bearer(parentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("pin", "2222", "grade", "LOP_4"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.grade").value("LOP_4"))
                .andExpect(jsonPath("$.data.vipGrade").value("LOP_5"));
    }

    private long createCustomSet(String token, String pin, String name, String subject, String grade) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/users/quiz/sets")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "pin", pin,
                                "name", name,
                                "defaultSubject", subject,
                                "defaultGrade", grade
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).path("data").path("id").asLong();
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
}
