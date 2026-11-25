package com.dippie.recommendation;

import java.util.Set;

// Chứa ba tập mã phim đã thích, đã xem và không thích của người xem ở thời điểm gợi ý.
public record ViewerSignals(Set<String> liked, Set<String> seen, Set<String> disliked) {

    boolean personalized() {
        return !liked.isEmpty() || !disliked.isEmpty();
    }
}
