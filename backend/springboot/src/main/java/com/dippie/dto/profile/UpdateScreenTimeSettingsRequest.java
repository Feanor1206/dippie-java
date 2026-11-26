package com.dippie.dto.profile;

// payload phụ huynh sửa giới hạn xem mỗi ngày và cấu hình giải toán cho 1 hồ sơ bé
public record UpdateScreenTimeSettingsRequest(
        String pin,
        Integer dailyMinutes,
        Integer mathLevel,
        Integer rewardMinutes,
        Integer maxBonusMinutes
) {
}
