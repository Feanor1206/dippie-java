package com.dippie.dto.play;

import com.dippie.model.play.ChildPlaySettings;

// cài đặt 3 tính năng xem phim của hồ sơ trẻ em
public record PlaySettingsView(
        boolean watchPetEnabled,
        int watchPetFaintMinutes,
        int watchPetRestMinutes,
        boolean lootDropEnabled,
        boolean recapToNextEnabled
) {

    public static PlaySettingsView from(ChildPlaySettings settings) {
        return new PlaySettingsView(
                settings.isWatchPetEnabled(),
                settings.getWatchPetFaintMinutes(),
                settings.getWatchPetRestMinutes(),
                settings.isLootDropEnabled(),
                settings.isRecapToNextEnabled()
        );
    }

    public static PlaySettingsView disabled() {
        return new PlaySettingsView(
                false,
                ChildPlaySettings.DEFAULT_FAINT_MINUTES,
                ChildPlaySettings.DEFAULT_REST_MINUTES,
                false,
                false
        );
    }
}
