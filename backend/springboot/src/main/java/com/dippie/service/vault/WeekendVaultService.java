package com.dippie.service.vault;

import com.dippie.dto.profile.PinRequest;
import com.dippie.dto.vault.ChildProfileWeekendVaultView;
import com.dippie.dto.vault.WeekendVaultDayView;
import com.dippie.dto.vault.WeekendVaultStatusView;
import com.dippie.exception.InvalidRequestException;
import com.dippie.exception.NotFoundException;
import com.dippie.exception.UnauthorizedException;
import com.dippie.exception.WeekendVaultLockedException;
import com.dippie.model.media.MediaAsset;
import com.dippie.model.profile.ChildProfile;
import com.dippie.model.profile.ScreenTimeLedgerKind;
import com.dippie.model.profile.ScreenTimeSummary;
import com.dippie.model.user.User;
import com.dippie.model.vault.WeekendVaultOverride;
import com.dippie.repository.catalog.CatalogEpisodeRepository;
import com.dippie.repository.catalog.CatalogSeriesRepository;
import com.dippie.repository.profile.ChildProfileRepository;
import com.dippie.repository.profile.ScreenTimeLedgerRepository;
import com.dippie.repository.user.UserRepository;
import com.dippie.repository.vault.WeekendVaultOverrideRepository;
import com.dippie.security.CurrentViewer;
import com.dippie.security.Viewer;
import com.dippie.service.profile.ParentModeService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// quản lý két sắt phim cuối tuần: khóa phim lẻ thứ Hai đến thứ Sáu cho hồ sơ bé, tính Vé xem phim từ sổ cái và cho phụ huynh mở bằng PIN
@Service
public class WeekendVaultService {

    private static final String LOGIN_REQUIRED = "Bạn cần đăng nhập để tiếp tục.";
    private static final String PARENT_MODE_REQUIRED = "Hãy bật chế độ hồ sơ trẻ em trước.";
    private static final String PROFILE_NOT_FOUND = "Không tìm thấy hồ sơ trẻ em này.";

    private final CurrentViewer currentViewer;
    private final UserRepository userRepository;
    private final ChildProfileRepository childProfileRepository;
    private final ScreenTimeLedgerRepository ledgerRepository;
    private final WeekendVaultOverrideRepository overrideRepository;
    private final CatalogEpisodeRepository catalogEpisodeRepository;
    private final CatalogSeriesRepository catalogSeriesRepository;
    private final ParentModeService parentModeService;
    private final Clock clock;

    public WeekendVaultService(
            CurrentViewer currentViewer,
            UserRepository userRepository,
            ChildProfileRepository childProfileRepository,
            ScreenTimeLedgerRepository ledgerRepository,
            WeekendVaultOverrideRepository overrideRepository,
            CatalogEpisodeRepository catalogEpisodeRepository,
            CatalogSeriesRepository catalogSeriesRepository,
            ParentModeService parentModeService,
            Clock clock
    ) {
        this.currentViewer = currentViewer;
        this.userRepository = userRepository;
        this.childProfileRepository = childProfileRepository;
        this.ledgerRepository = ledgerRepository;
        this.overrideRepository = overrideRepository;
        this.catalogEpisodeRepository = catalogEpisodeRepository;
        this.catalogSeriesRepository = catalogSeriesRepository;
        this.parentModeService = parentModeService;
        this.clock = clock;
    }

    // trả trạng thái két sắt cho người xem hiện tại, hồ sơ chính luôn mở hoàn toàn
    @Transactional(readOnly = true)
    public WeekendVaultStatusView getCurrentStatus() {
        Viewer viewer = currentViewer.require();
        LocalDate today = ScreenTimeSummary.todayVietnam(clock);
        LocalDate monday = weekStart(today);
        boolean weekend = isWeekend(today);
        if (!viewer.isChildProfile()) {
            return WeekendVaultStatusView.unrestricted(today, monday, weekend);
        }
        ChildProfile profile = childProfileRepository.findByIdAndUserId(viewer.profileId(), viewer.userId())
                .orElseThrow(() -> new UnauthorizedException(LOGIN_REQUIRED));
        return evaluateProfile(profile, today);
    }

