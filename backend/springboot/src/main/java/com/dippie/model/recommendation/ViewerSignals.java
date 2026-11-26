package com.dippie.model.recommendation;

import java.util.Set;

// ba tập mã phim đã thích, đã xem với không thích của người xem lúc gợi ý
public record ViewerSignals(Set<String> liked, Set<String> seen, Set<String> disliked) {

    public boolean personalized() {
        return !liked.isEmpty() || !disliked.isEmpty();
    }
}
