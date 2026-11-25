package com.dippie.progress;

import com.dippie.progress.NextEpisodePolicy.Episode;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static com.dippie.progress.NextEpisodePolicy.Reason.NEXT_UNWATCHED;
import static com.dippie.progress.NextEpisodePolicy.Reason.RESUME;
import static org.assertj.core.api.Assertions.assertThat;

class NextEpisodePolicyTest {

    private static final Episode FIRST = new Episode("first", "First", 1, 4, 100_000L);
    private static final Episode SECOND = new Episode("second", "Second", 2, 1, 100_000L);
    private static final Episode THIRD = new Episode("third", "Third", 2, 2, 100_000L);

    @Test
    void resumesTheMostRecentlyUpdatedUnfinishedEpisodeBeforeUnwatchedEpisodes() {
        var result = NextEpisodePolicy.pick(List.of(FIRST, SECOND, THIRD),
                List.of(progress(THIRD, 2_000L, 1), progress(SECOND, 1_000L, 2))).orElseThrow();

        assertThat(result.episode()).isEqualTo(SECOND);
        assertThat(result.resumePositionMs()).isEqualTo(1_000L);
        assertThat(result.reason()).isEqualTo(RESUME);
    }

    @Test
    void skipsCompletedEpisodesAndOrdersBySeasonThenEpisodeNumber() {
        var result = NextEpisodePolicy.pick(List.of(THIRD, SECOND, FIRST),
                List.of(progress(FIRST, 95_000L, 1), progress(SECOND, 0L, 2))).orElseThrow();

        assertThat(result.episode()).isEqualTo(SECOND);
        assertThat(result.resumePositionMs()).isZero();
        assertThat(result.reason()).isEqualTo(NEXT_UNWATCHED);
        assertThat(NextEpisodePolicy.pick(List.of(THIRD, SECOND, FIRST), List.of()).orElseThrow().episode())
                .isEqualTo(FIRST);
    }

    @Test
    void returnsEmptyWhenAllEligibleEpisodesAreCompletedOrThereAreNone() {
        assertThat(NextEpisodePolicy.pick(List.of(FIRST), List.of(progress(FIRST, 95_000L, 1)))).isEmpty();
        assertThat(NextEpisodePolicy.pick(List.of(), List.of(progress(FIRST, 1_000L, 1)))).isEmpty();
    }

    private static NextEpisodePolicy.Progress progress(Episode episode, long position, int day) {
        return new NextEpisodePolicy.Progress(episode.id(), position, LocalDateTime.of(2026, 1, day, 0, 0), null);
    }
}
