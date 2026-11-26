package com.dippie.service.report;

import com.dippie.dto.report.CreatePlaybackReportRequest;
import com.dippie.dto.report.PlaybackReportCreatedResponse;
import com.dippie.dto.report.PlaybackReportView;
import com.dippie.dto.report.UpdatePlaybackReportStatusRequest;
import com.dippie.exception.InvalidRequestException;
import com.dippie.exception.NotFoundException;
import com.dippie.model.catalog.CatalogEpisode;
import com.dippie.model.catalog.CatalogSeason;
import com.dippie.model.catalog.CatalogSeries;
import com.dippie.model.movie.Movie;
import com.dippie.model.report.PlaybackReport;
import com.dippie.model.report.PlaybackReportReason;
import com.dippie.model.report.PlaybackReportStatus;
import com.dippie.repository.catalog.CatalogEpisodeRepository;
import com.dippie.repository.catalog.CatalogSeasonRepository;
import com.dippie.repository.catalog.CatalogSeriesRepository;
import com.dippie.repository.movie.MovieRepository;
import com.dippie.repository.report.PlaybackReportRepository;
import com.dippie.security.CurrentViewer;
import com.dippie.security.Viewer;
import com.dippie.service.admin.AuditTrailWriter;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

// nhận báo lỗi phát video cho mấy tập đã đăng rồi cho admin xem list và cập nhật xử lý
@Service
public class PlaybackReportService {

    public static final String ACTION_PLAYBACK_REPORT_UPDATED = "PLAYBACK_REPORT_UPDATED";
    public static final String EPISODE_NOT_PUBLISHED_MESSAGE = "Không tìm thấy tập phim này hoặc tập phim chưa được đăng.";


    private final PlaybackReportRepository reportRepository;
    private final CatalogEpisodeRepository episodeRepository;
    private final CatalogSeasonRepository seasonRepository;
    private final CatalogSeriesRepository seriesRepository;
    private final MovieRepository movieRepository;
    private final CurrentViewer currentViewer;
    private final PlaybackReportRateLimiter rateLimiter;
    private final AuditTrailWriter auditTrailWriter;
    private final Clock clock;

    public PlaybackReportService(
            PlaybackReportRepository reportRepository,
            CatalogEpisodeRepository episodeRepository,
            CatalogSeasonRepository seasonRepository,
            CatalogSeriesRepository seriesRepository,
            MovieRepository movieRepository,
            CurrentViewer currentViewer,
            PlaybackReportRateLimiter rateLimiter,
            AuditTrailWriter auditTrailWriter,
            Clock clock
    ) {
        this.reportRepository = reportRepository;
        this.episodeRepository = episodeRepository;
        this.seasonRepository = seasonRepository;
        this.seriesRepository = seriesRepository;
        this.movieRepository = movieRepository;
        this.currentViewer = currentViewer;
        this.rateLimiter = rateLimiter;
        this.auditTrailWriter = auditTrailWriter;
        this.clock = clock;
    }

    // check rate limit theo IP, kiểm tra đầu vào với tập đang xuất bản rồi mới lưu báo lỗi
    @Transactional
    public PlaybackReportCreatedResponse submit(CreatePlaybackReportRequest request, String clientIp) {
        rateLimiter.enforce(clientIp);

        CreatePlaybackReportRequest payload = request == null
                ? new CreatePlaybackReportRequest(null, null, null, null)
                : request;
        String episodeId = payload.resolvedEpisodeId();
        String assetId = payload.resolvedAssetId();
        PlaybackReportReason reason = PlaybackReportReason.parse(payload.reason());
        String note = payload.resolvedNote();

        // check đủ cả tập, mùa cha lẫn bộ đều đã đăng
        requirePublishedEpisode(episodeId);

        // đang đăng nhập thì gắn reporterUserId, khách vãng lai thì để null
        Long reporterUserId = currentViewer.find().map(Viewer::userId).orElse(null);
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);

