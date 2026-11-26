package com.dippie.dto.play;

import com.dippie.model.play.ChildPlaySettings;
import com.dippie.model.profile.ChildProfile;

// cài đặt tính năng xem phim theo từng bé trả cho màn phụ huynh
public record ChildProfilePlaySettingsView(
        Long profileId,
        String name,
        String avatarKey,
        boolean watchPetEnabled,
        int watchPetFaintMinutes,
        int watchPetRestMinutes,
        boolean lootDropEnabled,
        boolean recapToNextEnabled
) {

    public static ChildProfilePlaySettingsView of(ChildProfile profile, ChildPlaySettings settings) {
        return new ChildProfilePlaySettingsView(
                profile.getId(),
                profile.getName(),
                profile.getAvatarKey(),
                settings.isWatchPetEnabled(),
                settings.getWatchPetFaintMinutes(),
                settings.getWatchPetRestMinutes(),
                settings.isLootDropEnabled(),
                settings.isRecapToNextEnabled()
        );
    }
}
