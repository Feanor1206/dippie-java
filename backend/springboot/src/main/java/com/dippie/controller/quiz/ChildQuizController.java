package com.dippie.controller.quiz;

import com.dippie.common.web.ApiResponse;
import com.dippie.dto.quiz.ChildQuizConfigView;
import com.dippie.dto.quiz.ConfirmGradeChangeRequest;
import com.dippie.dto.quiz.UpdateChildQuizSettingsRequest;
import com.dippie.service.quiz.QuizInsightService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// API cấu hình lớp + bộ câu hỏi theo từng bé, tổng kết 7 ngày và xác nhận đổi lớp bằng mã PIN
@RestController
@RequestMapping("/api/users/quiz/profiles")
public class ChildQuizController {

    private final QuizInsightService quizInsightService;

    public ChildQuizController(QuizInsightService quizInsightService) {
        this.quizInsightService = quizInsightService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ChildQuizConfigView>>> listChildConfigs() {
        return ResponseEntity.ok(ApiResponse.ok(quizInsightService.listParentChildConfigs()));
    }

    @PutMapping("/{profileId}")
    public ResponseEntity<ApiResponse<ChildQuizConfigView>> updateChildQuizSettings(
            @PathVariable Long profileId,
            @RequestBody(required = false) UpdateChildQuizSettingsRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
                quizInsightService.updateChildQuizSettings(profileId, request),
                "Đã lưu cài đặt câu hỏi cho bé."
        ));
    }

    @PostMapping("/{profileId}/grade")
    public ResponseEntity<ApiResponse<ChildQuizConfigView>> confirmGradeChange(
            @PathVariable Long profileId,
            @RequestBody(required = false) ConfirmGradeChangeRequest request
    ) {
        ChildQuizConfigView updated = quizInsightService.confirmGradeChange(profileId, request);
        return ResponseEntity.ok(ApiResponse.ok(
                updated,
                "Đã chuyển lớp học cho bé sang " + updated.gradeLabel() + "."
        ));
    }
}
