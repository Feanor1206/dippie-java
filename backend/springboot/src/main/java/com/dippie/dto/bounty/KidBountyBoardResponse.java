package com.dippie.dto.bounty;

import java.time.LocalDate;
import java.util.List;

// phản hồi bảng nhiệm vụ đời thực cho chế độ trẻ em gồm danh sách nhiệm vụ khả dụng và lượt nộp gần đây
public record KidBountyBoardResponse(
        Long profileId,
        LocalDate day,
        List<KidBountyTaskView> tasks,
        List<BountySubmissionView> submissions
) {
}
