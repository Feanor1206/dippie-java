package com.dippie.service.vip;

import com.dippie.dto.quiz.QuizAnswerRequest;
import com.dippie.dto.vip.ChildProfileVipSeriesView;
import com.dippie.dto.vip.KidVipStatusResponse;
import com.dippie.dto.vip.UpdateVipSeriesRequest;
import com.dippie.dto.vip.VipChallengeResponse;
import com.dippie.dto.vip.VipUnlockResponse;
import com.dippie.exception.ConflictException;
import com.dippie.exception.InvalidRequestException;
import com.dippie.exception.NotFoundException;
import com.dippie.exception.UnauthorizedException;
import com.dippie.exception.VipEpisodeLockedException;
import com.dippie.model.catalog.CatalogEpisode;
import com.dippie.model.profile.ChildProfile;
import com.dippie.model.profile.ScreenTimeSummary;
import com.dippie.model.quiz.QuizChallengeContext;
import com.dippie.model.quiz.QuizGrade;
import com.dippie.model.user.User;
import com.dippie.model.vip.ChildVipEpisodeUnlock;
import com.dippie.model.vip.ChildVipSeries;
import com.dippie.repository.catalog.CatalogEpisodeRepository;
import com.dippie.repository.catalog.CatalogSeriesRepository;
import com.dippie.repository.profile.ChildProfileRepository;
import com.dippie.repository.user.UserRepository;
import com.dippie.repository.vip.ChildVipEpisodeUnlockRepository;
import com.dippie.repository.vip.ChildVipSeriesRepository;
import com.dippie.security.CurrentViewer;
import com.dippie.security.Viewer;
import com.dippie.service.catalog.CatalogVisibility;
import com.dippie.service.profile.ParentModeService;
import com.dippie.service.quiz.QuizRateLimiter;
import com.dippie.service.quiz.QuizSelectionService;
import com.dippie.service.quiz.QuizTokenCodec;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// quản lý phim bộ VIP theo từng bé: phụ huynh gắn cờ VIP, bé trả lời 1 câu hỏi khó hơn (lớp kế tiếp hoặc cao nhất) để mở khóa tập trong ngày
@Service
public class VipTitleService {

    private static final String LOGIN_REQUIRED = "Bạn cần đăng nhập để tiếp tục.";
    private static final String CHILD_MANAGE_FORBIDDEN = "Hồ sơ trẻ em không thể chỉnh danh sách phim VIP.";
    private static final String CHILD_ONLY_MESSAGE = "Mở khóa tập phim VIP chỉ áp dụng cho hồ sơ trẻ em.";
    private static final String EPISODE_NOT_FOUND = "Không tìm thấy tập phim này.";
    private static final String VIP_LOCKED_MESSAGE =
            "Tập phim VIP đang khóa. Bé trả lời đúng một câu hỏi thử thách để mở khóa tập này hôm nay nhé.";

    private final CurrentViewer currentViewer;
    private final UserRepository userRepository;
    private final ChildProfileRepository childProfileRepository;
    private final CatalogSeriesRepository catalogSeriesRepository;
    private final CatalogEpisodeRepository catalogEpisodeRepository;
    private final CatalogVisibility catalogVisibility;
    private final ChildVipSeriesRepository vipSeriesRepository;
    private final ChildVipEpisodeUnlockRepository vipUnlockRepository;
    private final ParentModeService parentModeService;
    private final QuizSelectionService quizSelectionService;
    private final QuizRateLimiter rateLimiter;
    private final Clock clock;

    public VipTitleService(
            CurrentViewer currentViewer,
            UserRepository userRepository,
            ChildProfileRepository childProfileRepository,
            CatalogSeriesRepository catalogSeriesRepository,
            CatalogEpisodeRepository catalogEpisodeRepository,
            CatalogVisibility catalogVisibility,
            ChildVipSeriesRepository vipSeriesRepository,
            ChildVipEpisodeUnlockRepository vipUnlockRepository,
            ParentModeService parentModeService,
            QuizSelectionService quizSelectionService,
            QuizRateLimiter rateLimiter,
            Clock clock
    ) {
        this.currentViewer = currentViewer;
        this.userRepository = userRepository;
        this.childProfileRepository = childProfileRepository;
        this.catalogSeriesRepository = catalogSeriesRepository;
        this.catalogEpisodeRepository = catalogEpisodeRepository;
        this.catalogVisibility = catalogVisibility;
        this.vipSeriesRepository = vipSeriesRepository;
        this.vipUnlockRepository = vipUnlockRepository;
        this.parentModeService = parentModeService;
        this.quizSelectionService = quizSelectionService;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }

