package com.dippie.service.progress;

import com.dippie.model.progress.CompletionPolicy;
import com.dippie.model.progress.WatchDuration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// test ngưỡng xem xong (5% cuối, tối đa 60s, tối thiểu 1 ms) với WatchDuration chọn thời lượng dương nhỏ hơn
class CompletionPolicyTest {

    @Test
    void returnsFalseWhenDurationIsMissingOrNonPositive() {
        assertThat(CompletionPolicy.isCompleted(0L, null)).isFalse();
        assertThat(CompletionPolicy.isCompleted(1000L, 0L)).isFalse();
        assertThat(CompletionPolicy.isCompleted(1000L, -500L)).isFalse();
    }

    @Test
    void enforcesMinimumOneMillisecondMarginForTinyDurations() {
        assertThat(CompletionPolicy.isCompleted(0L, 1L)).isTrue();
        assertThat(CompletionPolicy.isCompleted(17L, 19L)).isFalse();
        assertThat(CompletionPolicy.isCompleted(18L, 19L)).isTrue();
        assertThat(CompletionPolicy.isCompleted(18L, 20L)).isFalse();
        assertThat(CompletionPolicy.isCompleted(19L, 20L)).isTrue();
    }

    @Test
    void usesFivePercentThresholdBelowTwentyMinutes() {
        long tenMinutes = 600_000L;
        assertThat(CompletionPolicy.isCompleted(569_999L, tenMinutes)).isFalse();
        assertThat(CompletionPolicy.isCompleted(570_000L, tenMinutes)).isTrue();
        assertThat(CompletionPolicy.isCompleted(600_000L, tenMinutes)).isTrue();

        long twentyMinutes = 1_200_000L;
        assertThat(CompletionPolicy.isCompleted(1_139_999L, twentyMinutes)).isFalse();
        assertThat(CompletionPolicy.isCompleted(1_140_000L, twentyMinutes)).isTrue();
    }

    @Test
    void capsNearEndWindowAtSixtySecondsForLongDurations() {
        long oneHour = 3_600_000L;
        assertThat(CompletionPolicy.isCompleted(3_420_000L, oneHour)).isFalse();
        assertThat(CompletionPolicy.isCompleted(3_539_999L, oneHour)).isFalse();
        assertThat(CompletionPolicy.isCompleted(3_540_000L, oneHour)).isTrue();
    }

    @Test
    void watchDurationPicksMinimumPositiveDuration() {
        assertThat(WatchDuration.resolve(600_000L, 500_000L).nullableDurationMs()).isEqualTo(500_000L);
        assertThat(WatchDuration.resolve(400_000L, null).nullableDurationMs()).isEqualTo(400_000L);
        assertThat(WatchDuration.resolve(0L, 300_000L).nullableDurationMs()).isEqualTo(300_000L);
        assertThat(WatchDuration.resolve(null, null).nullableDurationMs()).isNull();
        assertThat(WatchDuration.resolve(600_000L, 500_000L).isExceededBy(500_001L)).isTrue();
        assertThat(WatchDuration.resolve(600_000L, 500_000L).isExceededBy(500_000L)).isFalse();
    }
}
