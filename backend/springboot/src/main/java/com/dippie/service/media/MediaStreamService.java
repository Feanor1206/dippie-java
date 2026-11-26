package com.dippie.service.media;

import com.dippie.dto.media.MediaAssetView;
import com.dippie.dto.media.MediaDescriptorView;
import com.dippie.exception.NotFoundException;
import com.dippie.model.catalog.CatalogEpisode;
import com.dippie.model.catalog.CatalogMediaAsset;
import com.dippie.model.catalog.CatalogSeason;
import com.dippie.model.catalog.CatalogSeries;
import com.dippie.model.media.MediaAsset;
import com.dippie.model.media.ResolvedMediaStream;
import com.dippie.model.movie.Movie;
import com.dippie.repository.catalog.CatalogEpisodeRepository;
import com.dippie.repository.catalog.CatalogMediaAssetRepository;
import com.dippie.repository.media.MediaAssetRepository;
import com.dippie.security.ActiveViewerLookup;
import com.dippie.security.CurrentViewer;
import com.dippie.security.JwtService;
import com.dippie.security.PlaybackPrincipal;
import com.dippie.security.Viewer;
import com.dippie.service.movie.MovieVisibility;

import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

// Facade điều phối khâu kiểm tra quyền, kiểm tra xuất bản danh mục và mở tệp video cho MediaStreamController.
// Mình tách giao dịch CSDL chỉ đọc ra khỏi bước đụng ổ đĩa để kết nối CSDL không bị giữ trong lúc tìm tệp.
@Service
public class MediaStreamService {

    private static final MediaType DEFAULT_MEDIA_TYPE = MediaType.parseMediaType(MediaAssetView.DEFAULT_MIME_TYPE);

    private final MediaAssetRepository mediaAssetRepository;
    private final CatalogMediaAssetRepository catalogMediaAssetRepository;
    private final CatalogEpisodeRepository catalogEpisodeRepository;
    private final MovieVisibility movieVisibility;
    private final PlaybackAccess playbackAccess;
    private final CurrentViewer currentViewer;
    private final ActiveViewerLookup activeViewerLookup;
    private final JwtService jwtService;
    private final MediaFileResolver mediaFileResolver;
    private final TransactionTemplate reads;

    public MediaStreamService(MediaAssetRepository mediaAssetRepository,
            CatalogMediaAssetRepository catalogMediaAssetRepository,
            CatalogEpisodeRepository catalogEpisodeRepository,
            MovieVisibility movieVisibility,
            PlaybackAccess playbackAccess, CurrentViewer currentViewer, ActiveViewerLookup activeViewerLookup,
            JwtService jwtService, MediaFileResolver mediaFileResolver, PlatformTransactionManager transactions) {
        this.mediaAssetRepository = mediaAssetRepository;
        this.catalogMediaAssetRepository = catalogMediaAssetRepository;
        this.catalogEpisodeRepository = catalogEpisodeRepository;
        this.movieVisibility = movieVisibility;
        this.playbackAccess = playbackAccess;
        this.currentViewer = currentViewer;
        this.activeViewerLookup = activeViewerLookup;
        this.jwtService = jwtService;
        this.mediaFileResolver = mediaFileResolver;
        this.reads = new TransactionTemplate(transactions);
        this.reads.setReadOnly(true);
    }

    // Xác thực người xem hoặc vé phát kèm đối chiếu tokenVersion trong CSDL, xong xuôi mới tìm tệp và đọc dung lượng.
    public ResolvedMediaStream resolveStream(String assetId, String grant, String token) {
        Viewer viewer = currentViewer.find().orElse(null);
        String effectiveToken = selectToken(grant, token);
        JwtService.VerifiedPlaybackGrant verifiedGrant = effectiveToken == null ? null
                : jwtService.parsePlaybackGrant(effectiveToken, assetId).orElse(null);
        MediaAsset authorized = reads.execute(status -> {
            PlaybackPrincipal grantPrincipal = activeGrantPrincipal(verifiedGrant);
            MediaAsset asset = mediaAssetRepository.findById(assetId).orElse(null);
            CatalogMediaAsset catalogAsset = catalogMediaAssetRepository.findById(assetId).orElse(null);
            boolean hasPublishedParent = catalogAsset != null
                    && catalogEpisodeRepository.findWithParentsByMediaAssetId(assetId).stream()
                            .anyMatch(CatalogEpisodeRepository.EpisodeWithParents::isPublished);
            return playbackAccess.authorizeForStream(assetId, asset, catalogAsset, hasPublishedParent, viewer, grantPrincipal);
        });
        Path filePath = mediaFileResolver.requireFile(authorized);
        return new ResolvedMediaStream(filePath, readFileSize(filePath), resolveMediaType(authorized.getMimeType()));
    }

