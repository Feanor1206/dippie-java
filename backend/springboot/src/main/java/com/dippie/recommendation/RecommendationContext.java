package com.dippie.recommendation;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public record RecommendationContext(
        RecommendationRequest request, List<RecommendationCandidate> catalog, ViewerSignals signals
) {
    Set<String> explicitGenres() {
        return request.genres().stream().map(RecommendationContext::fold).collect(Collectors.toSet());
    }

    List<RecommendationCandidate> candidates(Set<String> explicitGenres) {
        return catalog.stream()
                .filter(item -> !item.matchesIds(signals.seen()))
                .filter(item -> explicitGenres.isEmpty() || !Collections.disjoint(explicitGenres, tags(item.genres())))
                .toList();
    }

    static Set<String> tags(List<String> values) {
        return values.stream().map(RecommendationContext::fold).collect(Collectors.toSet());
    }

    private static String fold(String value) {
        StringBuilder folded = new StringBuilder(value.length());
        value.codePoints().forEach(codePoint -> {
            String letter = Character.toString(codePoint);
            if (codePoint == 'ı') {
                folded.append(letter);
            } else if (Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.CHEROKEE) {
                folded.append(letter.toUpperCase(Locale.ROOT));
            } else if (codePoint == 'ẞ') {
                folded.append("ss");
            } else {
                folded.append(letter.toUpperCase(Locale.ROOT).toLowerCase(Locale.ROOT));
            }
        });
        return folded.toString();
    }
}
