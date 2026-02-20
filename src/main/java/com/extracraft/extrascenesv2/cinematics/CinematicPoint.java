package com.extracraft.extrascenesv2.cinematics;

import org.bukkit.Location;

public record CinematicPoint(int tick, Location location, InterpolationMode interpolationMode) {

    public enum InterpolationMode {
        SMOOTH,
        INSTANT;

        public static InterpolationMode fromString(String raw) {
            if (raw == null) {
                return SMOOTH;
            }

            return "instant".equalsIgnoreCase(raw) ? INSTANT : SMOOTH;
        }
    }

    public CinematicPoint {
        interpolationMode = interpolationMode == null ? InterpolationMode.SMOOTH : interpolationMode;
    }
}
