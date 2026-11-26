package com.dippie.dto.play;

// tổng hợp trạng thái 3 tính năng xem phim trẻ em cho trình phát
public record PlayFeaturesStatusResponse(
        boolean active,
        PlaySettingsView settings,
        WatchPetStatusView watchPet,
        LootStatusView loot,
        RecapStatusView recap
) {

    public static PlayFeaturesStatusResponse inactive() {
        return new PlayFeaturesStatusResponse(
                false,
                PlaySettingsView.disabled(),
                WatchPetStatusView.disabled(),
                LootStatusView.disabled(),
                RecapStatusView.disabled()
        );
    }
}
