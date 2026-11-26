package com.dippie.service.media;

import com.dippie.dto.media.MediaAssetView;
import com.dippie.dto.media.PlaybackGrantResponse;
import com.dippie.model.media.MediaAsset;
import com.dippie.repository.catalog.CatalogEpisodeRepository;
import com.dippie.repository.catalog.CatalogMediaAssetRepository;
import com.dippie.repository.media.MediaAssetRepository;
import com.dippie.security.ActiveViewerLookup;
import com.dippie.security.CurrentViewer;
import com.dippie.security.JwtProperties;
import com.dippie.security.JwtService;
import com.dippie.security.Viewer;
import com.dippie.service.play.PlayPlaybackGuard;
import com.dippie.service.profile.ScreenTimeService;
import com.dippie.service.vault.WeekendVaultService;
import com.dippie.service.vip.VipTitleService;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// check quyền xem với file trên đĩa rồi cấp vé phát JWT ngắn hạn để player gắn lên URL stream
@Service
public class PlaybackGrantService {

    private final MediaAssetRepository mediaAssetRepository;
    private final CatalogMediaAssetRepository catalogMediaAssetRepository;
    private final CatalogEpisodeRepository catalogEpisodeRepository;
    private final PlaybackAccess playbackAccess;
    private final MediaFileResolver mediaFiles;
    private final CurrentViewer currentViewer;
    private final ActiveViewerLookup activeViewerLookup;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final ScreenTimeService screenTimeService;
    private final WeekendVaultService weekendVaultService;
    private final VipTitleService vipTitleService;
    private final PlayPlaybackGuard playPlaybackGuard;
    private final TransactionTemplate reads;

    public PlaybackGrantService(MediaAssetRepository mediaAssetRepository,
            CatalogMediaAssetRepository catalogMediaAssetRepository,
            CatalogEpisodeRepository catalogEpisodeRepository,
            PlaybackAccess playbackAccess,
            MediaFileResolver mediaFiles, CurrentViewer currentViewer, ActiveViewerLookup activeViewerLookup,
            JwtService jwtService, JwtProperties jwtProperties, ScreenTimeService screenTimeService,
            WeekendVaultService weekendVaultService, VipTitleService vipTitleService,
            PlayPlaybackGuard playPlaybackGuard,
            PlatformTransactionManager transactions) {
        this.mediaAssetRepository = mediaAssetRepository;
        this.catalogMediaAssetRepository = catalogMediaAssetRepository;
        this.catalogEpisodeRepository = catalogEpisodeRepository;
        this.playbackAccess = playbackAccess;
        this.mediaFiles = mediaFiles;
        this.currentViewer = currentViewer;
        this.activeViewerLookup = activeViewerLookup;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.screenTimeService = screenTimeService;
        this.weekendVaultService = weekendVaultService;
        this.vipTitleService = vipTitleService;
        this.playPlaybackGuard = playPlaybackGuard;
        this.reads = new TransactionTemplate(transactions);
        this.reads.setReadOnly(true);
    }

    // gói phần check DB vào transaction chỉ đọc ngắn rồi mới ra ngoài kiểm tra file trên đĩa với ký JWT
    public PlaybackGrantResponse issueGrant(String assetId) {
        Viewer viewer = currentViewer.find().orElse(null);
        AuthorizedGrant authorized = reads.execute(status -> {
            MediaAsset asset = mediaAssetRepository.findById(assetId).orElse(null);
            var catalogAsset = catalogMediaAssetRepository.findById(assetId).orElse(null);
            List<CatalogEpisodeRepository.EpisodeWithParents> linkedEpisodes = catalogAsset == null
                    ? List.of()
                    : catalogEpisodeRepository.findWithParentsByMediaAssetId(assetId);
            boolean hasPublishedParent = linkedEpisodes.stream()
                    .anyMatch(CatalogEpisodeRepository.EpisodeWithParents::isPublished);
            MediaAsset allowed = playbackAccess.authorizeForGrant(assetId, asset, catalogAsset, hasPublishedParent, viewer);
            // kiểm tra khóa theo thứ tự: hạn mức ngày, két sắt cuối tuần, tập VIP, thú ảo nghỉ mắt rồi ôn tập qua tập
            screenTimeService.requireBudgetUnlockedForPlayback(viewer);
            if (viewer != null && viewer.isChildProfile()) {
                weekendVaultService.requireFilmUnlockedForAsset(viewer.userId(), viewer.profileId(), allowed, linkedEpisodes);
                vipTitleService.requireAssetUnlockedForProfile(viewer.userId(), viewer.profileId(), linkedEpisodes);
                playPlaybackGuard.requireWatchPetUnlocked(viewer.userId(), viewer.profileId());
                List<String> episodeIds = linkedEpisodes.stream()
                        .filter(CatalogEpisodeRepository.EpisodeWithParents::isPublished)
                        .map(row -> row.episode().getId())
                        .toList();
                playPlaybackGuard.requireEpisodesUnlocked(viewer, episodeIds);
            }
            long tokenVersion = viewer == null
                    ? 0L
                    : activeViewerLookup.findActiveTokenVersion(viewer.userId()).orElse(0L);
            return new AuthorizedGrant(allowed, tokenVersion);
        });
        mediaFiles.requireFile(authorized.asset());

        // nhét userId, tokenVersion và profileId (nếu là bé) vào vé phát để hết giờ hay logout là cắt ngay
        com.dippie.model.user.Role grantRole = viewer == null
                ? null
                : (viewer.isChildProfile() ? com.dippie.model.user.Role.ROLE_USER : viewer.role());
        String token = jwtService.issuePlaybackGrant(
                assetId,
                viewer == null ? null : viewer.userId(),
                authorized.tokenVersion(),
                grantRole,
                viewer == null ? null : viewer.profileId()
        );
        long expiresIn = jwtProperties.playbackGrantValidity().toSeconds();
        return new PlaybackGrantResponse(assetId, token, token,
                MediaAssetView.STREAM_PATH_PREFIX + assetId + "?grant=" + token, expiresIn, expiresIn);
    }

    // gói media đã duyệt kèm tokenVersion để mang ra ngoài transaction DB
    private record AuthorizedGrant(MediaAsset asset, long tokenVersion) {
    }
}
