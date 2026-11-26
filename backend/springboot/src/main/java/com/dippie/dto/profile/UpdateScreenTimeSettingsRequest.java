package com.dippie.dto.profile;

// payload phụ huynh sửa giới hạn xem mỗi ngày (hoặc không giới hạn) và mức thưởng phút cho 1 hồ sơ bé
public record UpdateScreenTimeSettingsRequest(
        String pin,
        Integer dailyMinutes,
        Boolean unlimited,
        Integer rewardMinutes,
        Integer maxBonusMinutes
) {
}
