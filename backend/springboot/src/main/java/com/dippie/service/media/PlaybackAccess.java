package com.dippie.service.media;

import com.dippie.exception.NotFoundException;
import com.dippie.model.catalog.CatalogMediaAsset;
import com.dippie.model.media.MediaAsset;
import com.dippie.model.user.Role;
import com.dippie.security.PlaybackPrincipal;
import com.dippie.security.Viewer;

import org.springframework.stereotype.Component;

// kiểu Protection Proxy đứng chặn trước khi cấp vé phát hay mở file video
// không quyền, file riêng tư hay chưa xuất bản đều trả 404 hết, Việt đừng đổi sang 403 kẻo lộ mã file đang tồn tại
@Component
public class PlaybackAccess {

    // check quyền theo phiên đang đăng nhập trước khi cấp vé phát
    public MediaAsset authorizeForGrant(
            String assetId,
            MediaAsset asset,
            CatalogMediaAsset catalogAsset,
            boolean hasPublishedParent,
            Viewer viewer
    ) {
        return authorize(assetId, asset, catalogAsset, hasPublishedParent, viewer, null);
    }

    // check quyền lúc phát thật, xét cả phiên đăng nhập lẫn vé phát trong query
    public MediaAsset authorizeForStream(
            String assetId,
            MediaAsset asset,
            CatalogMediaAsset catalogAsset,
            boolean hasPublishedParent,
            Viewer viewer,
            PlaybackPrincipal grantPrincipal
    ) {
        return authorize(assetId, asset, catalogAsset, hasPublishedParent, viewer, grantPrincipal);
    }

    private MediaAsset authorize(
            String assetId,
            MediaAsset asset,
            CatalogMediaAsset catalogAsset,
            boolean hasPublishedParent,
            Viewer viewer,
            PlaybackPrincipal grantPrincipal
    ) {
        // thiếu bản ghi, tắt khả dụng hay ngoài catalog_media_assets thì chặn hết kể cả admin kiểu fail-closed
        if (asset == null || !asset.isAvailable() || catalogAsset == null) {
            throw mediaNotFound(assetId);
        }
        Role role = effectiveRole(viewer, grantPrincipal);
        boolean authenticated = role != null;
        boolean admin = role == Role.ROLE_ADMIN;

        // khách với user thường chỉ xem được khi file lẫn bộ, mùa, tập đều đã xuất bản, riêng admin được xem trước bản nháp
        if (!catalogAsset.isAccessibleBy(authenticated, admin)) {
            throw mediaNotFound(assetId);
        }
        if (!admin && !hasPublishedParent) {
            throw mediaNotFound(assetId);
        }
        if (!asset.isPublic() && !authenticated) {
            throw mediaNotFound(assetId);
        }
        return asset;
    }

    // có phiên đăng nhập sẵn thì lấy quyền phiên đó, không cho mượn vé của admin để leo quyền
    private static Role effectiveRole(Viewer viewer, PlaybackPrincipal grantPrincipal) {
        if (viewer != null) {
            return viewer.role();
        }
        return switch (grantPrincipal) {
            case PlaybackPrincipal.Authenticated(Role role) -> role;
            case PlaybackPrincipal.Anonymous ignored -> null;
            case null -> null;
        };
    }

    private static NotFoundException mediaNotFound(String assetId) {
        return new NotFoundException("Không tìm thấy video: " + assetId);
    }
}
