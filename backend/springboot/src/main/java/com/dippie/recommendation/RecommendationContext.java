package com.dippie.recommendation;

import com.dippie.common.text.UnicodeText;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

record RecommendationContext(
        RecommendationRequest request, List<RecommendationCandidate> catalog, ViewerSignals signals
) {
    Set<String> explicitGenres() {
        return request.genres().stream().map(UnicodeText::caseFold).collect(Collectors.toSet());
    }

    List<RecommendationCandidate> candidates(Set<String> explicitGenres) {
        return catalog.stream()
                .filter(item -> !item.matchesIds(signals.seen()))
                .filter(item -> explicitGenres.isEmpty() || !Collections.disjoint(explicitGenres, tags(item.genres())))
                .toList();
    }

    static Set<String> tags(List<String> values) {
        return values.stream().map(UnicodeText::caseFold).collect(Collectors.toSet());
    }
}