        PlaybackReport report = new PlaybackReport(episodeId, assetId, reporterUserId, reason, note, now);
        PlaybackReport saved = reportRepository.saveAndFlush(report);
        return new PlaybackReportCreatedResponse(saved.getId());
    }

    // lấy tối đa 200 báo lỗi mới nhất theo bộ lọc trạng thái rồi gắn thêm tên bộ với tên tập
    @Transactional(readOnly = true)
    public List<PlaybackReportView> listForAdmin(String rawStatus) {
        currentViewer.requireAdmin();
        Optional<PlaybackReportStatus> filter = PlaybackReportStatus.parseFilter(rawStatus);
        PageRequest limit = PageRequest.of(0, 200);
        List<PlaybackReport> reports = filter
                .map(status -> reportRepository.findByStatusOrderByCreatedAtDescIdDesc(status.code(), limit))
                .orElseGet(() -> reportRepository.findAllByOrderByCreatedAtDescIdDesc(limit));
        return enrichViews(reports);
    }

    // chuyển báo lỗi sang resolved hoặc dismissed rồi ghi audit chung trong 1 transaction
    @Transactional
    public PlaybackReportView updateStatus(Long reportId, UpdatePlaybackReportStatusRequest request) {
        Viewer actor = currentViewer.requireAdmin();
        PlaybackReportStatus nextStatus = PlaybackReportStatus.parseResolution(
                request == null ? null : request.status()
        );
        PlaybackReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy báo cáo lỗi này."));

        String previousStatus = report.getStatus();
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        report.updateResolution(nextStatus, actor.userId(), now);
        PlaybackReport saved = reportRepository.saveAndFlush(report);

        // lưu trạng thái cũ với mới vào audit
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("episodeId", saved.getEpisodeId());
        details.put("reason", saved.getReason());
        details.put("previousStatus", previousStatus);
        details.put("newStatus", saved.getStatus());
        auditTrailWriter.record(
                actor,
                ACTION_PLAYBACK_REPORT_UPDATED,
                "PLAYBACK_REPORT",
                String.valueOf(saved.getId()),
                details
        );

        return enrichViews(List.of(saved)).getFirst();
    }

    // chặn báo lỗi vào tập nháp, tập ẩn hay tập gắn nhầm bộ
    private void requirePublishedEpisode(String episodeId) {
        CatalogEpisode episode = episodeRepository.findById(episodeId)
                .filter(CatalogEpisode::isPublished)
                .orElseThrow(() -> new InvalidRequestException(EPISODE_NOT_PUBLISHED_MESSAGE));
        CatalogSeries series = seriesRepository.findById(episode.getSeriesId())
                .filter(CatalogSeries::isPublished)
                .orElseThrow(() -> new InvalidRequestException(EPISODE_NOT_PUBLISHED_MESSAGE));
        if (episode.getSeasonId() != null && !episode.getSeasonId().isEmpty()) {
            CatalogSeason season = seasonRepository.findById(episode.getSeasonId())
                    .filter(candidate -> candidate.isPublished() && series.getId().equals(candidate.getSeriesId()))
                    .orElseThrow(() -> new InvalidRequestException(EPISODE_NOT_PUBLISHED_MESSAGE));
            if (season == null) {
                throw new InvalidRequestException(EPISODE_NOT_PUBLISHED_MESSAGE);
            }
        }
    }

    // Việt giữ kiểu kéo theo lô chỗ này nhé, gọi findById trong vòng lặp là dính N+1 query ngay lúc list dày lên
    private List<PlaybackReportView> enrichViews(List<PlaybackReport> reports) {
        if (reports.isEmpty()) {
            return List.of();
        }
        Set<String> episodeIds = new HashSet<>();
        for (PlaybackReport report : reports) {
            episodeIds.add(report.getEpisodeId());
        }

        // kéo 1 lượt các tập phim từ danh mục chuẩn
        Map<String, CatalogEpisode> episodesById = new HashMap<>();
        Set<String> seriesIds = new HashSet<>();
        for (CatalogEpisode episode : episodeRepository.findAllById(episodeIds)) {
            episodesById.put(episode.getId(), episode);
            if (episode.getSeriesId() != null) {
                seriesIds.add(episode.getSeriesId());
            }
        }

        // gom đống seriesId vừa tìm được để lấy tên bộ trong 1 query
        Map<String, String> seriesTitleById = new HashMap<>();
        if (!seriesIds.isEmpty()) {
            for (CatalogSeries series : seriesRepository.findAllById(seriesIds)) {
                seriesTitleById.put(series.getId(), series.getTitle());
            }
        }

        // mã nào không thấy bên catalog_episodes thì tra dự phòng qua bảng movies
        Set<String> missingIds = new HashSet<>(episodeIds);
        missingIds.removeAll(episodesById.keySet());
        Map<String, String> movieTitleById = new HashMap<>();
        if (!missingIds.isEmpty()) {
            for (Movie movie : movieRepository.findAllById(missingIds)) {
                movieTitleById.put(movie.getId(), movie.getTitle());
            }
        }

        return reports.stream().map(report -> {
            CatalogEpisode episode = episodesById.get(report.getEpisodeId());
            String seriesTitle = episode == null ? null : seriesTitleById.get(episode.getSeriesId());
            String episodeTitle = episode != null
                    ? episode.getTitle()
                    : movieTitleById.get(report.getEpisodeId());
            return PlaybackReportView.of(report, seriesTitle, episodeTitle);
        }).toList();
    }
}
