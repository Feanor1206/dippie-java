package com.dippie.progress;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class NextEpisodePolicy {

    private static final Comparator<Integer> NUMBER_ORDER = Comparator.nullsLast(Comparator.naturalOrder());
    private static final Comparator<Episode> EPISODE_ORDER = Comparator.comparing(Episode::seasonNumber, NUMBER_ORDER)
            .thenComparing(Episode::episodeNumber, NUMBER_ORDER).thenComparing(Episode::id);
    private static final Comparator<Choice> CHOICE_ORDER = Comparator.comparing(Choice::resumedAt,
                    Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(Choice::episode, EPISODE_ORDER);

    private NextEpisodePolicy() {
    }

    public static Optional<Choice> pick(List<Episode> episodes, List<Progress> progress) {
        Map<String, Progress> byEpisode = progress.stream()
                .collect(Collectors.toMap(Progress::episodeId, Function.identity()));
        return episodes.stream().map(episode -> choice(episode, byEpisode.get(episode.id())))
                .flatMap(Optional::stream).min(CHOICE_ORDER);
    }

    private static Optional<Choice> choice(Episode episode, Progress progress) {
        if (progress == null) {
            return Optional.of(new Choice(episode, 0, Reason.NEXT_UNWATCHED, null));
        }
        WatchDuration duration = WatchDuration.resolve(episode.durationMs(), progress.assetDurationMs());
        if (duration.isCompletedAt(progress.positionMs())) {
            return Optional.empty();
        }
        return Optional.of(progress.positionMs() > 0
                ? new Choice(episode, progress.positionMs(), Reason.RESUME, progress.updatedAt())
                : new Choice(episode, 0, Reason.NEXT_UNWATCHED, null));
    }

    public record Episode(String id, String title, Integer seasonNumber, Integer episodeNumber, Long durationMs) {
    }

    public record Progress(String episodeId, long positionMs, LocalDateTime updatedAt, Long assetDurationMs) {
    }

    public record Choice(Episode episode, long resumePositionMs, Reason reason, LocalDateTime resumedAt) {
    }

    public enum Reason {
        RESUME,
        NEXT_UNWATCHED
    }
}
