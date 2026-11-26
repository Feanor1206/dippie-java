package com.dippie.controller.quiz;

import com.dippie.common.web.ApiResponse;
import com.dippie.dto.profile.PinRequest;
import com.dippie.dto.quiz.CreateQuizSetRequest;
import com.dippie.dto.quiz.QuizCsvImportRequest;
import com.dippie.dto.quiz.QuizCsvImportResponse;
import com.dippie.dto.quiz.QuizCsvPreviewRequest;
import com.dippie.dto.quiz.QuizCsvPreviewResponse;
import com.dippie.dto.quiz.QuizQuestionMutationRequest;
import com.dippie.dto.quiz.QuizQuestionView;
import com.dippie.dto.quiz.QuizSampleQuestionView;
import com.dippie.dto.quiz.QuizSampleRequest;
import com.dippie.dto.quiz.QuizSetDetailResponse;
import com.dippie.dto.quiz.QuizSetView;
import com.dippie.dto.quiz.QuizSetsOverviewResponse;
import com.dippie.dto.quiz.UpdateQuizSetRequest;
import com.dippie.model.quiz.QuizGrade;
import com.dippie.security.CurrentViewer;
import com.dippie.security.Viewer;
import com.dippie.service.quiz.QuizBankService;
import com.dippie.service.quiz.QuizCsvParser;
import com.dippie.service.quiz.QuizSelectionService;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;

// API quản lý bộ câu hỏi, câu hỏi thủ công, xem trước/nhập CSV, tải tệp mẫu và xem trước câu hỏi mẫu cho phụ huynh
@RestController
@RequestMapping("/api/users/quiz")
public class QuizBankController {

    private final QuizBankService quizBankService;
    private final QuizSelectionService quizSelectionService;
    private final CurrentViewer currentViewer;

    public QuizBankController(
            QuizBankService quizBankService,
            QuizSelectionService quizSelectionService,
            CurrentViewer currentViewer
    ) {
        this.quizBankService = quizBankService;
        this.quizSelectionService = quizSelectionService;
        this.currentViewer = currentViewer;
    }

    @GetMapping("/sets")
    public ResponseEntity<ApiResponse<QuizSetsOverviewResponse>> listSets() {
        return ResponseEntity.ok(ApiResponse.ok(quizBankService.listSets()));
    }

    @PostMapping("/sets")
    public ResponseEntity<ApiResponse<QuizSetView>> createSet(
            @RequestBody(required = false) CreateQuizSetRequest request
    ) {
        QuizSetView created = quizBankService.createSet(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(created, "Đã tạo bộ câu hỏi mới."));
    }

    @PutMapping("/sets/{setId}")
    public ResponseEntity<ApiResponse<QuizSetView>> updateSet(
            @PathVariable Long setId,
            @RequestBody(required = false) UpdateQuizSetRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
                quizBankService.updateSet(setId, request),
                "Đã cập nhật bộ câu hỏi."
        ));
    }

    @DeleteMapping("/sets/{setId}")
    public ResponseEntity<Void> deleteSet(
            @PathVariable Long setId,
            @RequestBody(required = false) PinRequest request
    ) {
        quizBankService.deleteSet(setId, request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/sets/{setId}/questions")
    public ResponseEntity<ApiResponse<QuizSetDetailResponse>> getSetDetail(@PathVariable Long setId) {
        return ResponseEntity.ok(ApiResponse.ok(quizBankService.getSetDetail(setId)));
    }

    @PostMapping("/sets/{setId}/questions")
    public ResponseEntity<ApiResponse<QuizQuestionView>> addQuestion(
            @PathVariable Long setId,
            @RequestBody(required = false) QuizQuestionMutationRequest request
    ) {
        QuizQuestionView created = quizBankService.addQuestion(setId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(created, "Đã thêm câu hỏi mới."));
    }

    @PutMapping("/sets/{setId}/questions/{questionId}")
    public ResponseEntity<ApiResponse<QuizQuestionView>> updateQuestion(
            @PathVariable Long setId,
            @PathVariable Long questionId,
            @RequestBody(required = false) QuizQuestionMutationRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
                quizBankService.updateQuestion(setId, questionId, request),
                "Đã cập nhật câu hỏi."
        ));
    }

    @DeleteMapping("/sets/{setId}/questions/{questionId}")
    public ResponseEntity<Void> deleteQuestion(
            @PathVariable Long setId,
            @PathVariable Long questionId,
            @RequestBody(required = false) PinRequest request
    ) {
        quizBankService.deleteQuestion(setId, questionId, request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/csv-template")
    public ResponseEntity<byte[]> downloadCsvTemplate() {
        currentViewer.requireMainProfile("Hồ sơ trẻ em không thể tải mẫu câu hỏi.");
        byte[] bytes = QuizCsvParser.TEMPLATE_CSV.getBytes(StandardCharsets.UTF_8);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "csv", StandardCharsets.UTF_8));
        headers.setContentDisposition(ContentDisposition.attachment().filename("mau-cau-hoi-dippie.csv").build());
        return ResponseEntity.ok().headers(headers).body(bytes);
    }

    @PostMapping("/sets/{setId}/csv/preview")
    public ResponseEntity<ApiResponse<QuizCsvPreviewResponse>> previewCsv(
            @PathVariable Long setId,
            @RequestBody(required = false) QuizCsvPreviewRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(quizBankService.previewCsv(setId, request)));
    }

    @PostMapping("/sets/{setId}/csv/import")
    public ResponseEntity<ApiResponse<QuizCsvImportResponse>> importCsv(
            @PathVariable Long setId,
            @RequestBody(required = false) QuizCsvImportRequest request
    ) {
        QuizCsvImportResponse result = quizBankService.importCsv(setId, request);
        return ResponseEntity.ok(ApiResponse.ok(
                result,
                "Đã nhập " + result.importedCount() + " câu hỏi hợp lệ vào bộ."
        ));
    }

    @PostMapping("/samples")
    public ResponseEntity<ApiResponse<List<QuizSampleQuestionView>>> previewSamples(
            @RequestBody(required = false) QuizSampleRequest request
    ) {
        Viewer viewer = currentViewer.requireMainProfile("Hồ sơ trẻ em không thể xem trước bộ câu hỏi.");
        QuizGrade grade = request != null && request.grade() != null && !request.grade().isBlank()
                ? QuizGrade.require(request.grade())
                : QuizGrade.LOP_1;
        List<Long> setIds = request == null ? null : request.setIds();
        return ResponseEntity.ok(ApiResponse.ok(quizSelectionService.previewSamples(viewer.userId(), grade, setIds)));
    }
}
