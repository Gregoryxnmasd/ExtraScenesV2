package com.extracraft.extrascenesv2.cinematics;

import org.bukkit.Location;

public record CinematicPoint(int tick, Location location, InterpolationMode interpolationMode) {

    public enum InterpolationMode {
        SMOOTH,
        LINEAR,
        INSTANT;

        public static InterpolationMode fromString(String raw) {
            if (raw == null) {
                return SMOOTH;
            }

            if ("instant".equalsIgnoreCase(raw)) {
                return INSTANT;
            }
            if ("linear".equalsIgnoreCase(raw)) {
                return LINEAR;
            }
            return SMOOTH;
        }
    }

    public CinematicPoint {
        interpolationMode = interpolationMode == null ? InterpolationMode.SMOOTH : interpolationMode;
    }
}