    // Việt để ý chỗ này: vé phát phải tra lại tokenVersion trực tiếp trong CSDL để tài khoản vừa bị khóa hoặc đăng xuất là mất quyền xem ngay, không đợi JWT hết hạn.
    private PlaybackPrincipal activeGrantPrincipal(JwtService.VerifiedPlaybackGrant grant) {
        if (grant == null) {
            return null;
        }
        if (grant.userId() == null || grant.tokenVersion() == null) {
            return grant.principal();
        }
        return activeViewerLookup.findActiveViewerById(grant.userId(), grant.tokenVersion())
                .<PlaybackPrincipal>map(active -> new PlaybackPrincipal.Authenticated(active.role()))
                .orElse(null);
    }

    // Lấy thông tin phim chính (có kiểm tra tệp tồn tại trên đĩa) và trailer công khai; thiếu trailer thì rơi về videoUrl cũ.
    public MediaDescriptorView getMediaDescriptor(String movieId) {
        MovieMediaSnapshot snapshot = reads.execute(status -> loadMovieMedia(movieId));
        MediaAssetView feature = snapshot.allowedAssets().stream()
                .filter(asset -> MediaAssetView.KIND_FEATURE.equalsIgnoreCase(asset.getKind()))
                .filter(asset -> mediaFileResolver.resolve(asset).isPresent())
                .findFirst()
                .map(MediaAssetView::from)
                .orElse(null);
        MediaAssetView trailer = snapshot.allowedAssets().stream()
                .filter(asset -> MediaAssetView.KIND_TRAILER.equalsIgnoreCase(asset.getKind()))
                .findFirst()
                .map(MediaAssetView::from)
                .orElse(null);

        if (trailer == null && snapshot.legacyVideoUrl() != null && !snapshot.legacyVideoUrl().isBlank()) {
            trailer = MediaAssetView.legacyTrailer(snapshot.movieId(), snapshot.legacyVideoUrl());
        }
        return new MediaDescriptorView(snapshot.movieId(), feature != null, trailer != null, feature, trailer);
    }

    private MovieMediaSnapshot loadMovieMedia(String movieId) {
        Movie movie = movieVisibility.findVisibleById(movieId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy phim: " + movieId));
        List<MediaAsset> publicAssets = mediaAssetRepository.findByMovieIdAndAvailableTrueAndPublicAssetTrue(movieId);
        List<String> assetIds = publicAssets.stream().map(MediaAsset::getId).toList();
        Map<String, CatalogMediaAsset> catalogAssetsById = assetIds.isEmpty()
                ? Map.of()
                : catalogMediaAssetRepository.findAllById(assetIds).stream()
                        .collect(Collectors.toMap(CatalogMediaAsset::getId, Function.identity()));
        Set<String> publishedIds = publishedAssetIds(assetIds);
        // Chỉ trả ra những tệp thuộc danh mục công khai và có đủ bộ, mùa, tập đã xuất bản.
        List<MediaAsset> allowedAssets = publicAssets.stream()
                .filter(asset -> {
                    CatalogMediaAsset catalogAsset = catalogAssetsById.get(asset.getId());
                    return catalogAsset != null
                            && catalogAsset.isAccessibleBy(false, false)
                            && publishedIds.contains(asset.getId());
                })
                .toList();
        return new MovieMediaSnapshot(movie.getId(), movie.getVideoUrl(), allowedAssets);
    }

    private Set<String> publishedAssetIds(List<String> assetIds) {
        if (assetIds.isEmpty()) {
            return Set.of();
        }
        Set<String> published = new HashSet<>();
        for (Object[] row : catalogEpisodeRepository.findWithParentsByMediaAssetIdIn(assetIds)) {
            String assetId = (String) row[0];
            CatalogEpisode episode = (CatalogEpisode) row[1];
            CatalogSeries series = (CatalogSeries) row[2];
            CatalogSeason season = (CatalogSeason) row[3];
            if (new CatalogEpisodeRepository.EpisodeWithParents(episode, series, season).isPublished()) {
                published.add(assetId);
            }
        }
        return published;
    }

    // Giữ kết quả đọc từ CSDL để mang ra ngoài giao dịch trước khi kiểm tra tệp trên ổ đĩa.
    private record MovieMediaSnapshot(String movieId, String legacyVideoUrl, List<MediaAsset> allowedAssets) {
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
            throw new NotFoundException(MediaFileResolver.MISSING_FILE_MESSAGE);
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
