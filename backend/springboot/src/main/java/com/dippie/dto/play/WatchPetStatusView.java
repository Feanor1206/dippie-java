package com.dippie.dto.play;

import com.dippie.model.play.ChildPlaySettings;
import com.dippie.model.play.WatchPetStage;

import java.time.LocalDateTime;

// trạng thái thú ảo canh giờ nghỉ mắt trả về cho trình phát ở chế độ trẻ em
public record WatchPetStatusView(
        boolean enabled,
        WatchPetStage stage,
        int continuousWatchSeconds,
        int sweatyThresholdSeconds,
        int dizzyThresholdSeconds,
        int faintThresholdSeconds,
        int restMinutes,
        boolean fainted,
        boolean resting,
        LocalDateTime restLockUntil,
        long restRemainingSeconds
) {

    public static WatchPetStatusView disabled() {
        return new WatchPetStatusView(
                false,
                WatchPetStage.HAPPY,
                0,
                WatchPetStage.sweatyThresholdSeconds(ChildPlaySettings.DEFAULT_FAINT_MINUTES),
                WatchPetStage.dizzyThresholdSeconds(ChildPlaySettings.DEFAULT_FAINT_MINUTES),
                WatchPetStage.faintThresholdSeconds(ChildPlaySettings.DEFAULT_FAINT_MINUTES),
                ChildPlaySettings.DEFAULT_REST_MINUTES,
                false,
                false,
                null,
                0L
        );
    }
}
