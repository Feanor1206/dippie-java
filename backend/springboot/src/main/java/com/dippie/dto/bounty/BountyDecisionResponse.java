package com.dippie.dto.bounty;

import com.dippie.dto.profile.ScreenTimeStatusView;

// kết quả sau khi phụ huynh duyệt hoặc từ chối nhiệm vụ, kèm trạng thái thời gian xem mới nhất của bé
public record BountyDecisionResponse(
        BountySubmissionView submission,
        ScreenTimeStatusView screenTime
) {
}
