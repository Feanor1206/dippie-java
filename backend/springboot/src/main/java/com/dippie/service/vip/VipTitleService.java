package com.dippie.service.vip;

import com.dippie.dto.profile.MathChallengeAnswerRequest;
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
import com.dippie.model.profile.MathChallengeGenerator;
import com.dippie.model.profile.ScreenTimeSummary;
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
import com.dippie.service.profile.MathChallengeRateLimiter;
import com.dippie.service.profile.MathChallengeTokenCodec;
import com.dippie.service.profile.ParentModeService;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// quản lý phim bộ VIP theo từng bé: phụ huynh gắn cờ VIP, bé giải 1 bài toán khó hơn 1 bậc (tối đa mức 4) để mở khóa tập trong ngày
@Service
public class VipTitleService {

    private static final String LOGIN_REQUIRED = "Bạn cần đăng nhập để tiếp tục.";
    private static final String CHILD_MANAGE_FORBIDDEN = "Hồ sơ trẻ em không thể chỉnh danh sách phim VIP.";
    private static final String CHILD_ONLY_MESSAGE = "Mở khóa tập phim VIP chỉ áp dụng cho hồ sơ trẻ em.";
    private static final String EPISODE_NOT_FOUND = "Không tìm thấy tập phim này.";
    private static final String VIP_LOCKED_MESSAGE =
            "Tập phim VIP đang khóa. Bé giải một bài toán để mở khóa tập này hôm nay nhé.";
    private static final String TOKEN_USED_MESSAGE = "Câu hỏi này đã được trả lời rồi. Bé lấy câu mới nhé.";

