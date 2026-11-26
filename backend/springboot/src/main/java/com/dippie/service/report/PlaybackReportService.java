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

// Xử lý nhận báo lỗi phát video cho các tập đã đăng và cung cấp danh sách kèm thao tác xử lý cho admin.
@Service
public class PlaybackReportService {

    public static final String ACTION_PLAYBACK_REPORT_UPDATED = "PLAYBACK_REPORT_UPDATED";
    public static final String EPISODE_NOT_PUBLISHED_MESSAGE = "Không tìm thấy tập phim này hoặc tập phim chưa được đăng.";

    private static final int MAX_ADMIN_LIST_SIZE = 200;
    private static final String TARGET_TYPE_PLAYBACK_REPORT = "PLAYBACK_REPORT";
    private static final String REPORT_NOT_FOUND_MESSAGE = "Không tìm thấy báo cáo lỗi này.";

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

    // Kiểm tra giới hạn theo IP, xác thực dữ liệu đầu vào và chắc chắn tập phim đang xuất bản rồi mới lưu báo cáo.
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

        // Xác minh cả tập phim, mùa cha lẫn bộ phim đều đang ở trạng thái đã đăng.
        requirePublishedEpisode(episodeId);

        // Có phiên đăng nhập thì gắn reporterUserId, khách vãng lai thì để null.
        Long reporterUserId = currentViewer.find().map(Viewer::userId).orElse(null);
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);

        PlaybackReport report = new PlaybackReport(episodeId, assetId, reporterUserId, reason, note, now);
        PlaybackReport saved = reportRepository.saveAndFlush(report);
        return new PlaybackReportCreatedResponse(saved.getId());
    }

    // Lấy tối đa 200 báo cáo mới nhất theo bộ lọc trạng thái rồi nạp thêm tên bộ và tên tập.
    @Transactional(readOnly = true)
    public List<PlaybackReportView> listForAdmin(String rawStatus) {
        currentViewer.requireAdmin();
        Optional<PlaybackReportStatus> filter = PlaybackReportStatus.parseFilter(rawStatus);
        PageRequest limit = PageRequest.of(0, MAX_ADMIN_LIST_SIZE);
        List<PlaybackReport> reports = filter
                .map(status -> reportRepository.findByStatusOrderByCreatedAtDescIdDesc(status.code(), limit))
                .orElseGet(() -> reportRepository.findAllByOrderByCreatedAtDescIdDesc(limit));
        return enrichViews(reports);
    }

    // Đổi trạng thái báo cáo sang resolved hoặc dismissed và ghi dòng nhật ký trong cùng giao dịch.
    @Transactional
    public PlaybackReportView updateStatus(Long reportId, UpdatePlaybackReportStatusRequest request) {
        Viewer actor = currentViewer.requireAdmin();
        PlaybackReportStatus nextStatus = PlaybackReportStatus.parseResolution(
                request == null ? null : request.status()
        );
        PlaybackReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new NotFoundException(REPORT_NOT_FOUND_MESSAGE));

        String previousStatus = report.getStatus();
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        report.updateResolution(nextStatus, actor.userId(), now);
        PlaybackReport saved = reportRepository.saveAndFlush(report);

        // Ghi lại trạng thái cũ và mới vào nhật ký quản trị.
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("episodeId", saved.getEpisodeId());
        details.put("reason", saved.getReason());
        details.put("previousStatus", previousStatus);
        details.put("newStatus", saved.getStatus());
        auditTrailWriter.record(
                actor,
                ACTION_PLAYBACK_REPORT_UPDATED,
                TARGET_TYPE_PLAYBACK_REPORT,
                String.valueOf(saved.getId()),
                details
        );

        return enrichViews(List.of(saved)).getFirst();
    }

    // Chặn báo lỗi nhắm vào tập nháp, tập ẩn hoặc tập gắn sai bộ phim.
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

    // Việt giữ cách nạp theo lô ở đây nhé; gọi findById từng dòng trong vòng lặp sẽ dính N+1 query ngay khi danh sách nhiều lên.
    private List<PlaybackReportView> enrichViews(List<PlaybackReport> reports) {
        if (reports.isEmpty()) {
            return List.of();
        }
        Set<String> episodeIds = new HashSet<>();
        for (PlaybackReport report : reports) {
            episodeIds.add(report.getEpisodeId());
        }

        // Nạp hàng loạt bản ghi tập phim từ danh mục chuẩn.
        Map<String, CatalogEpisode> episodesById = new HashMap<>();
        Set<String> seriesIds = new HashSet<>();
        for (CatalogEpisode episode : episodeRepository.findAllById(episodeIds)) {
            episodesById.put(episode.getId(), episode);
            if (episode.getSeriesId() != null) {
                seriesIds.add(episode.getSeriesId());
            }
        }

        // Gom các seriesId tìm được để lấy tên bộ phim trong một câu query.
        Map<String, String> seriesTitleById = new HashMap<>();
        if (!seriesIds.isEmpty()) {
            for (CatalogSeries series : seriesRepository.findAllById(seriesIds)) {
                seriesTitleById.put(series.getId(), series.getTitle());
            }
        }

        // Những mã không thấy trong catalog_episodes thì tra dự phòng bên bảng movies.
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
