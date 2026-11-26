package com.dippie.controller.profile;

import com.dippie.common.web.ApiResponse;
import com.dippie.dto.profile.MathChallengeAnswerRequest;
import com.dippie.dto.profile.MathChallengeAnswerResponse;
import com.dippie.dto.profile.MathChallengeResponse;
import com.dippie.dto.profile.ScreenTimeHeartbeatRequest;
import com.dippie.dto.profile.ScreenTimeHeartbeatResponse;
import com.dippie.dto.profile.ScreenTimeStatusView;
import com.dippie.service.profile.MathChallengeService;
import com.dippie.service.profile.ScreenTimeService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// /api/users/screen-time cho hồ sơ trẻ em xem giờ còn lại, gửi nhịp tim 15s và giải toán nhận thêm phút
@RestController
@RequestMapping("/api/users/screen-time")
public class ScreenTimeController {

    private final ScreenTimeService screenTimeService;
    private final MathChallengeService mathChallengeService;

    public ScreenTimeController(ScreenTimeService screenTimeService, MathChallengeService mathChallengeService) {
        this.screenTimeService = screenTimeService;
        this.mathChallengeService = mathChallengeService;
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
    public ResponseEntity<ApiResponse<MathChallengeResponse>> getChallenge() {
        return ResponseEntity.ok(ApiResponse.ok(mathChallengeService.issueChallenge()));
    }

    @PostMapping("/challenge/answer")
    public ResponseEntity<ApiResponse<MathChallengeAnswerResponse>> answerChallenge(
            @RequestBody(required = false) MathChallengeAnswerRequest request
    ) {
        MathChallengeService.AnswerOutcome outcome = mathChallengeService.submitAnswer(request);
        return ResponseEntity.ok(ApiResponse.ok(outcome.response(), outcome.message()));
    }
}
