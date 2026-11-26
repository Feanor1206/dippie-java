package com.dippie.service.progress;

import com.dippie.model.progress.NextEpisodePolicy;
import com.dippie.model.progress.NextEpisodePolicy.Episode;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static com.dippie.model.progress.NextEpisodePolicy.Reason.NEXT_UNWATCHED;
import static com.dippie.model.progress.NextEpisodePolicy.Reason.RESUME;
import static org.assertj.core.api.Assertions.assertThat;

// Test đơn vị cho NextEpisodePolicy: ưu tiên nối lại tập đang xem dở, đi tiếp từ tập vừa xem xong và quay vòng khi đã hết tập cuối.
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

    @Test
    void prefersNextUnwatchedAfterMostRecentlyCompletedAndWrapsWhenFinaleCompleted() {
        // Nhảy cóc xem xong tập 2 trước thì hệ thống phải gợi ý tiếp tập 3 thay vì kéo ngược về tập 1
        var afterSecond = NextEpisodePolicy.pick(
                List.of(FIRST, SECOND, THIRD),
                List.of(progress(SECOND, 95_000L, 5))
        ).orElseThrow();
        assertThat(afterSecond.episode()).isEqualTo(THIRD);
        assertThat(afterSecond.reason()).isEqualTo(NEXT_UNWATCHED);

        // Xem hết tập cuối rồi mà tập đầu vẫn chưa xem thì mới quay vòng về tập đầu
        var wrapped = NextEpisodePolicy.pick(
                List.of(FIRST, SECOND, THIRD),
                List.of(progress(THIRD, 95_000L, 6))
        ).orElseThrow();
        assertThat(wrapped.episode()).isEqualTo(FIRST);
        assertThat(wrapped.reason()).isEqualTo(NEXT_UNWATCHED);
    }

    @Test
    void breaksSameSecondUpdatedAtTiesUsingProgressId() {
        LocalDateTime sameSecond = LocalDateTime.of(2026, 11, 26, 0, 33, 45);
        // Lưu tập 1 rồi lưu tập 2 trong đúng một giây -> bản ghi có progressId lớn hơn phải thắng
        var resume = NextEpisodePolicy.pick(
                List.of(FIRST, SECOND, THIRD),
                List.of(
                        new NextEpisodePolicy.Progress(FIRST.id(), 30_000L, sameSecond, null, "youtube:ep1", 10L),
                        new NextEpisodePolicy.Progress(SECOND.id(), 12_000L, sameSecond, null, "youtube:ep2", 11L)
                )
        ).orElseThrow();
        assertThat(resume.episode()).isEqualTo(SECOND);
        assertThat(resume.resumePositionMs()).isEqualTo(12_000L);
        assertThat(resume.reason()).isEqualTo(RESUME);

        // Hai tập cùng hoàn thành trong một giây -> lấy tập vừa hoàn thành sau (progressId cao hơn) làm mốc
        var afterCompleted = NextEpisodePolicy.pick(
                List.of(FIRST, SECOND, THIRD),
                List.of(
                        new NextEpisodePolicy.Progress(SECOND.id(), 95_000L, sameSecond, null, "youtube:ep2", 20L),
                        new NextEpisodePolicy.Progress(FIRST.id(), 95_000L, sameSecond, null, "youtube:ep1", 21L)
                )
        ).orElseThrow();
        assertThat(afterCompleted.episode()).isEqualTo(THIRD);
        assertThat(afterCompleted.reason()).isEqualTo(NEXT_UNWATCHED);
    }

    private static NextEpisodePolicy.Progress progress(Episode episode, long position, int day) {
        return new NextEpisodePolicy.Progress(episode.id(), position, LocalDateTime.of(2026, 1, day, 0, 0), null,
                "test-asset");
    }
}
