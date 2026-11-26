package com.dippie.service.play;

import com.dippie.dto.play.ChildProfilePlaySettingsView;
import com.dippie.dto.play.UpdatePlaySettingsRequest;
import com.dippie.exception.InvalidRequestException;
import com.dippie.exception.NotFoundException;
import com.dippie.exception.UnauthorizedException;
import com.dippie.model.play.ChildPlaySettings;
import com.dippie.model.profile.ChildProfile;
import com.dippie.model.user.User;
import com.dippie.repository.play.ChildEpisodeRecapGateRepository;
import com.dippie.repository.play.ChildPlaySettingsRepository;
import com.dippie.repository.play.ChildPlayStateRepository;
import com.dippie.repository.profile.ChildProfileRepository;
import com.dippie.repository.user.UserRepository;
import com.dippie.security.CurrentViewer;
import com.dippie.security.Viewer;
import com.dippie.service.profile.ParentModeService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

// phụ huynh đọc và chỉnh cài đặt Dieppie, rương báu và ôn tập qua tập cho từng bé (cần PIN), kiêm dọn dữ liệu khi xóa hồ sơ
@Service
public class PlaySettingsService {

    private static final String LOGIN_REQUIRED = "Bạn cần đăng nhập để tiếp tục.";
    private static final String CHILD_MANAGE_FORBIDDEN = "Hồ sơ trẻ em không thể chỉnh cài đặt tính năng xem phim.";

    private final CurrentViewer currentViewer;
    private final UserRepository userRepository;
    private final ChildProfileRepository childProfileRepository;
    private final ChildPlaySettingsRepository settingsRepository;
    private final ChildPlayStateRepository stateRepository;
    private final ChildEpisodeRecapGateRepository recapGateRepository;
    private final ParentModeService parentModeService;
    private final Clock clock;

    public PlaySettingsService(
            CurrentViewer currentViewer,
            UserRepository userRepository,
            ChildProfileRepository childProfileRepository,
            ChildPlaySettingsRepository settingsRepository,
            ChildPlayStateRepository stateRepository,
            ChildEpisodeRecapGateRepository recapGateRepository,
            ParentModeService parentModeService,
            Clock clock
    ) {
        this.currentViewer = currentViewer;
        this.userRepository = userRepository;
        this.childProfileRepository = childProfileRepository;
        this.settingsRepository = settingsRepository;
        this.stateRepository = stateRepository;
        this.recapGateRepository = recapGateRepository;
        this.parentModeService = parentModeService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ChildProfilePlaySettingsView> listParentSettings() {
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
        List<Long> ids = profiles.stream().map(ChildProfile::getId).toList();
        Map<Long, ChildPlaySettings> byProfile = settingsRepository.findByProfileIdIn(ids).stream()
                .collect(Collectors.toMap(ChildPlaySettings::getProfileId, Function.identity()));
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        return profiles.stream()
                .map(profile -> ChildProfilePlaySettingsView.of(
                        profile,
                        byProfile.getOrDefault(profile.getId(), ChildPlaySettings.defaults(profile.getId(), now))
                ))
                .toList();
    }

    // phụ huynh cập nhật cài đặt 3 tính năng xem phim cho 1 bé, bắt buộc đúng mã PIN phụ huynh
    @Transactional
    public ChildProfilePlaySettingsView updateParentSettings(Long profileId, UpdatePlaySettingsRequest request) {
        Viewer viewer = currentViewer.requireMainProfile(CHILD_MANAGE_FORBIDDEN);
        User user = userRepository.findByIdForUpdate(viewer.userId())
                .orElseThrow(() -> new UnauthorizedException(LOGIN_REQUIRED));
        if (!user.isParentModeEnabled()) {
            throw new InvalidRequestException("Hãy bật chế độ hồ sơ trẻ em trước.");
        }
        parentModeService.verifyParentPin(user, request == null ? null : request.pin());
        ChildProfile profile = childProfileRepository.findByIdAndUserIdForUpdate(profileId, user.getId())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy hồ sơ trẻ em này."));
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        ChildPlaySettings settings = settingsRepository.findById(profile.getId())
                .orElseGet(() -> ChildPlaySettings.defaults(profile.getId(), now));
        settings.update(
                request == null ? null : request.watchPetEnabled(),
                request == null ? null : request.watchPetFaintMinutes(),
                request == null ? null : request.watchPetRestMinutes(),
                request == null ? null : request.lootDropEnabled(),
                request == null ? null : request.recapToNextEnabled(),
                now
        );
        ChildPlaySettings saved = settingsRepository.saveAndFlush(settings);
        return ChildProfilePlaySettingsView.of(profile, saved);
    }

    @Transactional(readOnly = true)
    public ChildPlaySettings loadEffectiveSettings(Long profileId) {
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        return settingsRepository.findById(profileId)
                .orElseGet(() -> ChildPlaySettings.defaults(profileId, now));
    }

    @Transactional
    public void deleteForProfile(Long profileId) {
        recapGateRepository.deleteByProfileId(profileId);
        stateRepository.deleteByProfileId(profileId);
        settingsRepository.deleteByProfileId(profileId);
    }

    @Transactional
    public void deleteForUser(Long userId) {
        recapGateRepository.deleteByUserId(userId);
        stateRepository.deleteByUserId(userId);
        settingsRepository.deleteByUserId(userId);
    }
}
