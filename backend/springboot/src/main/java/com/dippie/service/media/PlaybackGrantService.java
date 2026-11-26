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

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// Kiểm tra quyền xem cùng tệp vật lý rồi phát hành vé phát JWT ngắn hạn để trình phát gắn lên URL stream.
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
    private final TransactionTemplate reads;

    public PlaybackGrantService(MediaAssetRepository mediaAssetRepository,
            CatalogMediaAssetRepository catalogMediaAssetRepository,
            CatalogEpisodeRepository catalogEpisodeRepository,
            PlaybackAccess playbackAccess,
            MediaFileResolver mediaFiles, CurrentViewer currentViewer, ActiveViewerLookup activeViewerLookup,
            JwtService jwtService, JwtProperties jwtProperties, PlatformTransactionManager transactions) {
        this.mediaAssetRepository = mediaAssetRepository;
        this.catalogMediaAssetRepository = catalogMediaAssetRepository;
        this.catalogEpisodeRepository = catalogEpisodeRepository;
        this.playbackAccess = playbackAccess;
        this.mediaFiles = mediaFiles;
        this.currentViewer = currentViewer;
        this.activeViewerLookup = activeViewerLookup;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.reads = new TransactionTemplate(transactions);
        this.reads.setReadOnly(true);
    }

    // Tách phần kiểm tra CSDL vào giao dịch chỉ đọc ngắn rồi mới kiểm tra tệp trên đĩa và ký JWT.
    public PlaybackGrantResponse issueGrant(String assetId) {
        Viewer viewer = currentViewer.find().orElse(null);
        AuthorizedGrant authorized = reads.execute(status -> {
            MediaAsset asset = mediaAssetRepository.findById(assetId).orElse(null);
            var catalogAsset = catalogMediaAssetRepository.findById(assetId).orElse(null);
            boolean hasPublishedParent = catalogAsset != null
                    && catalogEpisodeRepository.findWithParentsByMediaAssetId(assetId).stream()
                            .anyMatch(CatalogEpisodeRepository.EpisodeWithParents::isPublished);
            MediaAsset allowed = playbackAccess.authorizeForGrant(assetId, asset, catalogAsset, hasPublishedParent, viewer);
            long tokenVersion = viewer == null
                    ? 0L
                    : activeViewerLookup.findActiveTokenVersion(viewer.userId()).orElse(0L);
            return new AuthorizedGrant(allowed, tokenVersion);
        });
        mediaFiles.requireFile(authorized.asset());

        // Gắn userId và tokenVersion vào vé phát (không nhét username) để khi người dùng đăng xuất hoặc bị khóa là mất quyền ngay.
        String token = jwtService.issuePlaybackGrant(
                assetId,
                viewer == null ? null : viewer.userId(),
                authorized.tokenVersion(),
                viewer == null ? null : viewer.role()
        );
        long expiresIn = jwtProperties.playbackGrantValidity().toSeconds();
        return new PlaybackGrantResponse(assetId, token, token,
                MediaAssetView.STREAM_PATH_PREFIX + assetId + "?grant=" + token, expiresIn, expiresIn);
    }

    // Gom tài nguyên đã duyệt cùng tokenVersion hiện tại để mang ra khỏi giao dịch CSDL.
    private record AuthorizedGrant(MediaAsset asset, long tokenVersion) {
    }
}
