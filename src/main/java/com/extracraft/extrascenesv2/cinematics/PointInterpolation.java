package com.extracraft.extrascenesv2.cinematics;

import java.util.Locale;

public enum PointInterpolation {
    SMOOTH,
    INSTANT;

    public static PointInterpolation fromString(String raw) {
        if (raw == null) {
            return SMOOTH;
        }

        String normalized = raw.toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "INSTANT" -> INSTANT;
            default -> SMOOTH;
        };
    }
}
