package com.dippie.recommendation;

import com.dippie.catalog.query.CatalogReadModelCache;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class CandidateCatalog {

    private final CatalogReadModelCache cache;

    public CandidateCatalog(CatalogReadModelCache cache) {
        this.cache = cache;
    }

    public List<RecommendationCandidate> load() {
        return cache.current().recommendationCandidates();
    }
}
