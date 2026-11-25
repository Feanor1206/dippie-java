package com.dippie.recommendation;

import com.dippie.catalog.domain.MediaFileAvailability;
import com.dippie.catalog.query.CatalogReadModel;
import com.dippie.catalog.query.CatalogReadModel.EpisodeProfile;
import com.dippie.catalog.query.CatalogReadModelCache;
import org.springframework.stereotype.Repository;

import java.util.List;

// Nạp danh sách tập phim ứng viên cho tầng gợi ý và giữ bản cache ăn theo đúng tham chiếu CatalogReadModel hiện tại.
@Repository
public class CandidateCatalog {

    private final CatalogReadModelCache cache;
    private final MediaFileAvailability files;
    private volatile CachedCandidates cached;

    public CandidateCatalog(CatalogReadModelCache cache, MediaFileAvailability files) {
        this.cache = cache;
        this.files = files;
    }

    // Danh sách ứng viên được cache theo snapshot danh mục, nhưng bộ lọc playableFilter vẫn chạy mỗi lượt để cập nhật kịp nếu tệp video trên đĩa bị xóa hoặc mới thêm.
    public List<RecommendationCandidate> load() {
        CatalogReadModel catalog = cache.current();
        List<RecommendationCandidate> candidates = candidatesFor(catalog);
        var playable = catalog.playableFilter(files);
        return candidates.stream().filter(item -> playable.test(item.id())).toList();
    }

    // So tham chiếu == với snapshot cũ để chỉ chuyển đổi lại danh sách ứng viên khi catalog thật sự đổi phiên bản.
    private List<RecommendationCandidate> candidatesFor(CatalogReadModel catalog) {
        CachedCandidates current = cached;
        if (current != null && current.model() == catalog) {
            return current.candidates();
        }
        List<RecommendationCandidate> built = catalog.episodeProfiles().stream()
                .map(CandidateCatalog::toCandidate).toList();
        cached = new CachedCandidates(catalog, built);
        return built;
    }

    private static RecommendationCandidate toCandidate(EpisodeProfile profile) {
        return new RecommendationCandidate(profile.id(), profile.title(), profile.seriesId(), profile.seasonId(),
                profile.episodeNumber(), profile.year(), profile.rating(), profile.runtime(), profile.poster(),
                profile.backdrop(), profile.description(), profile.genres(), profile.genreTerms(), profile.themes(),
                profile.keywords(), profile.characters(), null, null, profile.durationMs(), profile.seriesTitle(),
                profile.seasonNumber(), profile.summaryQuality());
    }

    // Cặp snapshot CatalogReadModel và danh sách ứng viên tương ứng đã dựng sẵn.
    private record CachedCandidates(CatalogReadModel model, List<RecommendationCandidate> candidates) {
    }
}
