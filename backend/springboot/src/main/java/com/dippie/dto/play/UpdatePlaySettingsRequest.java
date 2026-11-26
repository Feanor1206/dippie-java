package com.dippie.dto.play;

// request phụ huynh cập nhật cấu hình thú ảo, rương báu và ôn tập cho một hồ sơ bé kèm mã PIN
public record UpdatePlaySettingsRequest(
        String pin,
        Boolean watchPetEnabled,
        Integer watchPetFaintMinutes,
        Integer watchPetRestMinutes,
        Boolean lootDropEnabled,
        Boolean recapToNextEnabled
) {
}