    // lớp thử thách VIP luôn là lớp kế tiếp so với lớp hiện tại của bé, cao nhất là Lớp 5
    public static QuizGrade requiredVipGrade(QuizGrade childGrade) {
        return (childGrade == null ? QuizGrade.LOP_1 : childGrade).nextGrade();
    }

    // phụ huynh xem danh sách bộ phim VIP đã gắn cho từng hồ sơ bé
    @Transactional(readOnly = true)
    public List<ChildProfileVipSeriesView> listParentOverview() {
        Viewer viewer = currentViewer.requireMainProfile(CHILD_MANAGE_FORBIDDEN);
        User user = userRepository.findById(viewer.userId())
                .orElseThrow(() -> new UnauthorizedException(LOGIN_REQUIRED));
        if (!user.isParentModeEnabled()) {
            return List.of();
        }
        List<ChildProfile> profiles = childProfileRepository.findByUserIdOrderByCreatedAtAscIdAsc(user.getId());
        if (profiles.isEmpty()) {
            return List.of();
        }
        List<Long> profileIds = profiles.stream().map(ChildProfile::getId).toList();
        Map<Long, List<String>> seriesByProfile = new HashMap<>();
        for (ChildVipSeries row : vipSeriesRepository.findByProfileIdIn(profileIds)) {
            seriesByProfile.computeIfAbsent(row.getProfileId(), ignored -> new ArrayList<>()).add(row.getSeriesId());
        }
        return profiles.stream()
                .map(profile -> {
                    QuizGrade grade = profile.getQuizGrade();
                    QuizGrade vipGrade = requiredVipGrade(grade);
                    return new ChildProfileVipSeriesView(
                            profile.getId(),
                            profile.getName(),
                            profile.getAvatarKey(),
                            grade.name(),
                            grade.label(),
                            vipGrade.name(),
                            vipGrade.label(),
                            List.copyOf(seriesByProfile.getOrDefault(profile.getId(), List.of()))
                    );
                })
                .toList();
    }

    // phụ huynh lưu danh sách phim bộ VIP cho 1 hồ sơ bé sau khi nhập đúng PIN
    @Transactional
    public ChildProfileVipSeriesView updateChildVipSeries(Long profileId, UpdateVipSeriesRequest request) {
        Viewer viewer = currentViewer.requireMainProfile(CHILD_MANAGE_FORBIDDEN);
        User user = userRepository.findByIdForUpdate(viewer.userId())
                .orElseThrow(() -> new UnauthorizedException(LOGIN_REQUIRED));
        if (!user.isParentModeEnabled()) {
            throw new InvalidRequestException("Hãy bật chế độ hồ sơ trẻ em trước.");
        }
        parentModeService.verifyParentPin(user, request == null ? null : request.pin());
        ChildProfile profile = childProfileRepository.findByIdAndUserIdForUpdate(profileId, user.getId())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy hồ sơ trẻ em này."));

        Set<String> cleanSeriesIds = new LinkedHashSet<>();
        if (request != null && request.seriesIds() != null) {
            for (String rawId : request.seriesIds()) {
                String trimmed = rawId == null ? "" : rawId.trim();
                if (!trimmed.isEmpty()) {
                    cleanSeriesIds.add(trimmed);
                }
            }
        }
        for (String seriesId : cleanSeriesIds) {
            if (!catalogSeriesRepository.existsById(seriesId)) {
                throw new NotFoundException("Không tìm thấy bộ phim với mã: " + seriesId);
            }
        }

        vipSeriesRepository.deleteByProfileId(profile.getId());
        vipSeriesRepository.flush();
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        if (!cleanSeriesIds.isEmpty()) {
            List<ChildVipSeries> rows = cleanSeriesIds.stream()
                    .map(seriesId -> new ChildVipSeries(profile.getId(), seriesId, now))
                    .toList();
            vipSeriesRepository.saveAllAndFlush(rows);
        }
        QuizGrade grade = profile.getQuizGrade();
        QuizGrade vipGrade = requiredVipGrade(grade);
        return new ChildProfileVipSeriesView(
                profile.getId(),
                profile.getName(),
                profile.getAvatarKey(),
                grade.name(),
                grade.label(),
                vipGrade.name(),
                vipGrade.label(),
                List.copyOf(cleanSeriesIds)
        );
    }

