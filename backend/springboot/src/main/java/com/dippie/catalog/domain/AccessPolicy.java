package com.dippie.catalog.domain;

import java.util.Locale;

public enum AccessPolicy {
    PUBLIC, AUTHENTICATED, PRIVATE, UNKNOWN;

    public static AccessPolicy of(String raw) {
        if (raw == null || raw.isBlank()) {
            return PUBLIC;
        }
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "public" -> PUBLIC;
            case "authenticated" -> AUTHENTICATED;
            case "private" -> PRIVATE;
            default -> UNKNOWN;
        };
    }

    public boolean allows(boolean authenticated, boolean admin) {
        return switch (this) {
            case PUBLIC -> true;
            case AUTHENTICATED -> authenticated;
            case PRIVATE -> authenticated && admin;
            case UNKNOWN -> false;
        };
    }
}
