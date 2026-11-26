package com.dippie.dto.bounty;

// payload phụ huynh duyệt hoặc từ chối lượt nộp nhiệm vụ kèm mã PIN và ghi chú tùy chọn
public record BountyDecisionRequest(String pin, String note) {
}