    // kid mode tải danh sách bộ VIP và các tập VIP đã mở khóa trong hôm nay
    @Transactional(readOnly = true)
    public KidVipStatusResponse getKidStatus() {
        Viewer viewer = currentViewer.require();
        LocalDate today = ScreenTimeSummary.todayVietnam(clock);
        if (!viewer.isChildProfile()) {
            return KidVipStatusResponse.empty(today);
        }
        ChildProfile profile = childProfileRepository.findByIdAndUserId(viewer.profileId(), viewer.userId())
                .orElseThrow(() -> new UnauthorizedException(LOGIN_REQUIRED));
        List<String> vipSeriesIds = vipSeriesRepository.findByProfileIdOrderByCreatedAtAscIdAsc(profile.getId())
                .stream()
                .map(ChildVipSeries::getSeriesId)
                .toList();
        List<String> unlockedEpisodeIds = vipUnlockRepository.findByProfileIdAndUnlockDay(profile.getId(), today)
                .stream()
                .map(ChildVipEpisodeUnlock::getEpisodeId)
                .toList();
        QuizGrade grade = profile.getQuizGrade();
        QuizGrade vipGrade = requiredVipGrade(grade);
        return new KidVipStatusResponse(
                profile.getId(),
                today,
                grade.name(),
                grade.label(),
                vipGrade.name(),
                vipGrade.label(),
                vipSeriesIds,
                unlockedEpisodeIds
        );
    }

    // phát câu hỏi VIP ở lớp khó hơn gắn token khóa cứng vào đúng tập phim và hồ sơ bé
    @Transactional(readOnly = true)
    public VipChallengeResponse issueEpisodeChallenge(String episodeId) {
        Viewer viewer = currentViewer.require();
        if (!viewer.isChildProfile()) {
            throw new InvalidRequestException(CHILD_ONLY_MESSAGE);
        }
        ChildProfile profile = childProfileRepository.findByIdAndUserId(viewer.profileId(), viewer.userId())
                .orElseThrow(() -> new UnauthorizedException(LOGIN_REQUIRED));
        CatalogEpisode episode = catalogEpisodeRepository.findById(episodeId)
                .filter(catalogVisibility::isEpisodeVisible)
                .orElseThrow(() -> new NotFoundException(EPISODE_NOT_FOUND));
        if (!vipSeriesRepository.existsByProfileIdAndSeriesId(profile.getId(), episode.getSeriesId())) {
            throw new InvalidRequestException("Tập phim này không cần mở khóa VIP.");
        }
        LocalDate today = ScreenTimeSummary.todayVietnam(clock);
        if (vipUnlockRepository.existsByProfileIdAndEpisodeIdAndUnlockDay(profile.getId(), episode.getId(), today)) {
            throw new ConflictException("Tập phim VIP này đã được mở khóa hôm nay rồi.");
        }

        QuizSelectionService.IssuedQuizChallenge issued = quizSelectionService.issueForChild(
                profile,
                QuizChallengeContext.VIP_UNLOCK,
                scopeForEpisode(episode.getId()),
                QuizTokenCodec.STANDARD_TTL,
                true
        );
        return new VipChallengeResponse(
                issued.token(),
                episode.getId(),
                episode.getSeriesId(),
                issued.question(),
                issued.options(),
                issued.subject().name(),
                issued.subject().label(),
                issued.grade().name(),
                issued.grade().label(),
                issued.setName(),
                issued.expiresAt(),
                issued.expiresInSeconds()
        );
    }

