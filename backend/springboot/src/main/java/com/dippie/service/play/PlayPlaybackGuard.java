package com.dippie.service.play;

import com.dippie.exception.PlayFeatureLockedException;
import com.dippie.model.play.ChildEpisodeRecapGate;
import com.dippie.model.play.ChildPlaySettings;
import com.dippie.model.play.ChildPlayState;
import com.dippie.repository.play.ChildEpisodeRecapGateRepository;
import com.dippie.repository.play.ChildPlaySettingsRepository;
import com.dippie.repository.play.ChildPlayStateRepository;
import com.dippie.repository.profile.ChildProfileRepository;
import com.dippie.security.Viewer;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;

// gom phần kiểm tra khóa phát video của thú ảo và ôn tập qua tập, cùng nhịp cập nhật từ heartbeat
@Service
public class PlayPlaybackGuard {

    private final ChildProfileRepository childProfileRepository;
    private final ChildPlaySettingsRepository settingsRepository;
    private final ChildPlayStateRepository stateRepository;
    private final ChildEpisodeRecapGateRepository recapGateRepository;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public PlayPlaybackGuard(
            ChildProfileRepository childProfileRepository,
            ChildPlaySettingsRepository settingsRepository,
            ChildPlayStateRepository stateRepository,
            ChildEpisodeRecapGateRepository recapGateRepository,
            Clock clock
    ) {
        this.childProfileRepository = childProfileRepository;
        this.settingsRepository = settingsRepository;
        this.stateRepository = stateRepository;
        this.recapGateRepository = recapGateRepository;
        this.clock = clock;
    }

    // kiểm tra thú ảo có đang nghỉ mắt hay ngất không để heartbeat khỏi trừ oan phút xem của bé
    @Transactional(readOnly = true)
    public boolean isWatchPetBlocking(Long profileId, LocalDateTime now) {
        ChildPlaySettings settings = settingsRepository.findById(profileId)
                .orElseGet(() -> ChildPlaySettings.defaults(profileId, now));
        if (!settings.isWatchPetEnabled()) {
            return false;
        }
        ChildPlayState state = stateRepository.findById(profileId).orElse(null);
        if (state == null) {
            return false;
        }
        if (state.isRestingAt(now)) {
            return true;
        }
        // đã qua giờ restLockUntil nghĩa là vừa ngủ dậy rồi, không còn khóa nữa
        if (state.getRestLockUntil() != null && !now.isBefore(state.getRestLockUntil())) {
            return false;
        }
        return state.isPetFainted();
    }

    // cộng giây xem thật vào đồng hồ xem liên tục của thú ảo và đồng hồ chờ rương báu trong cùng transaction heartbeat
    @Transactional
    public void onWatchHeartbeat(Long profileId, int creditedSeconds, LocalDateTime now) {
        if (creditedSeconds <= 0) {
            return;
        }
        ChildPlaySettings settings = settingsRepository.findById(profileId)
                .orElseGet(() -> ChildPlaySettings.defaults(profileId, now));
        if (!settings.isWatchPetEnabled() && !settings.isLootDropEnabled()) {
            return;
        }
        ChildPlayState state = stateRepository.findById(profileId)
                .orElseGet(() -> new ChildPlayState(profileId, randomLootThreshold(), now));
        state.recordWatchSeconds(creditedSeconds, settings, now);
        stateRepository.saveAndFlush(state);
    }

    // ghi nhận nhịp xem cho hồ sơ không giới hạn phút ngày, vẫn chặn nhịp dày dưới 10 giây
    @Transactional
    public void onUnlimitedWatchHeartbeat(Long profileId, int cappedSeconds, LocalDateTime now) {
        if (cappedSeconds <= 0) {
            return;
        }
        ChildPlaySettings settings = settingsRepository.findById(profileId)
                .orElseGet(() -> ChildPlaySettings.defaults(profileId, now));
        if (!settings.isWatchPetEnabled() && !settings.isLootDropEnabled()) {
            return;
        }
        ChildPlayState state = stateRepository.findById(profileId)
                .orElseGet(() -> new ChildPlayState(profileId, randomLootThreshold(), now));
        if (state.getLastWatchHeartbeatAt() != null
                && Duration.between(state.getLastWatchHeartbeatAt(), now).getSeconds() < 10L) {
            return;
        }
        state.recordWatchSeconds(cappedSeconds, settings, now);
        stateRepository.saveAndFlush(state);
    }

    // chặn grant/stream/progress bằng 423 nếu thú ảo đang nghỉ mắt hoặc đã ngất mà bé chưa xử lý
    @Transactional(readOnly = true)
    public void requireWatchPetUnlocked(Long userId, Long profileId) {
        if (userId == null || profileId == null) {
            return;
        }
        if (!childProfileRepository.existsByIdAndUserId(profileId, userId)) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        ChildPlaySettings settings = settingsRepository.findById(profileId)
                .orElseGet(() -> ChildPlaySettings.defaults(profileId, now));
        if (!settings.isWatchPetEnabled()) {
            return;
        }
        ChildPlayState state = stateRepository.findById(profileId).orElse(null);
        if (state == null) {
            return;
        }
        if (state.isRestingAt(now)) {
            long remaining = state.restRemainingSecondsAt(now);
            throw new PlayFeatureLockedException(
                    "WATCH_PET_REST",
                    "Thú ảo đang ngủ nghỉ mắt rồi, bé chờ hết giờ nghỉ nhé.",
                    remaining
            );
        }
        if (state.getRestLockUntil() != null && !now.isBefore(state.getRestLockUntil())) {
            return;
        }
        if (state.isPetFainted()) {
            throw new PlayFeatureLockedException(
                    "WATCH_PET_FAINT",
                    "Thú ảo mệt xỉu rồi! Bé cho thú ngủ hoặc mua thuốc hồi máu để xem tiếp nhé."
            );
        }
    }

    // chặn grant/stream/progress của tập kế tiếp nếu bé vừa xem xong tập trước mà chưa trả lời xong ôn tập
    @Transactional(readOnly = true)
    public void requireEpisodesUnlocked(Viewer viewer, Collection<String> episodeIds) {
        if (viewer == null || !viewer.isChildProfile()) {
            return;
        }
        requireEpisodesUnlocked(viewer.userId(), viewer.profileId(), episodeIds);
    }

    @Transactional(readOnly = true)
    public void requireEpisodesUnlocked(Long userId, Long profileId, Collection<String> episodeIds) {
        if (userId == null || profileId == null || episodeIds == null || episodeIds.isEmpty()) {
            return;
        }
        if (!childProfileRepository.existsByIdAndUserId(profileId, userId)) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        ChildPlaySettings settings = settingsRepository.findById(profileId)
                .orElseGet(() -> ChildPlaySettings.defaults(profileId, now));
        if (!settings.isRecapToNextEnabled()) {
            return;
        }
        List<ChildEpisodeRecapGate> locked = recapGateRepository.findByProfileIdAndTargetEpisodeIdInAndUnlockedFalse(
                profileId,
                episodeIds
        );
        if (!locked.isEmpty()) {
            throw new PlayFeatureLockedException(
                    "RECAP_REQUIRED",
                    "Bé trả lời nhanh câu hỏi ôn tập của tập trước để mở khóa tập này nhé."
            );
        }
    }

    public int randomLootThreshold() {
        return ChildPlayState.LOOT_MIN_THRESHOLD_SECONDS
                + random.nextInt(ChildPlayState.LOOT_MAX_THRESHOLD_SECONDS - ChildPlayState.LOOT_MIN_THRESHOLD_SECONDS + 1);
    }
}
