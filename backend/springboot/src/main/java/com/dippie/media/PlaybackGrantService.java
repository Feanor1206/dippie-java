package com.dippie.media;

import com.dippie.catalog.domain.CatalogMediaAsset;
import com.dippie.catalog.domain.CatalogMediaAssetRepository;
import com.dippie.security.CurrentViewer;
import com.dippie.security.JwtProperties;
import com.dippie.security.JwtService;
import com.dippie.security.Viewer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlaybackGrantService {

    private final MediaAssetRepository mediaAssetRepository;
    private final CatalogMediaAssetRepository catalogMediaAssetRepository;
    private final PlaybackAccess playbackAccess;
    private final CurrentViewer currentViewer;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;

    public PlaybackGrantService(
            MediaAssetRepository mediaAssetRepository,
            CatalogMediaAssetRepository catalogMediaAssetRepository,
            PlaybackAccess playbackAccess,
            CurrentViewer currentViewer,
            JwtService jwtService,
            JwtProperties jwtProperties
    ) {
        this.mediaAssetRepository = mediaAssetRepository;
        this.catalogMediaAssetRepository = catalogMediaAssetRepository;
        this.playbackAccess = playbackAccess;
        this.currentViewer = currentViewer;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
    }

    @Transactional(readOnly = true)
    public PlaybackGrantResponse issueGrant(String assetId) {
        MediaAsset asset = mediaAssetRepository.findById(assetId).orElse(null);
        CatalogMediaAsset catalogAsset = catalogMediaAssetRepository.findById(assetId).orElse(null);
        Viewer viewer = currentViewer.find().orElse(null);
        playbackAccess.authorizeForGrant(assetId, asset, catalogAsset, viewer);

        String token = jwtService.issuePlaybackGrant(
                assetId,
                viewer == null ? null : viewer.username(),
                viewer == null ? null : viewer.role()
        );
        long expiresIn = jwtProperties.playbackGrantValidity().toSeconds();
        return new PlaybackGrantResponse(
                assetId,
                token,
                token,
                "/api/media/stream/" + assetId + "?grant=" + token,
                expiresIn,
                expiresIn
        );
    }
}
