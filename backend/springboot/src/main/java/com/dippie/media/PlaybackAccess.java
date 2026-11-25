package com.dippie.media;

import com.dippie.catalog.domain.CatalogMediaAsset;
import com.dippie.common.error.NotFoundException;
import com.dippie.security.PlaybackPrincipal;
import com.dippie.security.Role;
import com.dippie.security.Viewer;
import org.springframework.stereotype.Component;

// Protection Proxy chặn ngay trước khi cấp vé phát hoặc mở tệp video.
// Mọi trường hợp không có quyền, tệp riêng tư hoặc chưa xuất bản mình đều trả 404 (Việt đừng đổi sang 403 kẻo lộ mã tài nguyên đang tồn tại).
@Component
class PlaybackAccess {

    private static final String MEDIA_NOT_FOUND_PREFIX = "Không tìm thấy video: ";

    // Kiểm tra quyền xem theo phiên đăng nhập hiện tại trước khi cấp vé phát.
    MediaAsset authorizeForGrant(
            String assetId,
            MediaAsset asset,
            CatalogMediaAsset catalogAsset,
            boolean hasPublishedParent,
            Viewer viewer
    ) {
        return authorize(assetId, asset, catalogAsset, hasPublishedParent, viewer, null);
    }

    // Kiểm tra quyền lúc phát thật, xét cả phiên đăng nhập lẫn vé phát kèm trong query.
    MediaAsset authorizeForStream(
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
        // Thiếu bản ghi, tắt khả dụng hoặc nằm ngoài catalog_media_assets thì chặn hết kể cả admin (fail-closed).
        if (asset == null || !asset.isAvailable() || catalogAsset == null) {
            throw mediaNotFound(assetId);
        }
        Role role = effectiveRole(viewer, grantPrincipal);
        boolean authenticated = role != null;
        boolean admin = role == Role.ROLE_ADMIN;

        // Khách và user thường chỉ xem được khi cả tệp lẫn chuỗi bộ, mùa, tập đều đã xuất bản; admin được xem trước bản nháp.
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

    // Nếu request có sẵn phiên đăng nhập thì lấy quyền của phiên đó, không cho mượn vé phát của admin để vượt quyền.
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
        return new NotFoundException(MEDIA_NOT_FOUND_PREFIX + assetId);
    }
}
