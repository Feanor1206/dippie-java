package com.dippie.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dippie.catalog.domain.CatalogMediaAsset;
import com.dippie.common.error.NotFoundException;
import com.dippie.security.PlaybackPrincipal;
import com.dippie.security.Role;
import com.dippie.security.Viewer;
import org.junit.jupiter.api.Test;

// Kiểm tra quy tắc phân quyền xem video theo danh mục, chuỗi bộ/mùa/tập đã xuất bản và vai trò người xem.
class PlaybackAccessTest {

    private static final String READY = "{\"status\":\"ready\"}";
    private static final String DRAFT = "{\"status\":\"draft\"}";
    private static final Viewer USER = new Viewer(1L, "user", Role.ROLE_USER);
    private static final Viewer ADMIN = new Viewer(2L, "admin", Role.ROLE_ADMIN);
    private static final PlaybackPrincipal GRANT_USER = new PlaybackPrincipal.Authenticated(Role.ROLE_USER);
    private static final PlaybackPrincipal GRANT_ADMIN = new PlaybackPrincipal.Authenticated(Role.ROLE_ADMIN);
    private static final PlaybackPrincipal GRANT_ANON = PlaybackPrincipal.Anonymous.INSTANCE;

    private final PlaybackAccess playbackAccess = new PlaybackAccess();

