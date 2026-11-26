package com.dippie.service.profile;

import com.dippie.dto.profile.ChildProfileScreenTimeView;
import com.dippie.dto.profile.ParentTimeGrantRequest;
import com.dippie.dto.profile.ScreenTimeHeartbeatRequest;
import com.dippie.dto.profile.ScreenTimeHeartbeatResponse;
import com.dippie.dto.profile.ScreenTimeStatusView;
import com.dippie.dto.profile.UpdateScreenTimeSettingsRequest;
import com.dippie.exception.InvalidRequestException;
import com.dippie.exception.NotFoundException;
import com.dippie.exception.ScreenTimeLockedException;
import com.dippie.exception.UnauthorizedException;
import com.dippie.model.profile.ChildProfile;
import com.dippie.model.profile.ScreenTimeLedgerEntry;
import com.dippie.model.profile.ScreenTimeLedgerKind;
import com.dippie.model.profile.ScreenTimeSummary;
import com.dippie.model.user.User;
import com.dippie.repository.profile.ChildProfileRepository;
import com.dippie.repository.profile.ScreenTimeLedgerRepository;
import com.dippie.repository.user.UserRepository;
import com.dippie.security.CurrentViewer;
import com.dippie.security.Viewer;
import com.dippie.service.play.PlayPlaybackGuard;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// quản lý hạn mức xem phim mỗi ngày cho hồ sơ trẻ em: đọc trạng thái, trừ heartbeat, kiểm tra khóa và thao tác phụ huynh
@Service
public class ScreenTimeService {

    private static final String LOGIN_REQUIRED = "Bạn cần đăng nhập để tiếp tục.";
    private static final String PARENT_MODE_REQUIRED = "Hãy bật chế độ hồ sơ trẻ em trước.";
    private static final String PROFILE_NOT_FOUND = "Không tìm thấy hồ sơ trẻ em này.";
    private static final String CHILD_MANAGE_FORBIDDEN = "Hồ sơ trẻ em không thể chỉnh cài đặt thời gian xem.";

    private final CurrentViewer currentViewer;
    private final UserRepository userRepository;
    private final ChildProfileRepository childProfileRepository;
    private final ScreenTimeLedgerRepository ledgerRepository;
    private final ParentModeService parentModeService;
    private final PlayPlaybackGuard playPlaybackGuard;
    private final Clock clock;

    public ScreenTimeService(
            CurrentViewer currentViewer,
            UserRepository userRepository,
            ChildProfileRepository childProfileRepository,
            ScreenTimeLedgerRepository ledgerRepository,
            ParentModeService parentModeService,
            PlayPlaybackGuard playPlaybackGuard,
            Clock clock
    ) {
        this.currentViewer = currentViewer;
        this.userRepository = userRepository;
        this.childProfileRepository = childProfileRepository;
        this.ledgerRepository = ledgerRepository;
        this.parentModeService = parentModeService;
        this.playPlaybackGuard = playPlaybackGuard;
        this.clock = clock;
    }

    // hồ sơ chính không bị giới hạn bao giờ, còn hồ sơ bé trả đủ số giây đã xem, thưởng và còn lại hôm nay
    @Transactional(readOnly = true)
    public ScreenTimeStatusView getCurrentStatus() {
        Viewer viewer = currentViewer.require();
        LocalDate today = ScreenTimeSummary.todayVietnam(clock);
        if (!viewer.isChildProfile()) {
            return ScreenTimeStatusView.unlimited(today);
        }
        ChildProfile profile = childProfileRepository.findByIdAndUserId(viewer.profileId(), viewer.userId())
                .orElseThrow(() -> new UnauthorizedException(LOGIN_REQUIRED));
        return ScreenTimeStatusView.from(summarize(profile, today));
    }

    // nhận nhịp tim 15 giây lúc bé đang xem, chặn trần 20s mỗi lần, bỏ qua nhịp sát nhau dưới 10s và không trừ âm
    @Transactional
    public ScreenTimeHeartbeatResponse recordHeartbeat(ScreenTimeHeartbeatRequest request) {
        Viewer viewer = currentViewer.require();
        LocalDate today = ScreenTimeSummary.todayVietnam(clock);
        if (!viewer.isChildProfile()) {
            ScreenTimeStatusView unlimited = ScreenTimeStatusView.unlimited(today);
            return new ScreenTimeHeartbeatResponse(false, 0, true, null, false, unlimited);
        }
        Integer rawSeconds = request == null ? null : request.seconds();
        if (rawSeconds == null || rawSeconds < 0) {
            throw new InvalidRequestException("Số giây đã xem cần là số nguyên không âm.");
        }
        ChildProfile profile = childProfileRepository.findByIdAndUserIdForUpdate(viewer.profileId(), viewer.userId())
                .orElseThrow(() -> new UnauthorizedException(LOGIN_REQUIRED));
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);