    // chấm lựa chọn mở khóa VIP bằng token ký HMAC dùng 1 lần, đúng thì mở đúng tập này cho hết hôm nay
    @Transactional
    public UnlockOutcome submitEpisodeUnlock(String episodeId, QuizAnswerRequest request) {
        Viewer viewer = currentViewer.require();
        if (!viewer.isChildProfile()) {
            throw new InvalidRequestException(CHILD_ONLY_MESSAGE);
        }
        Long profileId = viewer.profileId();
        rateLimiter.ensureNotLocked(profileId);

        ChildProfile profile = childProfileRepository.findByIdAndUserIdForUpdate(profileId, viewer.userId())
                .orElseThrow(() -> new UnauthorizedException(LOGIN_REQUIRED));
        CatalogEpisode episode = catalogEpisodeRepository.findById(episodeId)
                .filter(catalogVisibility::isEpisodeVisible)
                .orElseThrow(() -> new NotFoundException(EPISODE_NOT_FOUND));
        if (!vipSeriesRepository.existsByProfileIdAndSeriesId(profile.getId(), episode.getSeriesId())) {
            throw new InvalidRequestException("Tập phim này không cần mở khóa VIP.");
        }

        QuizSelectionService.VerifiedAnswer verified = quizSelectionService.verifyAndRecordAnswer(
                profileId,
                QuizChallengeContext.VIP_UNLOCK,
                scopeForEpisode(episode.getId()),
                null,
                request,
                true
        );

        LocalDate today = ScreenTimeSummary.todayVietnam(clock);
        if (!verified.correct()) {
            return new UnlockOutcome(
                    new VipUnlockResponse(
                            false,
                            false,
                            episode.getId(),
                            today,
                            verified.correctOption(),
                            verified.correctText(),
                            verified.explanation()
                    ),
                    verified.gentleWrongMessage()
            );
        }

        if (!vipUnlockRepository.existsByProfileIdAndEpisodeIdAndUnlockDay(profile.getId(), episode.getId(), today)) {
            LocalDateTime nowUtc = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
            try {
                vipUnlockRepository.saveAndFlush(new ChildVipEpisodeUnlock(profile.getId(), episode.getId(), today, nowUtc));
            } catch (DataIntegrityViolationException ignored) {
                // lỡ 2 request mở khóa cùng lúc thì giữ bản ghi vừa chèn trước
            }
        }
        return new UnlockOutcome(
                new VipUnlockResponse(
                        true,
                        true,
                        episode.getId(),
                        today,
                        verified.correctOption(),
                        verified.correctText(),
                        verified.explanation()
                ),
                "Giỏi lắm! Tập phim VIP đã được mở khóa cho hôm nay."
        );
    }

    // chặn lưu tiến độ cho tập VIP chưa mở khóa hôm nay, tập thường hoặc người lớn đi qua ngay
    @Transactional(readOnly = true)
    public void requireEpisodeUnlocked(Viewer viewer, String seriesId, String episodeId) {
        if (viewer == null || !viewer.isChildProfile()) {
            return;
        }
        requireEpisodeUnlockedForProfile(viewer.userId(), viewer.profileId(), seriesId, episodeId);
    }

    @Transactional(readOnly = true)
    public void requireEpisodeUnlockedForProfile(Long userId, Long profileId, String seriesId, String episodeId) {
        if (userId == null || profileId == null || seriesId == null || episodeId == null) {
            return;
        }
        if (!vipSeriesRepository.existsByProfileIdAndSeriesId(profileId, seriesId)) {
            return;
        }
        LocalDate today = ScreenTimeSummary.todayVietnam(clock);
        if (!vipUnlockRepository.existsByProfileIdAndEpisodeIdAndUnlockDay(profileId, episodeId, today)) {
            throw new VipEpisodeLockedException(VIP_LOCKED_MESSAGE);
        }
    }

    // chặn cấp vé phát và stream khi mọi tập đã xuất bản gắn với file này đều đang bị khóa VIP
    @Transactional(readOnly = true)
    public void requireAssetUnlockedForProfile(
            Long userId,
            Long profileId,
            List<CatalogEpisodeRepository.EpisodeWithParents> linkedEpisodes
    ) {
        if (userId == null || profileId == null || linkedEpisodes == null || linkedEpisodes.isEmpty()) {
            return;
        }
        List<CatalogEpisodeRepository.EpisodeWithParents> published = linkedEpisodes.stream()
                .filter(CatalogEpisodeRepository.EpisodeWithParents::isPublished)
                .toList();
        if (published.isEmpty()) {
            return;
        }
        LocalDate today = ScreenTimeSummary.todayVietnam(clock);
        for (CatalogEpisodeRepository.EpisodeWithParents item : published) {
            String seriesId = item.series().getId();
            String episodeId = item.episode().getId();
            if (!vipSeriesRepository.existsByProfileIdAndSeriesId(profileId, seriesId)
                    || vipUnlockRepository.existsByProfileIdAndEpisodeIdAndUnlockDay(profileId, episodeId, today)) {
                return;
            }
        }
        throw new VipEpisodeLockedException(VIP_LOCKED_MESSAGE);
    }

    private static String scopeForEpisode(String episodeId) {
        return "vip:" + episodeId;
    }

    public record UnlockOutcome(VipUnlockResponse response, String message) {
    }
}
