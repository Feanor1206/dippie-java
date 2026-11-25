package com.dippie.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dippie.catalog.domain.CatalogMediaAsset;
import com.dippie.common.error.NotFoundException;
import com.dippie.security.Role;
import com.dippie.security.Viewer;
import org.junit.jupiter.api.Test;

class PlaybackAccessTest {

    private static final String READY = "{\"status\":\"ready\"}";
    private static final String DRAFT = "{\"status\":\"draft\"}";
    private static final Viewer USER = new Viewer(1L, "user", Role.ROLE_USER);
    private static final Viewer ADMIN = new Viewer(2L, "admin", Role.ROLE_ADMIN);

    private final PlaybackAccess playbackAccess = new PlaybackAccess();

    @Test
    void rejectsMissingOrUnavailableAssetEvenForAdmin() {
        assertThatThrownBy(() -> playbackAccess.authorizeForGrant("missing", null, null, ADMIN))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Không tìm thấy tài nguyên media: missing");

        MediaAsset unavailable = mediaAsset("ast-1", true, false);
        assertThatThrownBy(() -> playbackAccess.authorizeForStream("ast-1", unavailable, null, ADMIN, "ROLE_ADMIN"))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Không tìm thấy tài nguyên media: ast-1");
    }

    @Test
    void allowsPublicAssetWithoutCatalogRecordForAnonymous() {
        MediaAsset asset = mediaAsset("ast-pub", true, true);

        assertThat(playbackAccess.authorizeForGrant("ast-pub", asset, null, null)).isSameAs(asset);
        assertThat(playbackAccess.authorizeForStream("ast-pub", asset, null, null, null)).isSameAs(asset);
    }

    @Test
    void requiresAuthenticatedViewerOrGrantForNonPublicAssetWithoutCatalogRecord() {
        MediaAsset asset = mediaAsset("ast-nonpub", false, true);

        assertThatThrownBy(() -> playbackAccess.authorizeForGrant("ast-nonpub", asset, null, null))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Không tìm thấy tài nguyên media: ast-nonpub");

        assertThatThrownBy(() -> playbackAccess.authorizeForStream("ast-nonpub", asset, null, null, "ROLE_ANONYMOUS"))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Không tìm thấy tài nguyên media: ast-nonpub");

        assertThat(playbackAccess.authorizeForGrant("ast-nonpub", asset, null, USER)).isSameAs(asset);
        assertThat(playbackAccess.authorizeForGrant("ast-nonpub", asset, null, ADMIN)).isSameAs(asset);
        assertThat(playbackAccess.authorizeForStream("ast-nonpub", asset, null, null, "ROLE_USER")).isSameAs(asset);
        assertThat(playbackAccess.authorizeForStream("ast-nonpub", asset, null, null, "ROLE_ADMIN")).isSameAs(asset);
    }

    @Test
    void enforcesCatalogPrivatePolicyForGrantAndStream() {
        MediaAsset asset = mediaAsset("ast-priv", false, true);
        CatalogMediaAsset catalogAsset = catalogAsset("ast-priv", "private", true, READY);

        assertThatThrownBy(() -> playbackAccess.authorizeForGrant("ast-priv", asset, catalogAsset, null))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Không tìm thấy tài nguyên media: ast-priv");

        assertThatThrownBy(() -> playbackAccess.authorizeForGrant("ast-priv", asset, catalogAsset, USER))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Không tìm thấy tài nguyên media: ast-priv");

        assertThatThrownBy(() -> playbackAccess.authorizeForStream("ast-priv", asset, catalogAsset, null, "ROLE_USER"))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Không tìm thấy tài nguyên media: ast-priv");

        assertThat(playbackAccess.authorizeForGrant("ast-priv", asset, catalogAsset, ADMIN)).isSameAs(asset);
        assertThat(playbackAccess.authorizeForStream("ast-priv", asset, catalogAsset, null, "ROLE_ADMIN"))
                .isSameAs(asset);
    }

    @Test
    void enforcesCatalogAuthenticatedDisabledAndUnpublishedRules() {
        MediaAsset asset = mediaAsset("ast-cat", true, true);

        CatalogMediaAsset authOnly = catalogAsset("ast-cat", "authenticated", true, READY);
        assertThatThrownBy(() -> playbackAccess.authorizeForGrant("ast-cat", asset, authOnly, null))
                .isInstanceOf(NotFoundException.class);
        assertThat(playbackAccess.authorizeForGrant("ast-cat", asset, authOnly, USER)).isSameAs(asset);
        assertThat(playbackAccess.authorizeForStream("ast-cat", asset, authOnly, null, "ROLE_USER")).isSameAs(asset);

        CatalogMediaAsset draft = catalogAsset("ast-cat", "public", true, DRAFT);
        assertThatThrownBy(() -> playbackAccess.authorizeForStream("ast-cat", asset, draft, USER, null))
                .isInstanceOf(NotFoundException.class);
        assertThat(playbackAccess.authorizeForStream("ast-cat", asset, draft, null, "ROLE_ADMIN")).isSameAs(asset);

        CatalogMediaAsset disabled = catalogAsset("ast-cat", "public", false, READY);
        assertThatThrownBy(() -> playbackAccess.authorizeForStream("ast-cat", asset, disabled, ADMIN, "ROLE_ADMIN"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void authenticViewerRoleTakesPrecedenceOverGrantRoleWhenStreaming() {
        MediaAsset asset = mediaAsset("ast-priv", false, true);
        CatalogMediaAsset privateAsset = catalogAsset("ast-priv", "private", true, READY);

        assertThatThrownBy(() -> playbackAccess.authorizeForStream("ast-priv", asset, privateAsset, USER, "ROLE_ADMIN"))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Không tìm thấy tài nguyên media: ast-priv");
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
