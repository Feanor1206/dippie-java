package com.dippie.media;

import com.dippie.catalog.domain.CatalogMediaAsset;
import com.dippie.common.error.NotFoundException;
import com.dippie.security.Role;
import com.dippie.security.Viewer;
import org.springframework.stereotype.Component;

@Component
class PlaybackAccess {

    private static final String MEDIA_NOT_FOUND_PREFIX = "Không tìm thấy tài nguyên media: ";

    MediaAsset authorizeForGrant(
            String assetId,
            MediaAsset asset,
            CatalogMediaAsset catalogAsset,
            Viewer viewer
    ) {
        return authorize(assetId, asset, catalogAsset, viewer, null);
    }

    MediaAsset authorizeForStream(
            String assetId,
            MediaAsset asset,
            CatalogMediaAsset catalogAsset,
            Viewer viewer,
            String grantRole
    ) {
        return authorize(assetId, asset, catalogAsset, viewer, grantRole);
    }

    private MediaAsset authorize(
            String assetId,
            MediaAsset asset,
            CatalogMediaAsset catalogAsset,
            Viewer viewer,
            String grantRole
    ) {
        if (asset == null || !asset.isAvailable()) {
            throw mediaNotFound(assetId);
        }
        Role effectiveRole = resolveEffectiveRole(viewer, grantRole);
        boolean authenticated = effectiveRole != null;
        boolean admin = effectiveRole == Role.ROLE_ADMIN;

        if (catalogAsset != null && !catalogAsset.isAccessibleBy(authenticated, admin)) {
            throw mediaNotFound(assetId);
        }
        if (!asset.isPublic() && !authenticated) {
            throw mediaNotFound(assetId);
        }
        return asset;
    }

    private static Role resolveEffectiveRole(Viewer viewer, String grantRole) {
        if (viewer != null) {
            return viewer.role();
        }
        if (Role.ROLE_ADMIN.name().equals(grantRole)) {
            return Role.ROLE_ADMIN;
        }
        if (Role.ROLE_USER.name().equals(grantRole)) {
            return Role.ROLE_USER;
        }
        return null;
    }

    private static NotFoundException mediaNotFound(String assetId) {
        return new NotFoundException(MEDIA_NOT_FOUND_PREFIX + assetId);
    }
}
