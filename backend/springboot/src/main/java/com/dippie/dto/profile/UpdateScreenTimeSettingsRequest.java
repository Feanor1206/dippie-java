package com.dippie.dto.profile;

// payload phụ huynh sửa giới hạn xem mỗi ngày (hoặc không giới hạn) và cấu hình giải toán cho 1 hồ sơ bé
public record UpdateScreenTimeSettingsRequest(
        String pin,
        Integer dailyMinutes,
        Boolean unlimited,
        Integer mathLevel,
        Integer rewardMinutes,
        Integer maxBonusMinutes
) {
}
