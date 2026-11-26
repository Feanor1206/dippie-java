package com.dippie.service.play;

import com.dippie.dto.play.LootStatusView;
import com.dippie.dto.play.PlayFeaturesStatusResponse;
import com.dippie.dto.play.PlaySettingsView;
import com.dippie.dto.play.RecapStatusView;
import com.dippie.dto.play.WatchPetStatusView;
import com.dippie.exception.UnauthorizedException;
import com.dippie.model.play.ChildPlaySettings;
import com.dippie.model.profile.ChildProfile;
import com.dippie.repository.profile.ChildProfileRepository;
import com.dippie.security.CurrentViewer;
import com.dippie.security.Viewer;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

// gom trạng thái cả 3 tính năng xem phim trẻ em vào 1 response gọn cho trình phát
@Service
public class PlayFeaturesQueryService {

    private final CurrentViewer currentViewer;
    private final ChildProfileRepository childProfileRepository;
    private final PlaySettingsService playSettingsService;
    private final WatchPetService watchPetService;
    private final LootDropService lootDropService;
    private final RecapQuizService recapQuizService;
    private final Clock clock;

    public PlayFeaturesQueryService(
            CurrentViewer currentViewer,
            ChildProfileRepository childProfileRepository,
            PlaySettingsService playSettingsService,
            WatchPetService watchPetService,
            LootDropService lootDropService,
            RecapQuizService recapQuizService,
            Clock clock
    ) {
        this.currentViewer = currentViewer;
        this.childProfileRepository = childProfileRepository;
        this.playSettingsService = playSettingsService;
        this.watchPetService = watchPetService;
        this.lootDropService = lootDropService;
        this.recapQuizService = recapQuizService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PlayFeaturesStatusResponse getStatus(String seriesId) {
        Viewer viewer = currentViewer.require();
        if (!viewer.isChildProfile()) {
            return PlayFeaturesStatusResponse.inactive();
        }
        ChildProfile profile = childProfileRepository.findByIdAndUserId(viewer.profileId(), viewer.userId())
                .orElseThrow(() -> new UnauthorizedException("Bạn cần đăng nhập để tiếp tục."));
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        ChildPlaySettings settings = playSettingsService.loadEffectiveSettings(profile.getId());
        WatchPetStatusView watchPet = watchPetService.buildStatus(profile.getId(), settings, now);
        LootStatusView loot = lootDropService.buildStatus(profile.getId(), settings, now);
        RecapStatusView recap = recapQuizService.buildStatus(profile.getId(), settings, seriesId);
        return new PlayFeaturesStatusResponse(
                true,
                PlaySettingsView.from(settings),
                watchPet,
                loot,
                recap
        );
    }
}
