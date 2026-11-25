package com.dippie.media;

import com.dippie.catalog.domain.CatalogMediaAsset;
import com.dippie.catalog.domain.CatalogMediaAssetRepository;
import com.dippie.common.error.NotFoundException;
import com.dippie.security.CurrentViewer;
import com.dippie.security.JwtService;
import com.dippie.security.Viewer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MediaStreamService {

    private static final String MISSING_FILE_MESSAGE = "Không tìm thấy tệp video.";
    private static final MediaType DEFAULT_MEDIA_TYPE = MediaType.parseMediaType("video/mp4");

    private final MediaAssetRepository mediaAssetRepository;
    private final CatalogMediaAssetRepository catalogMediaAssetRepository;
    private final PlaybackAccess playbackAccess;
    private final CurrentViewer currentViewer;
    private final JwtService jwtService;
    private final MediaFileResolver mediaFileResolver;

    public MediaStreamService(
            MediaAssetRepository mediaAssetRepository,
            CatalogMediaAssetRepository catalogMediaAssetRepository,
            PlaybackAccess playbackAccess,
            CurrentViewer currentViewer,
            JwtService jwtService,
            MediaFileResolver mediaFileResolver
    ) {
        this.mediaAssetRepository = mediaAssetRepository;
        this.catalogMediaAssetRepository = catalogMediaAssetRepository;
        this.playbackAccess = playbackAccess;
        this.currentViewer = currentViewer;
        this.jwtService = jwtService;
        this.mediaFileResolver = mediaFileResolver;
    }

    @Transactional(readOnly = true)
    public ResolvedMediaStream resolveStream(String assetId, String grant, String token) {
        MediaAsset asset = mediaAssetRepository.findById(assetId).orElse(null);
        CatalogMediaAsset catalogAsset = catalogMediaAssetRepository.findById(assetId).orElse(null);
        Viewer viewer = currentViewer.find().orElse(null);
        String effectiveToken = selectToken(grant, token);
        String grantRole = effectiveToken == null
                ? null
                : jwtService.playbackGrantRole(effectiveToken, assetId).orElse(null);

        MediaAsset authorized = playbackAccess.authorizeForStream(assetId, asset, catalogAsset, viewer, grantRole);
        Path filePath = mediaFileResolver.resolve(authorized)
                .orElseThrow(() -> new NotFoundException(MISSING_FILE_MESSAGE));
        long totalLength = readFileSize(filePath);
        MediaType mediaType = resolveMediaType(authorized.getMimeType());
        return new ResolvedMediaStream(filePath, totalLength, mediaType);
    }

    private static String selectToken(String grant, String token) {
        if (grant != null && !grant.isBlank()) {
            return grant.trim();
        }
        if (token != null && !token.isBlank()) {
            return token.trim();
        }
        return null;
    }

    private static long readFileSize(Path filePath) {
        try {
            return Files.size(filePath);
        } catch (IOException ex) {
            throw new NotFoundException(MISSING_FILE_MESSAGE);
        }
    }

    private static MediaType resolveMediaType(String mimeType) {
        if (mimeType == null || mimeType.isBlank()) {
            return DEFAULT_MEDIA_TYPE;
        }
        try {
            return MediaType.parseMediaType(mimeType);
        } catch (InvalidMediaTypeException ex) {
            return DEFAULT_MEDIA_TYPE;
        }
    }
}
