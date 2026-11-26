package com.dippie.controller.bounty;

import com.dippie.common.web.ApiResponse;
import com.dippie.dto.bounty.BountyDecisionRequest;
import com.dippie.dto.bounty.BountyDecisionResponse;
import com.dippie.dto.bounty.BountySubmissionView;
import com.dippie.dto.bounty.BountyTaskMutationRequest;
import com.dippie.dto.bounty.BountyTaskView;
import com.dippie.dto.bounty.KidBountyBoardResponse;
import com.dippie.dto.bounty.ParentBountyBoardResponse;
import com.dippie.dto.profile.PinRequest;
import com.dippie.service.bounty.BountyBoardService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// /api/users/bounties cho phụ huynh quản lý nhiệm vụ đời thực, bé nộp nhiệm vụ và ba mẹ duyệt thưởng phút bằng PIN
@RestController
@RequestMapping("/api/users/bounties")
public class BountyBoardController {

    private final BountyBoardService bountyBoardService;

    public BountyBoardController(BountyBoardService bountyBoardService) {
        this.bountyBoardService = bountyBoardService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<ParentBountyBoardResponse>> parentBoard() {
        return ResponseEntity.ok(ApiResponse.ok(bountyBoardService.getParentBoard()));
    }

    @PostMapping("/tasks")
    public ResponseEntity<ApiResponse<BountyTaskView>> createTask(
            @RequestBody(required = false) BountyTaskMutationRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(bountyBoardService.createTask(request), "Đã tạo nhiệm vụ mới."));
    }

    @PutMapping("/tasks/{taskId}")
    public ResponseEntity<ApiResponse<BountyTaskView>> updateTask(
            @PathVariable Long taskId,
            @RequestBody(required = false) BountyTaskMutationRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
                bountyBoardService.updateTask(taskId, request),
                "Đã cập nhật nhiệm vụ."
        ));
    }

    @DeleteMapping("/tasks/{taskId}")
    public ResponseEntity<Void> deleteTask(
            @PathVariable Long taskId,
            @RequestBody(required = false) PinRequest request
    ) {
        bountyBoardService.deleteTask(taskId, request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/kid")
    public ResponseEntity<ApiResponse<KidBountyBoardResponse>> kidBoard() {
        return ResponseEntity.ok(ApiResponse.ok(bountyBoardService.getKidBoard()));
    }

    @PostMapping("/tasks/{taskId}/submit")
    public ResponseEntity<ApiResponse<BountySubmissionView>> submitTask(@PathVariable Long taskId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(
                        bountyBoardService.submitTask(taskId),
                        "Bé đã nộp nhiệm vụ! Chờ ba mẹ duyệt nhé."
                ));
    }

    @PostMapping("/submissions/{submissionId}/approve")
    public ResponseEntity<ApiResponse<BountyDecisionResponse>> approveSubmission(
            @PathVariable Long submissionId,
            @RequestBody(required = false) BountyDecisionRequest request
    ) {
        BountyDecisionResponse decided = bountyBoardService.approveSubmission(submissionId, request);
        return ResponseEntity.ok(ApiResponse.ok(
                decided,
                "Đã duyệt nhiệm vụ và cộng thêm " + decided.submission().rewardMinutes() + " phút xem phim!"
        ));
    }

    @PostMapping("/submissions/{submissionId}/reject")
    public ResponseEntity<ApiResponse<BountyDecisionResponse>> rejectSubmission(
            @PathVariable Long submissionId,
            @RequestBody(required = false) BountyDecisionRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
                bountyBoardService.rejectSubmission(submissionId, request),
                "Đã từ chối lượt nộp nhiệm vụ."
        ));
    }
}
