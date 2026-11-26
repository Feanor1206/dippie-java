package com.dippie.service.media;

import com.dippie.model.media.MediaAsset;
import com.dippie.repository.media.MediaAssetRepository;
import com.dippie.service.catalog.MediaFileAvailability;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

// Adapter nối MediaFileResolver và MediaAssetRepository sang cổng MediaFileAvailability của gói catalog.
// Mình bọc lại thế này để gói catalog không cần biết video đang nằm ở ổ đĩa nào hay bảng media_assets ra sao.
@Component
public class MediaFileAvailabilityAdapter implements MediaFileAvailability {

    private final MediaAssetRepository mediaAssets;
    private final MediaFileResolver mediaFileResolver;
    private final EntityManager entities;

    public MediaFileAvailabilityAdapter(MediaAssetRepository mediaAssets, MediaFileResolver mediaFileResolver,
            EntityManager entities) {
        this.mediaAssets = mediaAssets;
        this.mediaFileResolver = mediaFileResolver;
        this.entities = entities;
    }

    // Lọc ra các mã media thực sự tìm thấy tệp trên đĩa qua MediaFileResolver.
    @Override
    public Set<String> availableAssetIds(Collection<String> assetIds) {
        if (assetIds == null || assetIds.isEmpty()) {
            return Set.of();
        }
        Set<String> available = new HashSet<>();
        for (MediaAsset asset : mediaAssets.findAllById(assetIds)) {
            if (mediaFileResolver.resolve(asset).isPresent()) {
                available.add(asset.getId());
            }
        }
        return Set.copyOf(available);
    }

    // Trả bản đồ id sang cờ công khai cho các video khả dụng có mặt ở cả media_assets lẫn catalog.
    @Override
    @Transactional(readOnly = true)
    public Map<String, Boolean> availableVideoAssets() {
        Map<String, Boolean> byId = new HashMap<>();
        for (Object[] row : entities.createQuery("""
                select media.id, media.publicAsset from MediaAsset media
                join CatalogMediaAsset asset on media.id = asset.id
                where media.available = true and lower(media.mimeType) like 'video/%'
                """, Object[].class).getResultList()) {
            byId.put((String) row[0], Boolean.TRUE.equals(row[1]));
        }
        return Map.copyOf(byId);
    }
}