        ScreenTimeSummary before = summarize(profile, today);
        if (before.unlimited()) {
            if (rawSeconds > 0 && !playPlaybackGuard.isWatchPetBlocking(profile.getId(), now)) {
                playPlaybackGuard.onUnlimitedWatchHeartbeat(profile.getId(), Math.min(rawSeconds, 20), now);
            }
            ScreenTimeStatusView status = ScreenTimeStatusView.from(before);
            return new ScreenTimeHeartbeatResponse(false, 0, true, null, false, status);
        }
        if (rawSeconds == 0 || before.locked() || playPlaybackGuard.isWatchPetBlocking(profile.getId(), now)) {
            ScreenTimeStatusView status = ScreenTimeStatusView.from(before);
            return new ScreenTimeHeartbeatResponse(true, 0, true, before.remainingSeconds(), before.locked(), status);
        }

        // bỏ qua nhịp gửi quá dày (dưới 10 giây so với lượt WATCH gần nhất của hồ sơ này)
        var lastWatch = ledgerRepository.findTopByProfileIdAndKindOrderByCreatedAtDescIdDesc(
                profile.getId(),
                ScreenTimeLedgerKind.WATCH
        );
        if (lastWatch.isPresent()) {
            long elapsedSeconds = Duration.between(lastWatch.get().getCreatedAt(), now).getSeconds();
            if (elapsedSeconds < 10L) {
                ScreenTimeStatusView status = ScreenTimeStatusView.from(before);
                return new ScreenTimeHeartbeatResponse(true, 0, true, before.remainingSeconds(), before.locked(), status);
            }
        }

        // mỗi lần gọi trừ tối đa 20 giây và không vượt quá phần còn lại để khỏi kéo âm sang phút thưởng sau này
        int capped = Math.min(rawSeconds, 20);
        int credited = (int) Math.min(capped, before.remainingSeconds());
        if (credited > 0) {
            ledgerRepository.saveAndFlush(new ScreenTimeLedgerEntry(
                    profile.getId(),
                    today,
                    ScreenTimeLedgerKind.WATCH,
                    credited,
                    now
            ));
            playPlaybackGuard.onWatchHeartbeat(profile.getId(), credited, now);
        }

