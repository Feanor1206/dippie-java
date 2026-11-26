package com.dippie.model.progress;

import com.dippie.exception.ConflictException;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Test đơn vị cho WatchProgress: khởi đầu ở version 0, tăng tuần tự và giữ nguyên trạng thái khi expectedVersion lệch.
class WatchProgressTest {

    @Test
    void startsAtVersionZeroAndAdvancesSequentially() {
        Clock startClock = Clock.fixed(Instant.parse("2026-01-01T10:00:00.555Z"), ZoneOffset.UTC);
        Clock laterClock = Clock.fixed(Instant.parse("2026-01-01T10:05:00.999Z"), ZoneOffset.UTC);

        WatchProgress progress = WatchProgress.start(10L, "ep-1", "ast-1", 12_000L, startClock);
        assertThat(progress.getUserId()).isEqualTo(10L);
        assertThat(progress.getEpisodeId()).isEqualTo("ep-1");
        assertThat(progress.getAssetId()).isEqualTo("ast-1");
        assertThat(progress.getPositionMs()).isEqualTo(12_000L);
        assertThat(progress.getOptLockVersion()).isEqualTo(0L);
        assertThat(progress.getUpdatedAt()).isEqualTo(LocalDateTime.of(2026, 1, 1, 10, 0, 0));

        progress.advance(0L, "ast-2", 45_000L, laterClock);
        assertThat(progress.getAssetId()).isEqualTo("ast-2");
        assertThat(progress.getPositionMs()).isEqualTo(45_000L);
        assertThat(progress.getOptLockVersion()).isEqualTo(1L);
        assertThat(progress.getUpdatedAt()).isEqualTo(LocalDateTime.of(2026, 1, 1, 10, 5, 0));
    }

    @Test
    void rejectsStaleOrFutureVersionWithoutMutatingState() {
        Clock startClock = Clock.fixed(Instant.parse("2026-01-01T10:00:00Z"), ZoneOffset.UTC);
        Clock laterClock = Clock.fixed(Instant.parse("2026-01-01T10:10:00Z"), ZoneOffset.UTC);
        WatchProgress progress = WatchProgress.start(10L, "ep-1", "ast-1", 12_000L, startClock);

        assertThatThrownBy(() -> progress.advance(1L, "ast-2", 99_000L, laterClock))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Tiến độ xem vừa thay đổi ở nơi khác. Bạn hãy tải lại trang rồi thử lại.");

        assertThat(progress.getOptLockVersion()).isEqualTo(0L);
        assertThat(progress.getPositionMs()).isEqualTo(12_000L);
        assertThat(progress.getAssetId()).isEqualTo("ast-1");
        assertThat(progress.getUpdatedAt()).isEqualTo(LocalDateTime.of(2026, 1, 1, 10, 0, 0));
    }
}
