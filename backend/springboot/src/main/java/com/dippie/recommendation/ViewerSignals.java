package com.dippie.recommendation;

import java.util.Set;

public record ViewerSignals(Set<String> liked, Set<String> seen) {
}
