package com.dippie.model.progress;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

// luật chọn tập phát tiếp trong bộ phim từ danh sách tập với lịch sử xem
// ưu tiên nối tập đang dở mới nhất, tới tập chưa xem ngay sau tập vừa xem xong, hết nữa mới vòng về tập chưa xem sớm nhất
public final class NextEpisodePolicy {

    private static final Comparator<Integer> NUMBER_ORDER = Comparator.nullsLast(Comparator.naturalOrder());
    private static final Comparator<Episode> EPISODE_ORDER = Comparator.comparing(Episode::seasonNumber, NUMBER_ORDER)
            .thenComparing(Episode::episodeNumber, NUMBER_ORDER).thenComparing(Episode::id);
    // hai tập trùng giây updatedAt do TIMESTAMP làm tròn thì lấy progressId lớn hơn, hòa nữa mới theo thứ tự tập
    private static final Comparator<ResumeCandidate> RESUME_ORDER = Comparator
            .comparing((ResumeCandidate candidate) -> candidate.choice().resumedAt(),
                    Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(ResumeCandidate::progressId, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(candidate -> candidate.choice().episode(), EPISODE_ORDER);

    private NextEpisodePolicy() {
    }

    // chọn một tập phát tiếp, xem hết sạch mấy tập hợp lệ rồi thì trả rỗng
    public static Optional<Choice> pick(List<Episode> episodes, List<Progress> progress) {
        Map<String, Progress> byEpisode = progress.stream()
                .collect(Collectors.toMap(Progress::episodeId, Function.identity()));

        // chia 3 nhóm: đang xem dở (giữ tập mới nhất), xem xong (giữ tập hoàn thành gần nhất) với chưa xem
        ResumeCandidate resumeChoice = null;
        CompletedEpisode latestCompleted = null;
        List<Episode> unwatched = new java.util.ArrayList<>();

        for (Episode episode : episodes) {
            Progress entry = byEpisode.get(episode.id());
            if (entry == null) {
                unwatched.add(episode);
                continue;
            }
            WatchDuration duration = WatchDuration.resolve(episode.durationMs(), entry.assetDurationMs());
            if (duration.isCompletedAt(entry.positionMs())) {
                CompletedEpisode candidate = new CompletedEpisode(episode, entry.updatedAt(), entry.progressId());
                if (latestCompleted == null || COMPLETED_ORDER.compare(candidate, latestCompleted) < 0) {
                    latestCompleted = candidate;
                }
                continue;
            }
            if (entry.positionMs() > 0) {
                ResumeCandidate candidate = new ResumeCandidate(
                        new Choice(episode, entry.positionMs(), Reason.RESUME, entry.updatedAt(), entry.assetId()),
                        entry.progressId());
                if (resumeChoice == null || RESUME_ORDER.compare(candidate, resumeChoice) < 0) {
                    resumeChoice = candidate;
                }
            } else {
                unwatched.add(episode);
            }
        }

        if (resumeChoice != null) {
            return Optional.of(resumeChoice.choice());
        }
        if (unwatched.isEmpty()) {
            return Optional.empty();
        }
        unwatched.sort(EPISODE_ORDER);

        // người xem nhảy cóc tới tập giữa bộ thì gợi ý tập chưa xem ngay sau nó, khỏi kéo ngược về tập 1
        if (latestCompleted != null) {
            Episode anchor = latestCompleted.episode();
            for (Episode candidate : unwatched) {
                if (EPISODE_ORDER.compare(candidate, anchor) > 0) {
                    return Optional.of(new Choice(candidate, 0, Reason.NEXT_UNWATCHED, null, null));
                }
            }
        }
        return Optional.of(new Choice(unwatched.getFirst(), 0, Reason.NEXT_UNWATCHED, null, null));
    }

    private static final Comparator<CompletedEpisode> COMPLETED_ORDER = Comparator.comparing(
                    CompletedEpisode::updatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(CompletedEpisode::progressId, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(CompletedEpisode::episode, EPISODE_ORDER.reversed());

    // gói ứng viên xem tiếp kèm progressId để phân thắng bại lúc trùng giây
    private record ResumeCandidate(Choice choice, Long progressId) {
    }

    // tập vừa xem xong gần nhất làm mốc tìm tập chưa xem kế tiếp
    private record CompletedEpisode(Episode episode, LocalDateTime updatedAt, Long progressId) {
    }

    // thông tin tối giản của một tập, vừa đủ để xếp mùa/tập và tính ngưỡng xem xong
    public record Episode(String id, String title, Integer seasonNumber, Integer episodeNumber, Long durationMs) {
    }

    // tiến độ đã lưu của một tập truyền vào NextEpisodePolicy.pick()
    public record Progress(String episodeId, long positionMs, LocalDateTime updatedAt, Long assetDurationMs,
            String assetId, Long progressId) {

        public Progress(String episodeId, long positionMs, LocalDateTime updatedAt, Long assetDurationMs,
                String assetId) {
            this(episodeId, positionMs, updatedAt, assetDurationMs, assetId, null);
        }
    }

    // tập được chọn kèm mốc phát tiếp, lý do với nguồn video đang xem nếu là nối lại
    public record Choice(Episode episode, long resumePositionMs, Reason reason, LocalDateTime resumedAt,
            String assetId) {
    }

    // phân biệt nối lại tập đang dở (RESUME) với mở tập chưa xem (NEXT_UNWATCHED)
    public enum Reason {
        RESUME,
        NEXT_UNWATCHED
    }
}