    @Test
    void rejectsMissingUnavailableOrNonCatalogAssetsEvenForAdmin() {
        assertThatThrownBy(() -> playbackAccess.authorizeForGrant("missing", null, null, true, ADMIN))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Không tìm thấy video: missing");

        MediaAsset unavailable = mediaAsset("ast-1", true, false);
        CatalogMediaAsset catalog = catalogAsset("ast-1", "public", true, READY);
        assertThatThrownBy(() -> playbackAccess.authorizeForStream("ast-1", unavailable, catalog, true, ADMIN, GRANT_ADMIN))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Không tìm thấy video: ast-1");

        // Tệp không nằm trong catalog_media_assets (catalogAsset == null) bị chặn với mọi vai trò, kể cả admin.
        MediaAsset nonCatalogPublic = mediaAsset("ast-noncat", true, true);
        assertThatThrownBy(() -> playbackAccess.authorizeForGrant("ast-noncat", nonCatalogPublic, null, false, null))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> playbackAccess.authorizeForGrant("ast-noncat", nonCatalogPublic, null, false, USER))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> playbackAccess.authorizeForGrant("ast-noncat", nonCatalogPublic, null, false, ADMIN))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> playbackAccess.authorizeForStream("ast-noncat", nonCatalogPublic, null, false, null, GRANT_ADMIN))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void rejectsUnpublishedParentChainForAnonymousAndRegularUserButAllowsAdminPreview() {
        MediaAsset asset = mediaAsset("ast-unpub", true, true);
        CatalogMediaAsset catalogAsset = catalogAsset("ast-unpub", "public", true, READY);

        assertThatThrownBy(() -> playbackAccess.authorizeForGrant("ast-unpub", asset, catalogAsset, false, null))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> playbackAccess.authorizeForGrant("ast-unpub", asset, catalogAsset, false, USER))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> playbackAccess.authorizeForStream("ast-unpub", asset, catalogAsset, false, null, GRANT_ANON))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> playbackAccess.authorizeForStream("ast-unpub", asset, catalogAsset, false, null, GRANT_USER))
                .isInstanceOf(NotFoundException.class);

        assertThat(playbackAccess.authorizeForGrant("ast-unpub", asset, catalogAsset, false, ADMIN)).isSameAs(asset);
        assertThat(playbackAccess.authorizeForStream("ast-unpub", asset, catalogAsset, false, null, GRANT_ADMIN))
                .isSameAs(asset);
    }

    @Test
    void allowsPublicCatalogAssetWithPublishedParentForAnonymous() {
        MediaAsset asset = mediaAsset("ast-pub", true, true);
        CatalogMediaAsset catalogAsset = catalogAsset("ast-pub", "public", true, READY);

        assertThat(playbackAccess.authorizeForGrant("ast-pub", asset, catalogAsset, true, null)).isSameAs(asset);
        assertThat(playbackAccess.authorizeForStream("ast-pub", asset, catalogAsset, true, null, null)).isSameAs(asset);
    }

    @Test
    void enforcesCatalogPrivatePolicyForGrantAndStream() {
        MediaAsset asset = mediaAsset("ast-priv", false, true);
        CatalogMediaAsset catalogAsset = catalogAsset("ast-priv", "private", true, READY);

        assertThatThrownBy(() -> playbackAccess.authorizeForGrant("ast-priv", asset, catalogAsset, true, null))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Không tìm thấy video: ast-priv");

        assertThatThrownBy(() -> playbackAccess.authorizeForGrant("ast-priv", asset, catalogAsset, true, USER))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Không tìm thấy video: ast-priv");

        assertThatThrownBy(() -> playbackAccess.authorizeForStream("ast-priv", asset, catalogAsset, true, null, GRANT_USER))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Không tìm thấy video: ast-priv");

        assertThat(playbackAccess.authorizeForGrant("ast-priv", asset, catalogAsset, true, ADMIN)).isSameAs(asset);
        assertThat(playbackAccess.authorizeForStream("ast-priv", asset, catalogAsset, true, null, GRANT_ADMIN))
                .isSameAs(asset);
    }

    @Test
    void enforcesCatalogAuthenticatedDisabledAndUnpublishedRules() {
        MediaAsset asset = mediaAsset("ast-cat", true, true);

        CatalogMediaAsset authOnly = catalogAsset("ast-cat", "authenticated", true, READY);
        assertThatThrownBy(() -> playbackAccess.authorizeForGrant("ast-cat", asset, authOnly, true, null))
                .isInstanceOf(NotFoundException.class);
        assertThat(playbackAccess.authorizeForGrant("ast-cat", asset, authOnly, true, USER)).isSameAs(asset);
        assertThat(playbackAccess.authorizeForStream("ast-cat", asset, authOnly, true, null, GRANT_USER)).isSameAs(asset);

        CatalogMediaAsset draft = catalogAsset("ast-cat", "public", true, DRAFT);
        assertThatThrownBy(() -> playbackAccess.authorizeForStream("ast-cat", asset, draft, true, USER, null))
                .isInstanceOf(NotFoundException.class);
        assertThat(playbackAccess.authorizeForStream("ast-cat", asset, draft, true, null, GRANT_ADMIN)).isSameAs(asset);

        CatalogMediaAsset disabled = catalogAsset("ast-cat", "public", false, READY);
        assertThatThrownBy(() -> playbackAccess.authorizeForStream("ast-cat", asset, disabled, true, ADMIN, GRANT_ADMIN))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void authenticViewerRoleTakesPrecedenceOverGrantRoleWhenStreaming() {
        MediaAsset asset = mediaAsset("ast-priv", false, true);
        CatalogMediaAsset privateAsset = catalogAsset("ast-priv", "private", true, READY);

        assertThatThrownBy(() -> playbackAccess.authorizeForStream("ast-priv", asset, privateAsset, true, USER, GRANT_ADMIN))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Không tìm thấy video: ast-priv");
    }

    private static MediaAsset mediaAsset(String id, boolean publicAsset, boolean available) {
        return new MediaAsset(
                id, "movie-1", "feature", "local", null,
                "video.mp4", "v1", "video/mp4", 1024L, 10.0, publicAsset, available
        );
    }

    private static CatalogMediaAsset catalogAsset(String id, String policy, boolean enabled, String recordJson) {
        return new CatalogMediaAsset(
                id, "video", "feature", null, null, null,
                "local", "local", "video.mp4", null, null, 1024L, null, 10_000L,
                null, null, null, policy, enabled, recordJson, null, 1
        );
    }
}
