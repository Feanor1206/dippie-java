package com.dippie.dto.bounty;

import java.util.List;

// tổng hợp bảng nhiệm vụ và các lượt nộp để phụ huynh quản lý trong trang cài đặt tài khoản
public record ParentBountyBoardResponse(
        List<BountyTaskView> tasks,
        List<BountySubmissionView> submissions
) {
}