    // phụ huynh xem tiến độ Vé xem phim và trạng thái khóa của từng bé
    @Transactional(readOnly = true)
    public List<ChildProfileWeekendVaultView> listParentOverview() {
        Viewer viewer = currentViewer.requireMainProfile("Hồ sơ trẻ em không thể xem tổng quan phụ huynh.");
        User user = userRepository.findById(viewer.userId())
                .orElseThrow(() -> new UnauthorizedException(LOGIN_REQUIRED));
        if (!user.isParentModeEnabled()) {
            return List.of();
        }
        List<ChildProfile> profiles = childProfileRepository.findByUserIdOrderByCreatedAtAscIdAsc(user.getId());
        if (profiles.isEmpty()) {
            return List.of();
        }
        LocalDate today = ScreenTimeSummary.todayVietnam(clock);
        LocalDate monday = weekStart(today);
        LocalDate friday = monday.plusDays(4);
        List<Long> profileIds = profiles.stream().map(ChildProfile::getId).toList();

        Map<Long, Map<LocalDate, Map<ScreenTimeLedgerKind, DayAggregate>>> statsByProfile = new HashMap<>();
        for (ScreenTimeLedgerRepository.ProfileDayKindStats row :
                ledgerRepository.summarizeByProfileIdsAndUsageDayBetween(profileIds, monday, friday)) {
            statsByProfile
                    .computeIfAbsent(row.getProfileId(), ignored -> new HashMap<>())
                    .computeIfAbsent(row.getUsageDay(), ignored -> new EnumMap<>(ScreenTimeLedgerKind.class))
                    .put(row.getKind(), new DayAggregate(
                            row.getTotalSeconds() == null ? 0L : row.getTotalSeconds(),
                            row.getEntryCount() == null ? 0L : row.getEntryCount()
                    ));
        }

        Map<Long, Boolean> manualUnlockByProfile = new HashMap<>();
        for (WeekendVaultOverride override : overrideRepository.findByProfileIdInAndWeekStart(profileIds, monday)) {
            manualUnlockByProfile.put(override.getProfileId(), override.isUnlocked());
        }

        return profiles.stream()
                .map(profile -> {
                    WeekendVaultStatusView status = computeStatus(
                            profile,
                            today,
                            statsByProfile.getOrDefault(profile.getId(), Map.of()),
                            Boolean.TRUE.equals(manualUnlockByProfile.get(profile.getId()))
                    );
                    return ChildProfileWeekendVaultView.of(profile, status);
                })
                .toList();
    }

    // phụ huynh mở khóa thủ công bằng mã PIN cho tuần này (gọi được từ hồ sơ chính hoặc ngay trên máy ở kid mode)
    @Transactional
    public ChildProfileWeekendVaultView unlockManually(Long profileId, PinRequest request) {
        Viewer viewer = currentViewer.require();
        if (viewer.isChildProfile() && !viewer.profileId().equals(profileId)) {
            throw new NotFoundException(PROFILE_NOT_FOUND);
        }
        return updateManualOverride(viewer.userId(), profileId, request, true);
    }

    // phụ huynh khóa lại theo quy tắc tuần bình thường (chỉ hồ sơ chính gọi được)
    @Transactional
    public ChildProfileWeekendVaultView restoreLock(Long profileId, PinRequest request) {
        Viewer viewer = currentViewer.requireMainProfile("Hồ sơ trẻ em không thể khóa lại két sắt phim cuối tuần.");
        return updateManualOverride(viewer.userId(), profileId, request, false);
    }

    // kiểm tra chặn phim lẻ khi cấp vé phát hoặc stream, phim bộ hay người lớn/khách bỏ qua luôn
    @Transactional(readOnly = true)
    public void requireFilmUnlockedForAsset(
            Long userId,
            Long profileId,
            MediaAsset asset,
            List<CatalogEpisodeRepository.EpisodeWithParents> linkedEpisodes
    ) {
        if (userId == null || profileId == null || asset == null) {
            return;
        }
        if (!isStandaloneFilmAsset(asset, linkedEpisodes)) {
            return;
        }
        ChildProfile profile = childProfileRepository.findByIdAndUserId(profileId, userId).orElse(null);
        if (profile == null) {
            return;
        }
        WeekendVaultStatusView status = evaluateProfile(profile, ScreenTimeSummary.todayVietnam(clock));
        if (status.locked()) {
            throw new WeekendVaultLockedException(
                    "Két sắt phim cuối tuần đang khóa. Bé tích đủ 100% Vé xem phim để mở vào thứ Bảy và Chủ Nhật nhé."
            );
        }
    }

