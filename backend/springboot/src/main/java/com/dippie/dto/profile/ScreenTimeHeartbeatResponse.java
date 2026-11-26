package com.dippie.dto.profile;

// kết quả sau khi trừ nhịp tim xem phim, kèm số giây thực trừ và trạng thái khóa mới nhất
public record ScreenTimeHeartbeatResponse(
        boolean limited,
        int creditedSeconds,
        boolean ignored,
        Long remainingSeconds,
        boolean locked,
        ScreenTimeStatusView status
) {
}