    private final CurrentViewer currentViewer;
    private final UserRepository userRepository;
    private final ChildProfileRepository childProfileRepository;
    private final CatalogSeriesRepository catalogSeriesRepository;
    private final CatalogEpisodeRepository catalogEpisodeRepository;
    private final CatalogVisibility catalogVisibility;
    private final ChildVipSeriesRepository vipSeriesRepository;
    private final ChildVipEpisodeUnlockRepository vipUnlockRepository;
    private final ParentModeService parentModeService;
    private final MathChallengeTokenCodec tokenCodec;
    private final MathChallengeRateLimiter rateLimiter;
    private final Clock clock;
    private final MathChallengeGenerator generator = new MathChallengeGenerator();
    private final Map<String, Instant> usedTokenIds = new LinkedHashMap<>();
    private final Object usedTokensLock = new Object();

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
            MathChallengeTokenCodec tokenCodec,
            MathChallengeRateLimiter rateLimiter,
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
        this.tokenCodec = tokenCodec;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }

    // mức toán cho phim VIP luôn tăng thêm 1 bậc so với mức hiện tại của bé, trần là mức 4
    public static int requiredVipMathLevel(int childMathLevel) {
        return Math.min(4, Math.max(1, childMathLevel) + 1);
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
                .map(profile -> new ChildProfileVipSeriesView(
                        profile.getId(),
                        profile.getName(),
                        profile.getAvatarKey(),
                        profile.getMathLevel(),
                        requiredVipMathLevel(profile.getMathLevel()),
                        List.copyOf(seriesByProfile.getOrDefault(profile.getId(), List.of()))
                ))
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
        return new ChildProfileVipSeriesView(
                profile.getId(),
                profile.getName(),
                profile.getAvatarKey(),
                profile.getMathLevel(),
                requiredVipMathLevel(profile.getMathLevel()),
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
        return new KidVipStatusResponse(
                profile.getId(),
                today,
                profile.getMathLevel(),
                requiredVipMathLevel(profile.getMathLevel()),
                vipSeriesIds,
                unlockedEpisodeIds
        );
    }

    // phát đề toán mức (mathLevel + 1, tối đa 4) gắn token v2 khóa cứng vào đúng tập VIP và hồ sơ bé
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

        int vipLevel = requiredVipMathLevel(profile.getMathLevel());
        MathChallengeGenerator.MathQuestion generated = generator.generate(vipLevel);
        MathChallengeTokenCodec.IssuedToken issued = tokenCodec.issueScoped(
                profile.getId(),
                generated.answer(),
                generated.level(),
                scopeForEpisode(episode.getId())
        );
        LocalDateTime expiresAtUtc = LocalDateTime.ofInstant(issued.expiresAt(), ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.SECONDS);
        return new VipChallengeResponse(
                issued.token(),
                episode.getId(),
                episode.getSeriesId(),
                generated.question(),
                generated.level(),
                expiresAtUtc,
                MathChallengeTokenCodec.TOKEN_TTL.toSeconds()
        );
    }

    // chấm đáp án mở khóa VIP bằng token ký HMAC 1 lần, đúng thì mở đúng tập này cho hết hôm nay
    @Transactional
    public UnlockOutcome submitEpisodeUnlock(String episodeId, MathChallengeAnswerRequest request) {
        Viewer viewer = currentViewer.require();
        if (!viewer.isChildProfile()) {
            throw new InvalidRequestException(CHILD_ONLY_MESSAGE);
        }
        Long profileId = viewer.profileId();
        rateLimiter.ensureNotLocked(profileId);

        Integer suppliedAnswer = request == null ? null : request.answer();
        if (suppliedAnswer == null) {
            throw new InvalidRequestException("Bé hãy nhập đáp án bằng số nhé.");
        }
        MathChallengeTokenCodec.DecodedScopedToken decoded = tokenCodec.decodeScoped(request.token())
                .orElseThrow(() -> new InvalidRequestException("Câu hỏi không hợp lệ hoặc đã hết hạn. Bé lấy câu mới nhé."));
        Instant nowInstant = clock.instant();
        if (decoded.profileId() != profileId
                || !decoded.expiresAt().isAfter(nowInstant)
                || !scopeForEpisode(episodeId).equals(decoded.scope())) {
            throw new InvalidRequestException("Câu hỏi đã hết hạn hoặc không thuộc tập phim này. Bé lấy câu mới nhé.");
        }
        if (isTokenUsed(decoded.tokenId(), nowInstant)) {
            throw new InvalidRequestException(TOKEN_USED_MESSAGE);
        }

        ChildProfile profile = childProfileRepository.findByIdAndUserIdForUpdate(profileId, viewer.userId())
                .orElseThrow(() -> new UnauthorizedException(LOGIN_REQUIRED));
        CatalogEpisode episode = catalogEpisodeRepository.findById(episodeId)
                .filter(catalogVisibility::isEpisodeVisible)
                .orElseThrow(() -> new NotFoundException(EPISODE_NOT_FOUND));
        if (!vipSeriesRepository.existsByProfileIdAndSeriesId(profile.getId(), episode.getSeriesId())) {
            throw new InvalidRequestException("Tập phim này không cần mở khóa VIP.");
        }
        if (decoded.level() < requiredVipMathLevel(profile.getMathLevel())) {
            throw new InvalidRequestException("Cấp độ bài toán đã thay đổi. Bé lấy câu hỏi mới nhé.");
        }

        LocalDate today = ScreenTimeSummary.todayVietnam(clock);
        if (suppliedAnswer != decoded.answer()) {
            rateLimiter.recordFailure(profileId);
            return new UnlockOutcome(
                    new VipUnlockResponse(false, false, episode.getId(), today),
                    "Chưa đúng rồi, bé thử tính lại nhé."
            );
        }

        if (!tryRedeemToken(decoded.tokenId(), decoded.expiresAt(), nowInstant)) {
            throw new InvalidRequestException(TOKEN_USED_MESSAGE);
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
                new VipUnlockResponse(true, true, episode.getId(), today),
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

    public void resetUsedTokens() {
        synchronized (usedTokensLock) {
            usedTokenIds.clear();
        }
    }

    private static String scopeForEpisode(String episodeId) {
        return "vip:" + episodeId;
    }

    private boolean isTokenUsed(String tokenId, Instant now) {
        synchronized (usedTokensLock) {
            pruneExpired(now);
            return usedTokenIds.containsKey(tokenId);
        }
    }

    private boolean tryRedeemToken(String tokenId, Instant expiresAt, Instant now) {
        synchronized (usedTokensLock) {
            pruneExpired(now);
            if (usedTokenIds.containsKey(tokenId)) {
                return false;
            }
            usedTokenIds.put(tokenId, expiresAt);
            return true;
        }
    }

    private void pruneExpired(Instant now) {
        Iterator<Map.Entry<String, Instant>> iterator = usedTokenIds.entrySet().iterator();
        while (iterator.hasNext()) {
            if (!iterator.next().getValue().isAfter(now)) {
                iterator.remove();
            }
        }
    }

    public record UnlockOutcome(VipUnlockResponse response, String message) {
    }
}
