package com.dippie.progress;

import com.dippie.common.error.ConflictException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WatchProgressTest {

    @Test
    void startsAtVersionZeroAndAdvancesSequentially() {
        Clock t0 = Clock.fixed(Instant.parse("2026-01-01T10:00:00.555Z"), ZoneOffset.UTC);
        Clock t1 = Clock.fixed(Instant.parse("2026-01-01T10:05:00.999Z"), ZoneOffset.UTC);

        WatchProgress progress = WatchProgress.start(10L, "ep-1", "ast-1", 12_000L, t0);
        assertThat(progress.getUserId()).isEqualTo(10L);
        assertThat(progress.getEpisodeId()).isEqualTo("ep-1");
        assertThat(progress.getAssetId()).isEqualTo("ast-1");
        assertThat(progress.getPositionMs()).isEqualTo(12_000L);
        assertThat(progress.getOptLockVersion()).isEqualTo(0L);
        assertThat(progress.getUpdatedAt()).isEqualTo(LocalDateTime.of(2026, 1, 1, 10, 0, 0));

        progress.advance(0L, "ast-2", 45_000L, t1);
        assertThat(progress.getAssetId()).isEqualTo("ast-2");
        assertThat(progress.getPositionMs()).isEqualTo(45_000L);
        assertThat(progress.getOptLockVersion()).isEqualTo(1L);
        assertThat(progress.getUpdatedAt()).isEqualTo(LocalDateTime.of(2026, 1, 1, 10, 5, 0));
    }

    @Test
    void rejectsStaleOrFutureVersionWithoutMutatingState() {
        Clock t0 = Clock.fixed(Instant.parse("2026-01-01T10:00:00Z"), ZoneOffset.UTC);
        Clock t1 = Clock.fixed(Instant.parse("2026-01-01T10:10:00Z"), ZoneOffset.UTC);
        WatchProgress progress = WatchProgress.start(10L, "ep-1", "ast-1", 12_000L, t0);

        assertThatThrownBy(() -> progress.advance(1L, "ast-2", 99_000L, t1))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Watch progress changed since it was loaded; refresh and retry.");

        assertThat(progress.getOptLockVersion()).isEqualTo(0L);
        assertThat(progress.getPositionMs()).isEqualTo(12_000L);
        assertThat(progress.getAssetId()).isEqualTo("ast-1");
        assertThat(progress.getUpdatedAt()).isEqualTo(LocalDateTime.of(2026, 1, 1, 10, 0, 0));
    }
}