        ScreenTimeSummary after = summarize(profile, today);
        ScreenTimeStatusView status = ScreenTimeStatusView.from(after);
        return new ScreenTimeHeartbeatResponse(true, credited, credited == 0, after.remainingSeconds(), after.locked(), status);
    }

    // chỉ kiểm tra khóa hạn mức ngày (bỏ qua khi không giới hạn) để các service phát xếp đúng thứ tự khóa
    @Transactional(readOnly = true)
    public void requireBudgetUnlockedForPlayback(Viewer viewer) {
        if (viewer == null || !viewer.isChildProfile()) {
            return;
        }
        requireBudgetUnlockedForProfile(viewer.userId(), viewer.profileId());
    }

    @Transactional(readOnly = true)
    public void requireBudgetUnlockedForProfile(Long userId, Long profileId) {
        if (userId == null || profileId == null) {
            return;
        }
        ChildProfile profile = childProfileRepository.findByIdAndUserId(profileId, userId).orElse(null);
        if (profile == null) {
            return;
        }
        ScreenTimeSummary summary = summarize(profile, ScreenTimeSummary.todayVietnam(clock));
        if (summary.locked()) {
            throw new ScreenTimeLockedException("Hết giờ xem hôm nay rồi.");
        }
    }

    // kiểm tra cả khóa hạn mức ngày lẫn thú ảo (dùng cho nơi chỉ cần 1 lệnh chặn chung như phát rương)
    @Transactional(readOnly = true)
    public void requireUnlockedForPlayback(Viewer viewer) {
        if (viewer == null || !viewer.isChildProfile()) {
            return;
        }
        requireUnlockedForProfile(viewer.userId(), viewer.profileId());
    }

    // kiểm tra khóa theo cặp userId + profileId (dùng cả cho vé phát video đang mang claim pid)
    @Transactional(readOnly = true)
    public void requireUnlockedForProfile(Long userId, Long profileId) {
        if (userId == null || profileId == null) {
            return;
        }
        requireBudgetUnlockedForProfile(userId, profileId);
        playPlaybackGuard.requireWatchPetUnlocked(userId, profileId);
    }

    // trang cài đặt tài khoản của phụ huynh đọc tổng quan thời gian xem hôm nay của mọi hồ sơ bé
    @Transactional(readOnly = true)
    public List<ChildProfileScreenTimeView> listParentOverview() {
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
        LocalDate today = ScreenTimeSummary.todayVietnam(clock);
        List<Long> profileIds = profiles.stream().map(ChildProfile::getId).toList();
        Map<Long, Map<ScreenTimeLedgerKind, Long>> totalsByProfile = new HashMap<>();
        for (ScreenTimeLedgerRepository.ProfileKindTotal row : ledgerRepository.sumByProfileIdsAndUsageDay(profileIds, today)) {
            totalsByProfile
                    .computeIfAbsent(row.getProfileId(), ignored -> new EnumMap<>(ScreenTimeLedgerKind.class))
                    .put(row.getKind(), row.getTotalSeconds() == null ? 0L : row.getTotalSeconds());
        }
        return profiles.stream()
                .map(profile -> {
                    Map<ScreenTimeLedgerKind, Long> totals = totalsByProfile.getOrDefault(profile.getId(), Map.of());
                    ScreenTimeSummary summary = ScreenTimeSummary.of(
                            profile,
                            today,
                            totals.getOrDefault(ScreenTimeLedgerKind.WATCH, 0L),
                            totals.getOrDefault(ScreenTimeLedgerKind.REWARD, 0L)
                                    + totals.getOrDefault(ScreenTimeLedgerKind.LOOT, 0L),
                            totals.getOrDefault(ScreenTimeLedgerKind.PARENT_GRANT, 0L),
                            totals.getOrDefault(ScreenTimeLedgerKind.BOUNTY, 0L)
                    );
                    return ChildProfileScreenTimeView.of(profile, summary);
                })
                .toList();
    }

    // phụ huynh sửa hạn mức ngày (hoặc không giới hạn), mức toán, phút thưởng mỗi câu và trần thưởng ngày cho 1 bé (cần PIN)
    @Transactional
    public ChildProfileScreenTimeView updateSettings(Long profileId, UpdateScreenTimeSettingsRequest request) {
        Viewer viewer = currentViewer.requireMainProfile(CHILD_MANAGE_FORBIDDEN);
        User user = userRepository.findByIdForUpdate(viewer.userId())
                .orElseThrow(() -> new UnauthorizedException(LOGIN_REQUIRED));
        if (!user.isParentModeEnabled()) {
            throw new InvalidRequestException(PARENT_MODE_REQUIRED);
        }
        parentModeService.verifyParentPin(user, request == null ? null : request.pin());
        ChildProfile profile = childProfileRepository.findByIdAndUserIdForUpdate(profileId, user.getId())
                .orElseThrow(() -> new NotFoundException(PROFILE_NOT_FOUND));
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        profile.updateScreenTimeSettings(
                request == null ? null : request.dailyMinutes(),
                request == null ? null : request.unlimited(),
                request == null ? null : request.mathLevel(),
                request == null ? null : request.rewardMinutes(),
                request == null ? null : request.maxBonusMinutes(),
                now
        );
        ChildProfile saved = childProfileRepository.saveAndFlush(profile);
        return ChildProfileScreenTimeView.of(saved, summarize(saved, ScreenTimeSummary.todayVietnam(clock)));
    }

    // phụ huynh tặng thêm phút đột xuất trong ngày (vd cho thêm 15 phút), cần đúng mã PIN phụ huynh
    @Transactional
    public ChildProfileScreenTimeView grantExtraMinutes(Long profileId, ParentTimeGrantRequest request) {
        Viewer viewer = currentViewer.requireMainProfile(CHILD_MANAGE_FORBIDDEN);
        User user = userRepository.findByIdForUpdate(viewer.userId())
                .orElseThrow(() -> new UnauthorizedException(LOGIN_REQUIRED));
        if (!user.isParentModeEnabled()) {
            throw new InvalidRequestException(PARENT_MODE_REQUIRED);
        }
        parentModeService.verifyParentPin(user, request == null ? null : request.pin());
        Integer minutes = request == null ? null : request.minutes();
        if (minutes == null || minutes < 1 || minutes > 180) {
            throw new InvalidRequestException("Số phút cho thêm cần từ 1 đến 180 phút.");
        }
        ChildProfile profile = childProfileRepository.findByIdAndUserIdForUpdate(profileId, user.getId())
                .orElseThrow(() -> new NotFoundException(PROFILE_NOT_FOUND));
        LocalDate today = ScreenTimeSummary.todayVietnam(clock);
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        ledgerRepository.saveAndFlush(new ScreenTimeLedgerEntry(
                profile.getId(),
                today,
                ScreenTimeLedgerKind.PARENT_GRANT,
                minutes * 60,
                now
        ));
        return ChildProfileScreenTimeView.of(profile, summarize(profile, today));
    }

    // gom tổng các dòng sổ cái trong ngày của 1 hồ sơ bé (REWARD và LOOT đều tính vào trần thưởng ngày)
    public ScreenTimeSummary summarize(ChildProfile profile, LocalDate day) {
        long watched = 0L;
        long reward = 0L;
        long grant = 0L;
        long bounty = 0L;
        for (ScreenTimeLedgerRepository.KindTotal row : ledgerRepository.sumByProfileIdAndUsageDay(profile.getId(), day)) {
            long total = row.getTotalSeconds() == null ? 0L : row.getTotalSeconds();
            if (row.getKind() == ScreenTimeLedgerKind.WATCH) {
                watched = total;
            } else if (row.getKind() == ScreenTimeLedgerKind.REWARD || row.getKind() == ScreenTimeLedgerKind.LOOT) {
                reward += total;
            } else if (row.getKind() == ScreenTimeLedgerKind.PARENT_GRANT) {
                grant = total;
            } else if (row.getKind() == ScreenTimeLedgerKind.BOUNTY) {
                bounty = total;
            }
        }
        return ScreenTimeSummary.of(profile, day, watched, reward, grant, bounty);
    }
}
