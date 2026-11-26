package com.dippie.model.progress;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

// Quy tắc chọn tập tiếp theo trong một bộ phim từ danh sách tập và lịch sử tiến độ.
// Thứ tự ưu tiên: nối lại tập đang xem dở mới nhất, sau đó chọn tập chưa xem ngay sau tập vừa xem xong, hết nữa mới quay vòng về tập chưa xem sớm nhất.
public final class NextEpisodePolicy {

    private static final Comparator<Integer> NUMBER_ORDER = Comparator.nullsLast(Comparator.naturalOrder());
    private static final Comparator<Episode> EPISODE_ORDER = Comparator.comparing(Episode::seasonNumber, NUMBER_ORDER)
            .thenComparing(Episode::episodeNumber, NUMBER_ORDER).thenComparing(Episode::id);
    // Khi hai tập cùng updatedAt (vì TIMESTAMP cắt tới giây), lấy bản ghi có progressId lớn hơn trước khi rơi về thứ tự tập.
    private static final Comparator<ResumeCandidate> RESUME_ORDER = Comparator
            .comparing((ResumeCandidate candidate) -> candidate.choice().resumedAt(),
                    Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(ResumeCandidate::progressId, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(candidate -> candidate.choice().episode(), EPISODE_ORDER);

    private NextEpisodePolicy() {
    }

    // Chọn một tập để phát tiếp hoặc trả về rỗng nếu mọi tập đủ điều kiện đều đã xem xong.
    public static Optional<Choice> pick(List<Episode> episodes, List<Progress> progress) {
        Map<String, Progress> byEpisode = progress.stream()
                .collect(Collectors.toMap(Progress::episodeId, Function.identity()));

        // Chia các tập thành ba nhóm: đang xem dở (giữ tập cập nhật gần nhất), đã xem xong (giữ tập hoàn thành gần nhất) và chưa xem
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

        // Nếu người xem nhảy cóc tới xem xong tập giữa bộ thì gợi ý tập chưa xem ngay phía sau, tránh kéo ngược về tập 1
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

    // Gói ứng viên nối lại kèm progressId để phân thắng bại khi trùng mốc giây.
    private record ResumeCandidate(Choice choice, Long progressId) {
    }

    // Tập vừa hoàn thành gần nhất dùng làm mốc định vị tập chưa xem kế tiếp.
    private record CompletedEpisode(Episode episode, LocalDateTime updatedAt, Long progressId) {
    }

    // Dữ liệu tối giản của một tập phim đủ để sắp thứ tự mùa, số tập và tính ngưỡng hoàn thành.
    public record Episode(String id, String title, Integer seasonNumber, Integer episodeNumber, Long durationMs) {
    }

    // Tiến độ đã lưu của một tập phim truyền vào cho NextEpisodePolicy.pick().
    public record Progress(String episodeId, long positionMs, LocalDateTime updatedAt, Long assetDurationMs,
            String assetId, Long progressId) {

        public Progress(String episodeId, long positionMs, LocalDateTime updatedAt, Long assetDurationMs,
                String assetId) {
            this(episodeId, positionMs, updatedAt, assetDurationMs, assetId, null);
        }
    }

    // Tập được đề xuất kèm thời điểm phát tiếp, lý do chọn và nguồn video đang xem (nếu nối lại).
    public record Choice(Episode episode, long resumePositionMs, Reason reason, LocalDateTime resumedAt,
            String assetId) {
    }

    // Phân biệt gợi ý nối tiếp tập đang dở (RESUME) với gợi ý mở tập mới chưa xem (NEXT_UNWATCHED).
    public enum Reason {
        RESUME,
        NEXT_UNWATCHED
    }
}
