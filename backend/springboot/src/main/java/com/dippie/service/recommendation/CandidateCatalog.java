package com.dippie.service.recommendation;

import com.dippie.model.recommendation.RecommendationCandidate;
import com.dippie.service.catalog.CatalogReadModel;
import com.dippie.service.catalog.CatalogReadModel.EpisodeProfile;
import com.dippie.service.catalog.CatalogReadModelCache;
import com.dippie.service.catalog.MediaFileAvailability;

import org.springframework.stereotype.Repository;

import java.util.List;

// nạp list tập phim ứng viên cho tầng gợi ý, cache ăn theo đúng tham chiếu CatalogReadModel hiện tại
@Repository
public class CandidateCatalog {

    private final CatalogReadModelCache cache;
    private final MediaFileAvailability files;
    private volatile CachedCandidates cached;

    public CandidateCatalog(CatalogReadModelCache cache, MediaFileAvailability files) {
        this.cache = cache;
        this.files = files;
    }

    // ứng viên cache theo snapshot danh mục, còn playableFilter vẫn chạy mỗi lượt để cập nhật kịp lúc file trên đĩa đổi
    public List<RecommendationCandidate> load() {
        CatalogReadModel catalog = cache.current();
        List<RecommendationCandidate> candidates = candidatesFor(catalog);
        var playable = catalog.playableFilter(files);
        return candidates.stream().filter(item -> playable.test(item.id())).toList();
    }

    // so thẳng == với snapshot cũ để chỉ dựng lại list ứng viên lúc catalog đổi bản mới
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

    // cặp snapshot CatalogReadModel với list ứng viên đã dựng sẵn theo nó
    private record CachedCandidates(CatalogReadModel model, List<RecommendationCandidate> candidates) {
    }
}