    private ChildProfileWeekendVaultView updateManualOverride(
            Long userId,
            Long profileId,
            PinRequest request,
            boolean unlocked
    ) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new UnauthorizedException(LOGIN_REQUIRED));
        if (!user.isParentModeEnabled()) {
            throw new InvalidRequestException(PARENT_MODE_REQUIRED);
        }
        parentModeService.verifyParentPin(user, request == null ? null : request.pin());
        ChildProfile profile = childProfileRepository.findByIdAndUserIdForUpdate(profileId, user.getId())
                .orElseThrow(() -> new NotFoundException(PROFILE_NOT_FOUND));

        LocalDate today = ScreenTimeSummary.todayVietnam(clock);
        LocalDate monday = weekStart(today);
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        WeekendVaultOverride override = overrideRepository.findByProfileIdAndWeekStart(profile.getId(), monday)
                .orElseGet(() -> new WeekendVaultOverride(profile.getId(), monday, unlocked, now));
        override.setUnlocked(unlocked, now);
        overrideRepository.saveAndFlush(override);
        return ChildProfileWeekendVaultView.of(profile, evaluateProfile(profile, today));
    }

    private WeekendVaultStatusView evaluateProfile(ChildProfile profile, LocalDate today) {
        LocalDate monday = weekStart(today);
        LocalDate friday = monday.plusDays(4);
        Map<LocalDate, Map<ScreenTimeLedgerKind, DayAggregate>> dayStats = new HashMap<>();
        for (ScreenTimeLedgerRepository.DayKindStats row :
                ledgerRepository.summarizeByProfileIdAndUsageDayBetween(profile.getId(), monday, friday)) {
            dayStats.computeIfAbsent(row.getUsageDay(), ignored -> new EnumMap<>(ScreenTimeLedgerKind.class))
                    .put(row.getKind(), new DayAggregate(
                            row.getTotalSeconds() == null ? 0L : row.getTotalSeconds(),
                            row.getEntryCount() == null ? 0L : row.getEntryCount()
                    ));
        }
        boolean manualUnlocked = overrideRepository.findByProfileIdAndWeekStart(profile.getId(), monday)
                .map(WeekendVaultOverride::isUnlocked)
                .orElse(false);
        return computeStatus(profile, today, dayStats, manualUnlocked);
    }

    private WeekendVaultStatusView computeStatus(
            ChildProfile profile,
            LocalDate today,
            Map<LocalDate, Map<ScreenTimeLedgerKind, DayAggregate>> statsByDay,
            boolean manualUnlocked
    ) {
        LocalDate monday = weekStart(today);
        boolean weekend = isWeekend(today);
        List<WeekendVaultDayView> days = new ArrayList<>(5);
        int rawPercent = 0;

        for (int offset = 0; offset < 5; offset++) {
            LocalDate day = monday.plusDays(offset);
            boolean ended = day.isBefore(today);
            Map<ScreenTimeLedgerKind, DayAggregate> kinds = statsByDay.getOrDefault(day, Map.of());
            long watched = secondsOf(kinds, ScreenTimeLedgerKind.WATCH);
            long reward = secondsOf(kinds, ScreenTimeLedgerKind.REWARD) + secondsOf(kinds, ScreenTimeLedgerKind.LOOT);
            long grant = secondsOf(kinds, ScreenTimeLedgerKind.PARENT_GRANT);
            long bounty = secondsOf(kinds, ScreenTimeLedgerKind.BOUNTY);
            long mathRewardCount = countOf(kinds, ScreenTimeLedgerKind.REWARD);

            // cuối ngày còn dư giờ và có xem thì cộng 20% vào Vé xem phim
            boolean hasRemaining = profile.isUnlimited()
                    || (profile.getDailyMinutes() * 60L + reward + grant + bounty - watched) > 0L;
            boolean stoppedInTime = ended && watched > 0L && hasRemaining;
            boolean mathGoalMet = !day.isAfter(today) && mathRewardCount >= 3L;
            int earnedPercent = (stoppedInTime ? 20 : 0) + (mathGoalMet ? 20 : 0);
            rawPercent += earnedPercent;

            days.add(new WeekendVaultDayView(
                    day,
                    day.getDayOfWeek().getValue(),
                    ended,
                    stoppedInTime,
                    mathRewardCount,
                    mathGoalMet,
                    earnedPercent
            ));
        }

        int progressPercent = Math.min(100, rawPercent);
        boolean unlocked = manualUnlocked || (weekend && progressPercent >= 100);
        return new WeekendVaultStatusView(
                true,
                profile.getId(),
                today,
                monday,
                weekend,
                progressPercent,
                unlocked,
                manualUnlocked,
                !unlocked,
                List.copyOf(days)
        );
    }

    // phim lẻ là mục movies không nằm trong bảng tập/bộ phim, hoặc tập danh mục mang kind = movie
    private boolean isStandaloneFilmAsset(
            MediaAsset asset,
            List<CatalogEpisodeRepository.EpisodeWithParents> linkedEpisodes
    ) {
        String movieId = asset.getMovieId();
        if (movieId != null
                && !catalogEpisodeRepository.existsById(movieId)
                && !catalogSeriesRepository.existsById(movieId)) {
            return true;
        }
        if (linkedEpisodes == null || linkedEpisodes.isEmpty()) {
            return false;
        }
        List<CatalogEpisodeRepository.EpisodeWithParents> published = linkedEpisodes.stream()
                .filter(CatalogEpisodeRepository.EpisodeWithParents::isPublished)
                .toList();
        return !published.isEmpty()
                && published.stream().allMatch(ep -> "movie".equalsIgnoreCase(ep.episode().getKind()));
    }

    private static long secondsOf(Map<ScreenTimeLedgerKind, DayAggregate> kinds, ScreenTimeLedgerKind kind) {
        DayAggregate aggregate = kinds.get(kind);
        return aggregate == null ? 0L : Math.max(0L, aggregate.seconds());
    }

    private static long countOf(Map<ScreenTimeLedgerKind, DayAggregate> kinds, ScreenTimeLedgerKind kind) {
        DayAggregate aggregate = kinds.get(kind);
        return aggregate == null ? 0L : Math.max(0L, aggregate.count());
    }

    public static LocalDate weekStart(LocalDate day) {
        return day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    public static boolean isWeekend(LocalDate day) {
        DayOfWeek dow = day.getDayOfWeek();
        return dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;
    }

    private record DayAggregate(long seconds, long count) {
    }
}
