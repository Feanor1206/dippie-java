package com.dippie.model.play;

import com.dippie.exception.InvalidRequestException;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// test quy tắc đổi trạng thái Dieppie, nghỉ 2 phút reset bộ đếm và giới hạn cài đặt phụ huynh
class WatchPetStageTest {

    @Test
    void resolvesStagesAtExactDefaultThresholdsAndScalesForCustomFaintMinutes() {
        assertThat(WatchPetStage.sweatyThresholdSeconds(20)).isEqualTo(15 * 60);
        assertThat(WatchPetStage.dizzyThresholdSeconds(20)).isEqualTo(18 * 60);
        assertThat(WatchPetStage.faintThresholdSeconds(20)).isEqualTo(20 * 60);

        assertThat(WatchPetStage.resolve(false, false, 0, 20)).isEqualTo(WatchPetStage.HAPPY);
        assertThat(WatchPetStage.resolve(false, false, 15 * 60 - 1, 20)).isEqualTo(WatchPetStage.HAPPY);
        assertThat(WatchPetStage.resolve(false, false, 15 * 60, 20)).isEqualTo(WatchPetStage.SWEATY);
        assertThat(WatchPetStage.resolve(false, false, 18 * 60 - 1, 20)).isEqualTo(WatchPetStage.SWEATY);
        assertThat(WatchPetStage.resolve(false, false, 18 * 60, 20)).isEqualTo(WatchPetStage.DIZZY);
        assertThat(WatchPetStage.resolve(false, false, 20 * 60, 20)).isEqualTo(WatchPetStage.FAINTED);
        assertThat(WatchPetStage.resolve(true, true, 20 * 60, 20)).isEqualTo(WatchPetStage.RESTING);

        // ngưỡng tùy chỉnh 10 phút: 75% là 450s, 90% là 540s, ngất ở 600s
        assertThat(WatchPetStage.resolve(false, false, 449, 10)).isEqualTo(WatchPetStage.HAPPY);
        assertThat(WatchPetStage.resolve(false, false, 450, 10)).isEqualTo(WatchPetStage.SWEATY);
        assertThat(WatchPetStage.resolve(false, false, 540, 10)).isEqualTo(WatchPetStage.DIZZY);
        assertThat(WatchPetStage.resolve(false, false, 600, 10)).isEqualTo(WatchPetStage.FAINTED);
    }

    @Test
    void resetsContinuousWatchAfterTwoMinutePauseAndClearsRestOnExpiry() {
        LocalDateTime t0 = LocalDateTime.of(2026, 11, 26, 10, 0, 0);
        ChildPlaySettings settings = ChildPlaySettings.defaults(1L, t0);
        ChildPlayState state = new ChildPlayState(1L, 360, t0);

        state.recordWatchSeconds(600, settings, t0);
        assertThat(state.getContinuousWatchSeconds()).isEqualTo(600);

        // nghỉ 119 giây chưa đủ 2 phút nên vẫn cộng dồn
        state.recordWatchSeconds(15, settings, t0.plusSeconds(119));
        assertThat(state.getContinuousWatchSeconds()).isEqualTo(615);

        // nghỉ đủ 120 giây là đặt lại về 0 trước khi cộng nhịp mới
        state.recordWatchSeconds(15, settings, t0.plusSeconds(119 + 120));
        assertThat(state.getContinuousWatchSeconds()).isEqualTo(15);

        // chạm ngưỡng 20 phút -> ngất -> cho ngủ 5 phút -> hết 5 phút tự khỏe lại
        state.recordWatchSeconds(1200, settings, t0.plusSeconds(255));
        assertThat(state.isPetFainted()).isTrue();

        state.startRest(5, t0.plusSeconds(260));
        assertThat(state.isRestingAt(t0.plusSeconds(260 + 299))).isTrue();
        assertThat(state.isRestingAt(t0.plusSeconds(260 + 300))).isFalse();

        assertThat(state.clearExpiredRestIfNeeded(t0.plusSeconds(260 + 300))).isTrue();
        assertThat(state.isPetFainted()).isFalse();
        assertThat(state.getContinuousWatchSeconds()).isZero();
        assertThat(state.getRestLockUntil()).isNull();
    }

    @Test
    void validatesParentSettingsBounds() {
        assertThat(ChildPlaySettings.validateFaintMinutes(10)).isEqualTo(10);
        assertThat(ChildPlaySettings.validateFaintMinutes(60)).isEqualTo(60);
        assertThatThrownBy(() -> ChildPlaySettings.validateFaintMinutes(9))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Ngưỡng Dieppie mệt xỉu cần từ 10 đến 60 phút.");
        assertThatThrownBy(() -> ChildPlaySettings.validateFaintMinutes(61)).isInstanceOf(InvalidRequestException.class);

        assertThat(ChildPlaySettings.validateRestMinutes(3)).isEqualTo(3);
        assertThat(ChildPlaySettings.validateRestMinutes(15)).isEqualTo(15);
        assertThatThrownBy(() -> ChildPlaySettings.validateRestMinutes(2))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Thời gian cho Dieppie nghỉ mắt cần từ 3 đến 15 phút.");
        assertThatThrownBy(() -> ChildPlaySettings.validateRestMinutes(16)).isInstanceOf(InvalidRequestException.class);
    }
}
