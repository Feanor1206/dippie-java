package com.dippie.controller.profile;

import com.dippie.common.web.ApiResponse;
import com.dippie.dto.profile.ParentTimeGrantRequest;
import com.dippie.dto.profile.ScreenTimeHeartbeatRequest;
import com.dippie.dto.profile.ScreenTimeHeartbeatResponse;
import com.dippie.dto.profile.ScreenTimeStatusView;
import com.dippie.dto.quiz.QuizAnswerRequest;
import com.dippie.dto.quiz.QuizChallengeAnswerResponse;
import com.dippie.dto.quiz.QuizChallengeResponse;
import com.dippie.service.profile.ScreenTimeService;
import com.dippie.service.quiz.QuizChallengeService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// /api/users/screen-time cho hồ sơ trẻ em xem giờ còn lại, gửi nhịp tim 15s và trả lời câu hỏi trắc nghiệm nhận thêm phút
@RestController
@RequestMapping("/api/users/screen-time")
public class ScreenTimeController {

    private final ScreenTimeService screenTimeService;
    private final QuizChallengeService quizChallengeService;

    public ScreenTimeController(ScreenTimeService screenTimeService, QuizChallengeService quizChallengeService) {
        this.screenTimeService = screenTimeService;
        this.quizChallengeService = quizChallengeService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<ScreenTimeStatusView>> status() {
        return ResponseEntity.ok(ApiResponse.ok(screenTimeService.getCurrentStatus()));
    }

    @PostMapping("/heartbeat")
    public ResponseEntity<ApiResponse<ScreenTimeHeartbeatResponse>> heartbeat(
            @RequestBody(required = false) ScreenTimeHeartbeatRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(screenTimeService.recordHeartbeat(request)));
    }

    @GetMapping("/challenge")
    public ResponseEntity<ApiResponse<QuizChallengeResponse>> getChallenge() {
        return ResponseEntity.ok(ApiResponse.ok(quizChallengeService.issueChallenge()));
    }

    @PostMapping("/challenge/answer")
    public ResponseEntity<ApiResponse<QuizChallengeAnswerResponse>> answerChallenge(
            @RequestBody(required = false) QuizAnswerRequest request
    ) {
        QuizChallengeService.AnswerOutcome outcome = quizChallengeService.submitAnswer(request);
        return ResponseEntity.ok(ApiResponse.ok(outcome.response(), outcome.message()));
    }

    @PostMapping("/parent-grant")
    public ResponseEntity<ApiResponse<ScreenTimeStatusView>> grantByParentPin(
            @RequestBody(required = false) ParentTimeGrantRequest request
    ) {
        ScreenTimeStatusView updated = screenTimeService.grantExtraMinutesInKidMode(request);
        int minutes = request != null && request.minutes() != null ? request.minutes() : 0;
        return ResponseEntity.ok(ApiResponse.ok(
                updated,
                "Ba mẹ đã cho thêm " + minutes + " phút xem phim hôm nay."
        ));
    }
}
